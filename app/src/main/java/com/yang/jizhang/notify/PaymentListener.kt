package com.yang.jizhang.notify

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.notify.keepalive.KeepAliveService
import com.yang.jizhang.notify.keepalive.ListenerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 实时记账核心：
 * 监听系统通知栏，捕获支付宝 / 微信 / QQ / 云闪付 / 京东淘宝 / 银行 的支付通知并自动入账。
 *
 * 借鉴 SmartLedger 的成熟方案：
 *  - 完整提取通知各字段（BIG_TEXT / TEXT_LINES / MessagingStyle / ticker）
 *  - 聚合通知（[N条]）从同组子通知补全金额
 *  - 营销 / 信贷 / 非支付通知过滤
 *  - 智能去重（按金额 + 收支 + 渠道 + 时间窗）
 *  - 前台保活 + 自动重连 + 开机自启
 */
class PaymentListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentListener"

        /** 监控渠道包名（精确） */
        private val MONITORED_PACKAGES = setOf(
            "com.tencent.mm",              // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.tencent.mobileqq",        // QQ
            "com.unionpay",                // 云闪付
            "com.jd.jrapp",                // 京东金融
            "com.jingdong.app.mall",       // 京东
            "com.taobao.taobao",           // 淘宝
            "com.tmall.wireless",          // 天猫
        )

        /** 银行类 App（精确 + 宽松匹配） */
        private val BANK_PACKAGES = setOf(
            "com.icbc", "com.icbc.im", "com.icbc.icbcmb", "com.icbc.android",
            "com.chinapost.pbs", "com.psbc",
            "com.ccb.start", "com.ccb.main",
            "com.chinamworld.bocmbci", "com.boc.bocsoft.bocmbs",
            "com.abchina.abcpocket", "com.android.bankabc",
            "cmb.pb", "cmb.b2c",
            "com.chinamworld.main",
            "com.pingan.paces.ccms",
            "com.spdb.mobilebank",
            "com.cmbc", "com.cebbank", "com.cib",
            "com.bankcomm", "com.bocomm",
        )
        private val BANK_PACKAGE_HINTS = listOf(
            "icbc", "psbc", "chinapost", "ccb", "abchina", "bankabc",
            "bocmbci", "bocsoft", "cmb.pb", "cmb.b2c", "pingan.paces",
            "spdb", "cmbc", "cebbank", "bankcomm", "bocomm", "cib"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        com.yang.jizhang.notify.keepalive.NotificationChannels.ensure(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        ListenerStatus.setConnected(applicationContext, true)
        ListenerStatus.clearPendingInAppPrompt(applicationContext)
        KeepAliveService.start(applicationContext)
        KeepAliveService.refreshNotification(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected, will requestRebind")
        ListenerStatus.setConnected(applicationContext, false)
        KeepAliveService.refreshNotification(applicationContext)
        KeepAliveService.start(applicationContext)
        // 只 requestRebind，不要短时间 forceReconnect（会拆组件导致一直断开）
        mainHandler.postDelayed({
            ListenerStatus.requestRebind(applicationContext, force = true)
        }, 1500L)
        mainHandler.postDelayed({
            ListenerStatus.requestRebind(applicationContext, force = true)
        }, 6000L)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // 能收到通知 = binder 已连接（修复误标断开）
        ListenerStatus.markAliveFromNotification(applicationContext)

        val packageName = sbn.packageName
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // 兼容各渠道把金额放在 BIG_TEXT / ticker / 同组子通知里
        var (title, content) = extractNotificationText(extras)
        val ticker = notification.tickerText?.toString()?.trim().orEmpty()
        if (ticker.isNotBlank() && !content.contains(ticker) && !title.contains(ticker)) {
            content = if (content.isBlank()) ticker else "$content $ticker"
        }
        var postTime = sbn.postTime // 通知发布时间，比 currentTimeMillis 更准确
        var text = NotificationParser.normalizeNotificationText("$title $content")

        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val isMonitoredEarly = isMonitoredPackage(packageName)

        // 聚合补全：系统栏可能显示「[N条]…金额」，extras 却只有「[N条]」
        val looksGrouped = isGroupSummary || NotificationParser.looksLikeGroupedSummary(text)
        val needGroupEnrich = isMonitoredEarly && looksGrouped &&
            !NotificationParser.hasPayableAmountSignal(text) &&
            !NotificationParser.hasBankLedgerSignal(text)
        if (needGroupEnrich) {
            val enriched = enrichPaymentTextFromGroup(sbn)
            if (enriched != null) {
                title = enriched.title
                content = enriched.content
                postTime = enriched.postTime
                text = NotificationParser.normalizeNotificationText("$title $content")
                Log.d(TAG, "Enriched grouped pay notice ($packageName): $text")
            } else {
                Log.w(TAG, "Group enrich failed: pkg=$packageName text=$text")
            }
        }

        // 聚合摘要：仅监控渠道且补全/原文已像真实账务时才解析
        if (isGroupSummary) {
            val looksLikePay = NotificationParser.looksLikeLedgerChild(text) ||
                text.contains("已支付") || text.contains("付款") || text.contains("动账")
            if (!isMonitoredEarly || !looksLikePay) {
                Log.d(TAG, "Skip group summary: pkg=$packageName text=$text")
                return
            }
            Log.d(TAG, "Parse payment group summary: pkg=$packageName text=$text")
        }

        Log.d(TAG, "=== New Notification ===")
        Log.d(TAG, "Package: $packageName | Title: $title | Content: $content")

        // 白条 / 花呗 / 借呗等信贷支付：不记账
        if (NotificationParser.isCreditProductPayment(text)) return
        // 营销 / 额度 / 物流 / 提现提醒等
        if (NotificationParser.isPromotionalOrNonPayment(text)) {
            Log.d(TAG, "Promotional/non-payment, skip: $text")
            return
        }

        val isShopping = packageName.contains("jingdong") || packageName.contains("jd.jr") ||
            packageName.contains("jdlite") || packageName.contains("taobao") ||
            packageName.contains("tmall")
        val isChatPay = packageName == "com.tencent.mm" ||
            packageName.contains("AlipayGphone") ||
            packageName.contains("tencent.mobileqq")
        val isBankRelated = isBankPackage(packageName) ||
            text.contains("银行") || text.contains("工商") || text.contains("邮政") ||
            text.contains("工行") || text.contains("邮储") || text.contains("建设") ||
            text.contains("中国银行") || text.contains("农业") || text.contains("招商") ||
            text.contains("动账通知") || text.contains("交易提醒") ||
            text.contains("支付信息确认") || text.contains("储蓄卡") || text.contains("借记卡") ||
            Regex("尾号\\d{4}卡").containsMatchIn(text)

        // 银行类：必须有动账信号，禁止营销/余额/还款提醒误记
        if (isBankRelated &&
            !NotificationParser.hasBankLedgerSignal(text) &&
            !NotificationParser.hasStrongPaymentSignal(text)
        ) {
            Log.d(TAG, "Bank-related without ledger signal, skip: $text")
            return
        }

        // 微信/支付宝/QQ/电商：无明确支付确认时直接跳过（防公众号「每天5元干饭」）
        if ((isShopping || isChatPay) && !NotificationParser.hasStrongPaymentSignal(text)) {
            Log.d(TAG, "Chat/shopping without pay confirm, skip: $text")
            return
        }

        val hasExpenseKeyword = text.contains("支出") || text.contains("消费") ||
            text.contains("付款") || text.contains("支付") || text.contains("扣款") ||
            text.contains("转出")
        val hasIncomeKeyword = text.contains("收入") || text.contains("到账") ||
            text.contains("收款") || text.contains("退款") || text.contains("转入") ||
            text.contains("红包")
        if (!hasExpenseKeyword && !hasIncomeKeyword) {
            val bankOk = isBankRelated && NotificationParser.hasBankLedgerSignal(text) &&
                (text.contains("元") || text.contains("¥") || text.contains("￥"))
            if (isShopping || isChatPay || !bankOk) {
                Log.d(TAG, "No keywords, skip: $text")
                return
            }
        }

        if (!isMonitoredEarly && !isBankRelated) {
            Log.d(TAG, "Not monitored app and not bank related, skipping")
            return
        }

        val parsed = NotificationParser.parse(title, content, packageName)
        if (parsed == null) {
            Log.d(TAG, "Parse failed for: $title - $content")
            return
        }
        Log.d(TAG, "Parsed: amount=${parsed.amountCents} income=${parsed.isIncome} " +
            "merchant=${parsed.merchant} source=${parsed.source} confirm=${parsed.needsConfirm}")

        // 需用户确认的模糊账单：弹系统通知，点击进 App 确认
        if (parsed.needsConfirm) {
            askUserConfirm(parsed, postTime)
            return
        }

        scope.launch {
            try {
                saveAutoTransaction(parsed, postTime)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transaction", e)
            }
        }
    }

    private fun askUserConfirm(parsed: NotificationParser.Parsed, postTime: Long) {
        val pendingId = PendingConfirmStore.put(
            parsed, postTime
        )
        Log.d(TAG, "Ask confirm pendingId=$pendingId reason=${parsed.confirmReason}")
        com.yang.jizhang.notify.keepalive.NotificationChannels.notifyNeedsConfirm(
            applicationContext,
            pendingId,
            parsed.amountCents,
            parsed.source,
            parsed.confirmReason
        )
    }

    private suspend fun saveAutoTransaction(parsed: NotificationParser.Parsed, postTime: Long) {
        val app = application as? JizhangApp ?: return
        val dao = app.db.txDao()

        // 智能去重：同金额 + 同收支 + 同商户（或同渠道无商户短窗）内不重复入账
        val duplicate = DedupHelper.findDuplicate(dao, parsed, postTime)
        if (duplicate != null) {
            Log.d(TAG, "Duplicate skipped: amount=${parsed.amountCents} source=${parsed.source}")
            return
        }

        val id = dao.insertIgnore(
            TxEntity(
                amountCents = parsed.amountCents,
                isIncome = parsed.isIncome,
                category = CategoryRules.guess(parsed.merchant, parsed.source),
                note = parsed.merchant.ifBlank { parsed.source },
                source = parsed.source,
                time = postTime,
                dedupeKey = parsed.dedupeKey,
            )
        )
        Log.d(TAG, "Saved: id=$id ${parsed.amountCents} ${if (parsed.isIncome) "收入" else "支出"} ${parsed.source}")

        // 预算超支本地提醒（真实入库后才检查）
        if (id != -1L) {
            com.yang.jizhang.util.BudgetAlertHelper.checkAsync(applicationContext)
        }

        // 低调通知：自动记了一笔（可点击进 App 查看）；并发去重被 IGNORE 时 id=-1，不再谎报
        if (id != -1L) try {
            com.yang.jizhang.notify.keepalive.NotificationChannels.notifyPaymentDetected(
                applicationContext,
                parsed.amountCents,
                parsed.merchant,
                parsed.source,
                parsed.isIncome,
                id,
            )
        } catch (e: Exception) {
            Log.w(TAG, "notifyPaymentDetected failed", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    /**
     * 从通知 extras 中提取完整的标题和正文
     * 兼容微信等把金额放在 EXTRA_BIG_TEXT / EXTRA_TEXT_LINES / MessagingStyle 的通知
     */
    private fun extractNotificationText(extras: android.os.Bundle): Pair<String, String> {
        val title = (
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
                ?: ""
            )

        val parts = mutableListOf<String>()
        fun addIfNew(text: CharSequence?) {
            val trimmed = text?.toString()?.trim() ?: return
            if (trimmed.isEmpty()) return
            val existingIndex = parts.indexOfFirst { it.contains(trimmed) || trimmed.contains(it) }
            if (existingIndex >= 0) {
                if (trimmed.length > parts[existingIndex].length) {
                    parts[existingIndex] = trimmed
                }
            } else {
                parts.add(trimmed)
            }
        }

        addIfNew(extras.getCharSequence(Notification.EXTRA_TEXT))
        addIfNew(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null) for (line in lines) addIfNew(line)
        addIfNew(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        addIfNew(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        addIfNew(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        // MessagingStyle 消息列表（部分机型微信聚合走这里）
        try {
            @Suppress("DEPRECATION")
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null) {
                for (msg in messages) {
                    if (msg is android.os.Bundle) {
                        addIfNew(msg.getCharSequence("text"))
                    }
                }
            }
        } catch (_: Exception) {
        }

        val content = parts.joinToString(" ")
        Log.d(TAG, "extractNotificationText: title='$title', content='$content' (from ${parts.size} parts)")
        return title to content
    }

    private data class EnrichedPayText(
        val title: String,
        val content: String,
        val postTime: Long
    )

    /**
     * 从同组 / 同包最近活跃通知中找出带真实账务金额的子通知。
     * 适用于微信/支付宝聚合、银行 App 折叠动账、云闪付/电商收银台等。
     */
    private fun enrichPaymentTextFromGroup(sbn: StatusBarNotification): EnrichedPayText? {
        val active = try {
            activeNotifications ?: return null
        } catch (e: Exception) {
            Log.w(TAG, "activeNotifications unavailable", e)
            return null
        }
        val groupKey = sbn.groupKey
        val notifGroup = sbn.notification.group
        val children = active
            .asSequence()
            .filter { it.packageName == sbn.packageName }
            .filter { it.key != sbn.key }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .sortedByDescending { it.postTime }
            .toList()

        fun pickFrom(list: List<StatusBarNotification>, tag: String): EnrichedPayText? {
            for (child in list) {
                val extras = child.notification?.extras ?: continue
                val (t, c) = extractNotificationText(extras)
                val ticker = child.notification?.tickerText?.toString()?.trim().orEmpty()
                val combined = NotificationParser.normalizeNotificationText(
                    listOf(t, c, ticker).filter { it.isNotBlank() }.joinToString(" ")
                )
                if (NotificationParser.looksLikeLedgerChild(combined)) {
                    Log.d(TAG, "Found ledger child ($tag): pkg=${child.packageName} text=$combined")
                    return EnrichedPayText(
                        t.ifBlank { ticker },
                        c.ifBlank { ticker },
                        child.postTime
                    )
                }
            }
            return null
        }

        val sameGroup = children.filter { child ->
            (!groupKey.isNullOrBlank() && child.groupKey == groupKey) ||
                (!notifGroup.isNullOrBlank() && child.notification.group == notifGroup)
        }
        pickFrom(sameGroup, "sameGroup")?.let { return it }

        // 回退：同包近 5 分钟内任意带账务金额的子通知（覆盖 groupKey 不一致）
        val recent = children.filter {
            kotlin.math.abs(sbn.postTime - it.postTime) <= 300_000L
        }
        pickFrom(recent, "recentSamePkg")?.let { return it }

        return null
    }

    private fun isMonitoredPackage(packageName: String): Boolean {
        if (packageName in MONITORED_PACKAGES || packageName in BANK_PACKAGES) return true
        if (BANK_PACKAGE_HINTS.any { packageName.contains(it, ignoreCase = true) }) return true
        if (packageName.contains("jd.jr") || packageName.contains("jingdong")) return true
        return false
    }

    private fun isBankPackage(packageName: String): Boolean {
        if (packageName in BANK_PACKAGES) return true
        return BANK_PACKAGE_HINTS.any { packageName.contains(it, ignoreCase = true) }
    }
}
