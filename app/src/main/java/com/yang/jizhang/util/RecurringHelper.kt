package com.yang.jizhang.util

import android.content.Context
import com.yang.jizhang.data.AppDatabase
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import kotlinx.coroutines.runBlocking

/**
 * 周期记账补账引擎（v3.1.0）：
 * 扫描启用中的周期规则，把「上次检查以来已到达锚点」的每期入账（按期的时间戳落库，
 * dedupeKey = RECUR|规则|锚点时刻 保证幂等），然后推进 lastRunAt。
 * 触发点：App 每次打开时补账 + 保活服务心跳兜底；最长只回补 60 天。
 */
object RecurringHelper {
    private const val BACKFILL_MS = 60L * 24 * 3600 * 1000
    private const val MIN_INTERVAL_MS = 10L * 60 * 1000

    @Volatile private var lastAsyncRunAt = 0L

    suspend fun processDue(context: Context, now: Long = System.currentTimeMillis()): Int {
        val db = AppDatabase.get(context)
        val rules = try {
            db.recurringDao().getEnabledOnce()
        } catch (_: Exception) {
            return 0
        }
        var inserted = 0
        for (rule in rules) {
            val floor = maxOf(rule.lastRunAt, now - BACKFILL_MS)
            var t = Periods.nextAnchorAt(rule.period, rule.anchor, floor)
            var guard = 0
            while (t <= now && guard < 10) {
                val key = "RECUR|${rule.id}|$t"
                val tx = TxEntity(
                    amountCents = rule.amountCents,
                    isIncome = rule.isIncome,
                    category = rule.category,
                    note = rule.note.ifBlank { rule.title },
                    source = Source.RECUR,
                    time = t,
                    dedupeKey = key,
                )
                val rowId = try {
                    db.txDao().insertIgnore(tx)
                } catch (_: Exception) {
                    -1L
                }
                if (rowId != -1L) inserted++
                t = Periods.nextAnchorAt(rule.period, rule.anchor, t)
                guard++
            }
            try {
                db.recurringDao().update(rule.copy(lastRunAt = now))
            } catch (_: Exception) {
            }
        }
        return inserted
    }

    /** 保活服务心跳兜底：独立线程执行，不阻塞主线程；节流至最少 10 分钟一次 */
    fun processDueAsync(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAsyncRunAt < MIN_INTERVAL_MS) return
        lastAsyncRunAt = now
        val app = context.applicationContext
        Thread {
            try {
                runBlocking { processDue(app) }
            } catch (_: Exception) {
            }
        }.start()
    }
}
