package com.yang.jizhang.engine.keepalive

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
 * v4.0.0 引擎重写：通知渠道与系统通知的唯一出口（替代旧 NotificationChannels）。
 * 渠道四条：自动记账 / 待确认 / 保活前台 / 异常 + 预算 + 小结。
 */
object ChannelIds {
    const val AUTO_RECORD = "auto_record"
    const val NEEDS_REVIEW = "needs_review"
    const val GUARDIAN = "guardian_fg"
    const val ALERT = "engine_alert"
    const val BUDGET = "budget_alert"

    private const val ID_AUTO_BASE = 2001
    private const val ID_REVIEW_BASE = 3001
    const val ID_GUARDIAN_FG = 1002
    const val ID_WATCH_DOWN = 1003
    private const val ID_BUDGET_BASE = 4001
    private const val ID_DIGEST = 5001

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        fun ch(id: String, name: String, importance: Int, desc: String, vibrate: Boolean) =
            nm.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    description = desc
                    enableVibration(vibrate)
                    setShowBadge(importance >= NotificationManager.IMPORTANCE_DEFAULT)
                    if (!vibrate) setSound(null, null)
                }
            )
        ch(AUTO_RECORD, "自动记账", NotificationManager.IMPORTANCE_LOW, "自动记账成功（低打扰）", false)
        ch(NEEDS_REVIEW, "账单确认", NotificationManager.IMPORTANCE_HIGH, "识别不确定的账单待确认", true)
        ch(GUARDIAN, "后台自动记账", NotificationManager.IMPORTANCE_MIN, "保活前台服务（可隐藏）", false)
        ch(ALERT, "监听异常提醒", NotificationManager.IMPORTANCE_HIGH, "支付监听长时间未连上", true)
        ch(BUDGET, "预算提醒", NotificationManager.IMPORTANCE_DEFAULT, "预算 80%/用完/超支提醒", true)
    }

    fun openApp(ctx: Context, rc: Int = 0): PendingIntent = PendingIntent.getActivity(
        ctx, rc,
        Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(ctx: Context, id: Int, n: Notification) {
        ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    /** 自动记账成功（低打扰，点击进 App）；budgetHint 为预算建议短句（v5.1.0） */
    fun autoRecorded(
        ctx: Context,
        cents: Long,
        merchant: String?,
        channel: String,
        income: Boolean,
        txId: Long,
        budgetHint: String? = null,
    ) {
        ensure(ctx)
        val amount = "¥%.2f".format(cents / 100.0)
        val detail = buildString {
            append(channel.ifBlank { "自动记账" })
            if (!merchant.isNullOrBlank()) append(" · $merchant")
            if (budgetHint != null) append("\n预算：$budgetHint")
        }
        val n = NotificationCompat.Builder(ctx, AUTO_RECORD)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("${if (income) "收入" else "支出"}  $amount")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\n已记入账单，点击查看"))
            .setContentIntent(openApp(ctx, (txId % 60000).toInt()))
            .setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS).setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notify(ctx, ID_AUTO_BASE + (txId % 100000).toInt(), n)
    }

    /** 待确认账单（高优先级，点击进 App 确认） */
    fun needsReview(ctx: Context, pendingId: Long, cents: Long, channel: String, reason: String?) {
        ensure(ctx)
        val n = NotificationCompat.Builder(ctx, NEEDS_REVIEW)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("待确认账单 ¥%.2f".format(cents / 100.0))
            .setContentText("$channel · ${reason ?: "识别不确定"}，点击确认")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$channel 通知已捕获，但金额或方向不确定，点开 App 确认后入账"))
            .setContentIntent(openApp(ctx, (pendingId % 60000).toInt() + 1))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION).setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notify(ctx, ID_REVIEW_BASE + (pendingId % 100000).toInt(), n)
    }

    /** 保活前台常驻 */
    fun guardian(ctx: Context, stateText: String): Notification {
        ensure(ctx)
        return NotificationCompat.Builder(ctx, GUARDIAN)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("记账 · 自动记账运行中")
            .setContentText(stateText)
            .setOngoing(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    /** 监听断开告警 */
    fun watchDown(ctx: Context, text: String) {
        ensure(ctx)
        val n = NotificationCompat.Builder(ctx, ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("支付监听未连接")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n点击打开 App 自动恢复"))
            .setContentIntent(openApp(ctx, ID_WATCH_DOWN))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR).setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notify(ctx, ID_WATCH_DOWN, n)
    }

    fun cancelWatchDown(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_WATCH_DOWN)
    }

    /** 预算提醒（标题做 ID 种子，同预算同档只留最新） */
    fun budgetAlert(ctx: Context, title: String, text: String) {
        ensure(ctx)
        val n = NotificationCompat.Builder(ctx, BUDGET)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text).setBigContentTitle(title))
            .setContentIntent(openApp(ctx, ID_BUDGET_BASE))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notify(ctx, ID_BUDGET_BASE + (title.hashCode() % 100000).toInt(), n)
    }

    /** 每日小结 */
    fun dailyDigest(ctx: Context, count: Int, expenseCents: Long, incomeCents: Long) {
        ensure(ctx)
        val title = "今日自动记账 $count 笔"
        val detail = buildString {
            append("支出 ¥%.2f".format(expenseCents / 100.0))
            if (incomeCents > 0) append(" · 收入 ¥%.2f".format(incomeCents / 100.0))
            append("\n点开核对一下，确认没有漏记")
        }
        val n = NotificationCompat.Builder(ctx, AUTO_RECORD)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title).setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail).setBigContentTitle(title))
            .setContentIntent(openApp(ctx, ID_DIGEST))
            .setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS).setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notify(ctx, ID_DIGEST, n)
    }
}
