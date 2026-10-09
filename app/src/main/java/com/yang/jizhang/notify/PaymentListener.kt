package com.yang.jizhang.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 实时记账核心：
 * 监听系统通知栏，捕获支付宝 / 微信 / QQ 的支付通知并自动入账。
 * 需要用户在「设置 → 通知使用权」中授权本 App。
 */
class PaymentListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val watchPackages = mapOf(
        "com.eg.android.AlipayGphone" to Source.ALIPAY,
        "com.tencent.mm" to Source.WECHAT,
        "com.tencent.mobileqq" to Source.QQ,
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = watchPackages[sbn.packageName] ?: return
        val n: Notification = sbn.notification ?: return
        val extras = n.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val parsed = NotificationParser.parse(sbn.packageName, source, title, text) ?: return
        val app = application as? JizhangApp ?: return

        scope.launch {
            app.db.txDao().insertIgnore(
                TxEntity(
                    amountCents = parsed.amountCents,
                    isIncome = parsed.isIncome,
                    category = CategoryRules.guess(parsed.merchant, text),
                    note = parsed.merchant.ifBlank { "自动记账" },
                    source = source,
                    time = System.currentTimeMillis(),
                    dedupeKey = parsed.dedupeKey,
                )
            )
        }
    }
}
