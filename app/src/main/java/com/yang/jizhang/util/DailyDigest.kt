package com.yang.jizhang.util

import android.content.Context
import com.yang.jizhang.data.AppDatabase
import com.yang.jizhang.data.Source
import com.yang.jizhang.engine.keepalive.ChannelIds
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * 每日自动记账小结（v3.11.0）：晚上 21 点后，若当天有自动记账则发一条汇总通知，
 * 供用户核对当日账单、发现漏记。每自然日最多一条（prefs 记录已发日期）。
 * 触发：保活服务心跳顺带检查（后台即可发）；App 前台使用时不打扰（用户自己看得到）。
 */
object DailyDigest {
    private const val PREFS = "jizhang_prefs"
    private const val KEY_SENT_DATE = "daily_digest_date"
    private const val SEND_HOUR = 21

    /** 后台线程执行（DB 读取为 suspend），心跳直接调用不阻塞主线程 */
    fun maybeNotify(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        Thread {
            try {
                runBlocking { checkAndNotify(app, now) }
            } catch (_: Exception) {
            }
        }.start()
    }

    private suspend fun checkAndNotify(context: Context, now: Long) {
        val ctx = context.applicationContext
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        if (cal.get(Calendar.HOUR_OF_DAY) < SEND_HOUR) return
        val today = "%04d%02d%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SENT_DATE, "") == today) return

        // 今日自然日内、来自通知监听渠道的记录
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val auto = try {
            AppDatabase.get(ctx).txDao().getByTimeRangeOnce(dayStart, now)
                .filter { it.source in listOf(Source.ALIPAY, Source.WECHAT, Source.QQ) }
        } catch (_: Exception) {
            return
        }
        if (auto.isEmpty()) return

        val expense = auto.filter { !it.isIncome }.sumOf { it.amountCents }
        val income = auto.filter { it.isIncome }.sumOf { it.amountCents }
        try {
            ChannelIds.dailyDigest(ctx, auto.size, expense, income)
        } catch (_: Exception) {
            return
        }
        prefs.edit().putString(KEY_SENT_DATE, today).apply()
    }
}
