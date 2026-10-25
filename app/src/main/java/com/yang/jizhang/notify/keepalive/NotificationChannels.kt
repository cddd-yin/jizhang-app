package com.yang.jizhang.notify.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yang.jizhang.ui.MainActivity

/**
 * 系统通知统一样式：记账检测 / 待确认 / 保活前台 / 监听异常。
 */
object NotificationChannels {

    const val CHANNEL_PAYMENT = "payment_detected"
    const val CHANNEL_CONFIRM = "payment_confirm"
    const val CHANNEL_KEEP_ALIVE = "keep_alive"
    const val CHANNEL_LISTENER_ALERT = "listener_alert"
    const val CHANNEL_BUDGET = "budget_alert"

    private const val ID_PAYMENT_BASE = 2001
    private const val ID_CONFIRM_BASE = 3001
    const val ID_KEEP_ALIVE = 1002
    const val ID_LISTENER_DOWN = 1003
    private const val ID_BUDGET_BASE = 4001

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PAYMENT,
                "自动记账",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "自动记账成功后的提醒（低打扰）"
                enableVibration(false)
                setShowBadge(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONFIRM,
                "账单确认",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "模糊或可疑账单，需确认后才记入"
                enableVibration(true)
                setShowBadge(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_KEEP_ALIVE,
                "后台自动记账",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持支付监听，可在系统设置中隐藏"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LISTENER_ALERT,
                "监听异常提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "支付监听长时间未连上时提醒"
                enableVibration(true)
                setShowBadge(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUDGET,
                "预算提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "预算用到 80%、用完或超支时提醒"
                enableVibration(true)
                setShowBadge(true)
            }
        )
    }

    fun openAppPendingIntent(context: Context, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 自动记账成功通知
     */
    fun notifyPaymentDetected(
        context: Context,
        amountCents: Long,
        merchant: String?,
        source: String,
        isIncome: Boolean,
        transactionId: Long
    ) {
        ensure(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val typeLabel = if (isIncome) "收入" else "支出"
        val amountText = "¥" + (amountCents / 100.0).let {
            String.format("%.2f", it)
        }
        val detail = buildString {
            append(source.ifBlank { "自动记账" })
            if (!merchant.isNullOrBlank()) {
                append(" · ")
                append(merchant)
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PAYMENT)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("$typeLabel  $amountText")
            .setContentText(detail)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$detail\n已记入账单，点击查看详情")
                    .setBigContentTitle("$typeLabel  $amountText")
            )
            .setContentIntent(openAppPendingIntent(context, transactionId.toInt()))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(ID_PAYMENT_BASE + (transactionId % 100000).toInt(), notification)
    }

    /**
     * 预算提醒通知（80% / 用完 / 超支三档，点击打开 App）。
     */
    fun notifyBudgetAlert(context: Context, title: String, text: String) {
        ensure(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(text)
                    .setBigContentTitle(title)
            )
            .setContentIntent(openAppPendingIntent(context, ID_BUDGET_BASE))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // 同预算同档只保留最新一条：标题做 notificationId 的稳定种子
        manager.notify(ID_BUDGET_BASE + (title.hashCode() % 100000).toInt(), notification)
    }

    /**
     * 模糊账单：点击打开 App 确认页（可改金额）。
     */
    fun notifyNeedsConfirm(
        context: Context,
        pendingId: Long,
        amountCents: Long,
        source: String,
        reason: String?
    ) {
        ensure(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val amountText = if (amountCents > 0)
            "¥" + String.format("%.2f", amountCents / 100.0) else "金额待确认"
        val detail = buildString {
            append(source)
            if (!reason.isNullOrBlank()) {
                append(" · ")
                append(reason)
            }
            append(" · 点此确认或修改后入账")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CONFIRM)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("待确认 $amountText")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openAppPendingIntent(context, (ID_CONFIRM_BASE + pendingId).toInt()))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(ID_CONFIRM_BASE + (pendingId % 100000).toInt(), notification)
    }

    fun cancelConfirm(context: Context, pendingId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(ID_CONFIRM_BASE + (pendingId % 100000).toInt())
    }

    /**
     * 保活前台通知（低调）
     */
    fun buildKeepAlive(
        context: Context,
        state: ListenerStatus.DisplayState
    ): Notification {
        ensure(context)
        val text = when (state) {
            ListenerStatus.DisplayState.CONNECTED ->
                "正在自动记录微信 / 支付宝 / QQ / 银行卡"
            ListenerStatus.DisplayState.NEED_PERMISSION ->
                "请开启通知使用权后才能自动记账"
            ListenerStatus.DisplayState.RECOVERING ->
                "正在重新连接支付监听…"
        }
        return NotificationCompat.Builder(context, CHANNEL_KEEP_ALIVE)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("蓝鲸记账")
            .setContentText(text)
            .setContentIntent(openAppPendingIntent(context, ID_KEEP_ALIVE))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 监听长时间未连上：可点进 App 触发重连 */
    fun notifyListenerDown(context: Context) {
        ensure(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_LISTENER_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("支付监听未连接")
            .setContentText("点按打开蓝鲸记账以恢复自动记账")
            .setContentIntent(openAppPendingIntent(context, ID_LISTENER_DOWN))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        manager.notify(ID_LISTENER_DOWN, notification)
    }

    fun cancelListenerDown(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(ID_LISTENER_DOWN)
    }
}
