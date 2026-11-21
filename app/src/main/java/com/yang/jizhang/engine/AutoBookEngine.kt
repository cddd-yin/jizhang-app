package com.yang.jizhang.engine

import android.content.Context
import androidx.room.withTransaction
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.PendingBill
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.engine.keepalive.ChannelIds
import com.yang.jizhang.engine.keepalive.ListenerHealth
import com.yang.jizhang.engine.payment.DetectedPayment
import com.yang.jizhang.engine.payment.ScreenPaymentMonitor
import com.yang.jizhang.engine.payment.ScreenPaymentPolicy
import com.yang.jizhang.util.BudgetAlertHelper
import java.security.MessageDigest
import java.util.UUID

/**
 * v4.0.0 引擎重写：自动入账编排（替代旧 PaymentListener.saveAutoTransaction 散落逻辑）。
 * 管线：解析 → 去重 → 高置信直入 / 低置信进待确认队列 → 通知 + 预算检查 + 小组件刷新。
 */
object AutoBookEngine {
    private const val REVIEW_REPLAY_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L
    private enum class Intake { BOOKED, REVIEW, SILENT_REVIEW }
    private data class Ingested(val id: Long, val intake: Intake, val detail: String? = null)

    internal enum class ReviewResult { SAVED, ALREADY_HANDLED, POSSIBLE_DUPLICATE }

    /** 监听器收到通知的统一入口（监听器工作协程内调用） */
    suspend fun ingest(context: Context, pkg: String, title: String, text: String, postTime: Long, notificationKey: String) {
        if (!BillParser.mightBeBill(pkg, title, text)) return
        val bill = BillParser.parse(pkg, title, text) ?: return
        val app = context.applicationContext

        val db = (app as JizhangApp).db
        val saved = db.withTransaction {
            val dao = db.txDao()
            val pendingDao = db.pendingBillDao()
            val window = ScreenPaymentPolicy.REVIEW_WINDOW_MS
            val automaticKey = "AUTO|${bill.fingerprint}|${postTime / 25_000}"
            val screenAlsoSawPayment = !bill.isIncome && bill.channel in setOf(Source.WECHAT, Source.ALIPAY) && (
                pendingDao.hasNearbyScreenPayment(bill.channel, bill.amountCents, postTime - window, postTime + window) ||
                    dao.getByTimeRangeOnce(postTime - window, postTime + window).any {
                        it.source == bill.channel && !it.isIncome && it.amountCents == bill.amountCents &&
                            ScreenPaymentPolicy.isConfirmedScreen(it.dedupeKey)
                    }
                )
            if (bill.needsReview || screenAlsoSawPayment) {
                if (screenAlsoSawPayment && dao.findByDedupeKey(automaticKey) != null) return@withTransaction null
                val eventKey = MessageDigest.getInstance("SHA-256")
                    .digest("$notificationKey|$postTime|${bill.fingerprint}".toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                pendingDao.pruneHandled(System.currentTimeMillis() - REVIEW_REPLAY_WINDOW_MS)
                if (dao.findByDedupeKey(ScreenPaymentPolicy.CONFIRMED_PREFIX + eventKey) != null) return@withTransaction null
                val reason = if (screenAlsoSawPayment) ScreenPaymentPolicy.POSSIBLE_DUPLICATE_REASON else bill.reviewReason
                val rawSnippet = if (screenAlsoSawPayment) null
                else bill.classificationDetails.ifBlank { bill.rawSnippet.orEmpty() }.ifBlank { null }
                val id = pendingDao.insert(
                    PendingBill(
                        eventKey = eventKey,
                        amountCents = bill.amountCents,
                        isIncome = bill.isIncome,
                        merchant = bill.merchant,
                        source = bill.channel,
                        transactionTime = postTime,
                        reason = reason,
                        rawSnippet = rawSnippet,
                    ),
                )
                val intake = if (screenAlsoSawPayment) Intake.SILENT_REVIEW else Intake.REVIEW
                return@withTransaction Ingested(id, intake, reason)
            }

            if (DuplicateGuard.findDuplicate(dao, bill, eventTime = postTime) != null) return@withTransaction null
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
                    dedupeKey = automaticKey,
                ),
            )
            Ingested(id, Intake.BOOKED, category)
        } ?: return
        if (saved.id == -1L) return

