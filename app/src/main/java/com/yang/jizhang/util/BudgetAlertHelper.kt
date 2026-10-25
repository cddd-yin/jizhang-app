package com.yang.jizhang.util

import android.content.Context
import com.yang.jizhang.data.AppDatabase
import com.yang.jizhang.data.BudgetEntity
import com.yang.jizhang.notify.keepalive.NotificationChannels
import com.yang.jizhang.ui.fmtCents
import kotlinx.coroutines.runBlocking

/**
 * 预算超支本地提醒（v3.2.0）：
 * 对每个启用中的预算计算当前窗口已用比例，跨过 80% / 100% 阈值时发本地通知；
 * 每个预算每个周期内每档只提醒一次（档位状态记在 SharedPreferences，随窗口重置）。
 * 触发点：自动记账入库 / 手动记账 / 导入 / 周期记账补账后，以及保活心跳兜底。
 */
object BudgetAlertHelper {
    private const val MIN_INTERVAL_MS = 60L * 1000

    @Volatile private var lastCheckAt = 0L

    /** 已提醒档位：0=未提醒 80=到 80% 100=到 100% 200=已超支 */
    private fun levelKey(budgetId: Long, windowStart: Long) = "budget_alert_${budgetId}_$windowStart"

    suspend fun check(context: Context, now: Long = System.currentTimeMillis()) {
        val db = AppDatabase.get(context)
        val budgets = try {
            db.budgetDao().getAllOnce().filter { it.enabled }
        } catch (_: Exception) {
            return
        }
        if (budgets.isEmpty()) return
        val prefs = context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
        val earliestStart = budgets.minOfOrNull { Periods.currentWindow(it.period)[0] } ?: return
        val txs = try {
            db.txDao().getByTimeRangeOnce(earliestStart, now)
        } catch (_: Exception) {
            return
        }

        for (budget in budgets) {
            val win = Periods.currentWindow(budget.period, now)
            val spent = txs
                .filter { !it.isIncome && it.time in win[0] until win[1] }
                .filter { budget.category.isEmpty() || it.category == budget.category }
                .sumOf { it.amountCents }
            if (budget.amountCents <= 0) continue
            val frac = spent.toDouble() / budget.amountCents
            val level = when {
                frac > 1.0 -> 200
                frac >= 1.0 -> 100
                frac >= 0.8 -> 80
                else -> 0
            }
            val key = levelKey(budget.id, win[0])
            val notified = prefs.getInt(key, 0)
            if (level <= notified) continue

            val name = budget.category.ifEmpty { "总预算" }
            val periodLabel = Periods.label(budget.period)
            val title: String
            val text: String
            when (level) {
                80 -> {
                    title = "$name 预算提醒"
                    text = "$periodLabel$name 预算已用 ${"%.0f".format(frac * 100)}%（${fmtCents(spent)} / ${fmtCents(budget.amountCents)}），注意安排"
                }
                100 -> {
                    title = "$name 预算用完"
                    text = "$periodLabel$name 预算已达上限：${fmtCents(spent)} / ${fmtCents(budget.amountCents)}"
                }
                else -> {
                    title = "$name 预算已超支"
                    text = "$periodLabel$name 已用 ${fmtCents(spent)}，超出预算 ${fmtCents(spent - budget.amountCents)}"
                }
            }
            NotificationChannels.notifyBudgetAlert(context, title, text)
            prefs.edit().putInt(key, level).apply()
        }
    }

    /** 心跳/后台兜底：独立线程 + 节流（最少 60 秒一次） */
    fun checkAsync(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastCheckAt < MIN_INTERVAL_MS) return
        lastCheckAt = now
        val app = context.applicationContext
        Thread {
            try {
                runBlocking { check(app) }
            } catch (_: Exception) {
            }
        }.start()
    }
}
