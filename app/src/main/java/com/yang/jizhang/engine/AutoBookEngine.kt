package com.yang.jizhang.engine

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.PendingBill
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.engine.keepalive.ChannelIds
import com.yang.jizhang.engine.keepalive.ListenerHealth
import com.yang.jizhang.util.BudgetAlertHelper
import java.security.MessageDigest

/**
 * v4.0.0 引擎重写：自动入账编排（替代旧 PaymentListener.saveAutoTransaction 散落逻辑）。
 * 管线：解析 → 去重 → 高置信直入 / 低置信进待确认队列 → 通知 + 预算检查 + 小组件刷新。
 */
object AutoBookEngine {
    private const val TAG = "AutoBook"
    private const val REVIEW_REPLAY_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

    /** 监听器收到通知的统一入口（监听器工作协程内调用） */
    suspend fun ingest(context: Context, pkg: String, title: String, text: String, postTime: Long, notificationKey: String) {
        if (!BillParser.mightBeBill(pkg, title, text)) return
        val bill = BillParser.parse(pkg, title, text) ?: return
        val app = context.applicationContext

        val db = (app as JizhangApp).db
        if (bill.needsReview) {
            val eventKey = MessageDigest.getInstance("SHA-256")
                .digest("$notificationKey|$postTime|${bill.fingerprint}".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val id = db.withTransaction {
                val pendingDao = db.pendingBillDao()
                pendingDao.pruneHandled(System.currentTimeMillis() - REVIEW_REPLAY_WINDOW_MS)
                if (db.txDao().findByDedupeKey("REVIEW_CONFIRMED|$eventKey") != null) return@withTransaction -1L
                pendingDao.insert(
                    PendingBill(
                        eventKey = eventKey,
                        amountCents = bill.amountCents,
                        isIncome = bill.isIncome,
                        merchant = bill.merchant,
                        source = bill.channel,
                        transactionTime = postTime,
                        reason = bill.reviewReason,
                        rawSnippet = bill.classificationDetails.ifBlank { bill.rawSnippet.orEmpty() }.ifBlank { null },
                    ),
                )
            }
            if (id != -1L) {
                runCatching { ChannelIds.needsReview(app, id, bill.amountCents, bill.channel, bill.reviewReason) }
                if (db.pendingBillDao().find(id) == null) runCatching { ChannelIds.cancelReview(app, id) }
            }
            return
        }

        val dao = db.txDao()
        if (DuplicateGuard.findDuplicate(dao, bill, eventTime = postTime) != null) {
            Log.d(TAG, "duplicate skipped: ${bill.amountCents} ${bill.channel}")
            return
        }

        val category = CategoryRules.classify(
            merchant = bill.merchant,
            details = bill.classificationDetails,
            channel = bill.channel,
            isIncome = bill.isIncome,
            learned = com.yang.jizhang.util.CategoryLearner.hint(app, bill.merchant),
        )
        val id = dao.insertIgnore(
            TxEntity(
                amountCents = bill.amountCents,
                isIncome = bill.isIncome,
                category = category,
                note = bill.merchant.ifBlank { bill.channel },
                source = bill.channel,
                time = postTime,
                dedupeKey = "AUTO|${bill.fingerprint}|${postTime / 25_000}",
            )
        )
        if (id == -1L) {
            Log.d(TAG, "insert ignored by unique key")
            return
        }
        Log.i(TAG, "recorded id=$id ${bill.amountCents} ${if (bill.isIncome) "income" else "expense"} ${bill.channel}")

        // v5.1.0：入账即判预算状态，通知带剩余建议
        val hint = if (!bill.isIncome) budgetHintFor(app, category) else null
        runCatching { ChannelIds.autoRecorded(app, bill.amountCents, bill.merchant, bill.channel, bill.isIncome, id, hint) }
        BudgetAlertHelper.checkAsync(app)
        com.yang.jizhang.widget.WidgetSync.refreshAsync(app)
    }

    /** 找受影响预算（分类优先于总预算）并生成建议短句 */
    private suspend fun budgetHintFor(app: Context, category: String): String? {
        return try {
            val budgets = com.yang.jizhang.data.AppDatabase.get(app).budgetDao().getAllOnce()
                .filter { it.enabled }
            val budget = budgets.firstOrNull { it.category == category }
                ?: budgets.firstOrNull { it.category.isEmpty() }
                ?: return null
            val now = System.currentTimeMillis()
            val win = com.yang.jizhang.util.Periods.currentWindow(budget.period, now)
            val spent = com.yang.jizhang.data.AppDatabase.get(app).txDao()
                .getByTimeRangeOnce(win[0], now)
                .filter { !it.isIncome }
                .filter { budget.category.isEmpty() || it.category == budget.category }
                .sumOf { it.amountCents }
            com.yang.jizhang.util.BudgetAdvisor.advise(budget, spent, now).message
        } catch (_: Exception) {
            null
        }
    }

    /** 在同一个事务内完成入账和移出待确认列表，写入失败时可重试。 */
    suspend fun confirm(context: Context, pendingId: Long, amountCents: Long, isIncome: Boolean): Boolean {
        require(amountCents > 0)
        val app = context.applicationContext as JizhangApp
        val db = app.db
        val saved = db.withTransaction {
            val pending = db.pendingBillDao().find(pendingId) ?: return@withTransaction false
            val id = db.txDao().insertIgnore(
                TxEntity(
                    amountCents = amountCents,
                    isIncome = isIncome,
                    category = CategoryRules.classify(
                        merchant = pending.merchant,
                        details = pending.rawSnippet,
                        channel = pending.source,
                        isIncome = isIncome,
                        learned = com.yang.jizhang.util.CategoryLearner.hint(app, pending.merchant),
                    ),
                    note = pending.merchant.ifBlank { pending.source },
                    source = pending.source,
                    time = pending.transactionTime,
                    dedupeKey = "REVIEW_CONFIRMED|${pending.eventKey}",
                ),
            )
            db.pendingBillDao().markHandled(pendingId, System.currentTimeMillis())
            id != -1L
        }
        if (saved) {
            runCatching { BudgetAlertHelper.checkAsync(app) }
            runCatching { com.yang.jizhang.widget.WidgetSync.refreshAsync(app) }
        }
        return saved
    }

    /** 监听器连接状态变化时同步健康标记 */
    fun onListenerConnected(context: Context) {
        ListenerHealth.setConnected(true)
        ListenerHealth.markAlive(context)
        ListenerHealth.markEverEnabled(context)
    }

    fun onListenerDisconnected() {
        ListenerHealth.setConnected(false)
    }
}