        when (saved.intake) {
            Intake.REVIEW -> {
                runCatching { ChannelIds.needsReview(app, saved.id, bill.amountCents, bill.channel, saved.detail) }
                if (db.pendingBillDao().find(saved.id) == null) runCatching { ChannelIds.cancelReview(app, saved.id) }
            }
            Intake.SILENT_REVIEW -> Unit
            Intake.BOOKED -> {
                val hint = if (!bill.isIncome) budgetHintFor(app, requireNotNull(saved.detail)) else null
                runCatching { ChannelIds.autoRecorded(app, bill.amountCents, bill.merchant, bill.channel, bill.isIncome, saved.id, hint) }
                BudgetAlertHelper.checkAsync(app)
                com.yang.jizhang.widget.WidgetSync.refreshAsync(app)
            }
        }
    }

    internal suspend fun queueScreenPayment(context: Context, payment: DetectedPayment, capturedAt: Long) {
        val app = context.applicationContext as JizhangApp
        val db = app.db
        db.withTransaction {
            if (!ScreenPaymentMonitor.isEnabled(app)) return@withTransaction
            val pending = db.pendingBillDao()
            val source = payment.source.label
            val digest = payment.referenceDigest
            val eventKey = if (digest != null) ScreenPaymentPolicy.REFERENCE_PREFIX + digest
            else "${ScreenPaymentPolicy.EVENT_PREFIX}VISIT|${UUID.randomUUID()}"
            if (db.txDao().findByDedupeKey(ScreenPaymentPolicy.CONFIRMED_PREFIX + eventKey) != null) return@withTransaction
            pending.pruneHandled(System.currentTimeMillis() - REVIEW_REPLAY_WINDOW_MS)
            pending.insert(PendingBill(eventKey = eventKey, amountCents = payment.cents, isIncome = false,
                merchant = "", source = source, transactionTime = capturedAt, reason = null, rawSnippet = null))
        }
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
    internal suspend fun confirm(
        context: Context,
        pendingId: Long,
        amountCents: Long,
        isIncome: Boolean,
        allowPossibleDuplicate: Boolean = false,
    ): ReviewResult {
        require(amountCents > 0)
        val app = context.applicationContext as JizhangApp
        val db = app.db
        val saved = db.withTransaction {
            val pending = db.pendingBillDao().find(pendingId) ?: return@withTransaction ReviewResult.ALREADY_HANDLED
            val dao = db.txDao()
            val confirmedKey = ScreenPaymentPolicy.CONFIRMED_PREFIX + pending.eventKey
            val window = ScreenPaymentPolicy.REVIEW_WINDOW_MS
            if (!allowPossibleDuplicate && !isIncome && pending.source in setOf(Source.WECHAT, Source.ALIPAY) &&
                dao.getByTimeRangeOnce(pending.transactionTime - window, pending.transactionTime + window).any {
                    it.dedupeKey != confirmedKey && it.source == pending.source && !it.isIncome && it.amountCents == amountCents &&
                        ScreenPaymentPolicy.requiresDuplicateReview(pending.eventKey, it.dedupeKey)
                }
            ) return@withTransaction ReviewResult.POSSIBLE_DUPLICATE

            val id = dao.insertIgnore(
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
                    dedupeKey = confirmedKey,
                ),
            )
            db.pendingBillDao().markHandled(pendingId, System.currentTimeMillis())
            if (id == -1L) ReviewResult.ALREADY_HANDLED else ReviewResult.SAVED
        }
        if (saved == ReviewResult.SAVED) {
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
