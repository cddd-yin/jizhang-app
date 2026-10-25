package com.yang.jizhang.engine

import android.content.Context
import android.util.Log
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.engine.keepalive.ChannelIds
import com.yang.jizhang.engine.keepalive.ListenerHealth
import com.yang.jizhang.util.BudgetAlertHelper
import kotlinx.coroutines.runBlocking

/**
 * v4.0.0 引擎重写：自动入账编排（替代旧 PaymentListener.saveAutoTransaction 散落逻辑）。
 * 管线：解析 → 去重 → 高置信直入 / 低置信进待确认队列 → 通知 + 预算检查 + 小组件刷新。
 */
object AutoBookEngine {
    private const val TAG = "AutoBook"

    /** 监听器收到通知的统一入口（监听器工作协程内调用） */
    suspend fun ingest(context: Context, pkg: String, title: String, text: String, postTime: Long) {
        if (!BillParser.mightBeBill(pkg, title, text)) return
        val bill = BillParser.parse(pkg, title, text) ?: return
        val app = context.applicationContext

        if (bill.needsReview) {
            val id = UncertainBillQueue.put(bill, postTime)
            ChannelIds.needsReview(app, id, bill.amountCents, bill.channel, bill.reviewReason)
            Log.d(TAG, "queued for review: ${bill.amountCents} ${bill.channel}")
            return
        }

        val dao = (app as JizhangApp).db.txDao()
        if (DuplicateGuard.findDuplicate(dao, bill, eventTime = postTime) != null) {
            Log.d(TAG, "duplicate skipped: ${bill.amountCents} ${bill.channel}")
            return
        }

        val category = com.yang.jizhang.util.CategoryLearner.hint(app, bill.merchant)
            ?: CategoryRules.guess(bill.merchant, bill.channel)
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

    /** 待确认账单被用户确认后入库（UI 调用） */
    fun confirm(context: Context, pending: UncertainBillQueue.Pending, amountCents: Long, isIncome: Boolean) {
        val app = context.applicationContext
        Thread {
            try {
                runBlocking {
                    val dao = (app as JizhangApp).db.txDao()
                    val id = dao.insertIgnore(
                        TxEntity(
                            amountCents = amountCents,
                            isIncome = isIncome,
                            category = com.yang.jizhang.util.CategoryLearner.hint(app, pending.merchant)
                                ?: CategoryRules.guess(pending.merchant, pending.source),
                            note = pending.merchant.ifBlank { pending.source },
                            source = pending.source,
                            time = pending.transactionTime,
                            dedupeKey = "CONFIRM|${pending.id}",
                        )
                    )
                    if (id != -1L) {
                        BudgetAlertHelper.check(app)
                        com.yang.jizhang.widget.WidgetSync.refreshAsync(app)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "confirm failed", e)
            }
        }.start()
    }

    /** 监听器连接状态变化时同步健康标记 */
    fun onListenerConnected(context: Context) {
        ListenerHealth.setConnected(true)
        ListenerHealth.markAlive(context)
        ListenerHealth.markEverEnabled(context)
    }

    fun onListenerDisconnected(context: Context) {
        ListenerHealth.setConnected(false)
    }
}
