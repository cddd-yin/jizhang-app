package com.yang.jizhang.util

import com.yang.jizhang.data.BudgetEntity
import java.util.Calendar

/**
 * v5.1.0 预算建议算法引擎（内置算法）。
 *
 * 输入预算与当前已用，输出四件套：
 * - 日均可用（剩余额度 / 剩余天数，日历感知：月预算按当月剩余天、周预算按本周剩余天）
 * - 期末外推（当前日均消耗速率 × 周期总天数 → 预计超支/结余）
 * - 消费节奏（消耗进度 vs 时间进度，±10% 容差分档：宽裕/正常/偏快/超支）
 * - 一句话建议
 */
object BudgetAdvisor {

    enum class Pace { RELAXED, NORMAL, FAST, OVER }

    data class Advice(
        val remaining: Long,
        /** 日均可用额度（分）；已超支时为 0 */
        val dailyAllowance: Long,
        /** 期末外推：预计超支（正）/ 结余（负），分 */
        val projectedDelta: Long,
        val pace: Pace,
        /** 一句话建议（可直接展示） */
        val message: String,
        val remainingDays: Int,
        val dailyAvg: Long,
    )

    fun advise(budget: BudgetEntity, spent: Long, now: Long = System.currentTimeMillis()): Advice {
        val win = Periods.currentWindow(budget.period, now)
        val cal = Calendar.getInstance().apply { timeInMillis = win[0] }
        val totalDays = ((win[1] - win[0]) + 86_399_999L) / 86_400_000L
        val elapsed = ((now - win[0]) / 86_400_000L).toInt() + 1 // 今天算第 1 天
        val remainingDays = (((win[1] - now) + 86_399_999L) / 86_400_000L).toInt().coerceAtLeast(1)
        val remaining = budget.amountCents - spent
        val dailyAvg = if (elapsed > 0) spent / elapsed else 0L
        val projected = dailyAvg * totalDays
        val delta = projected - budget.amountCents
        val unit = if (budget.period == Periods.WEEKLY) "本周" else "本月"

        // 节奏分档：消耗进度对比时间进度（±10% 容差）
        val pace = when {
            remaining < 0 -> Pace.OVER
            budget.amountCents <= 0 -> Pace.NORMAL
            spent.toFloat() / budget.amountCents > (elapsed.toFloat() / totalDays) + 0.10f -> Pace.FAST
            spent.toFloat() / budget.amountCents < (elapsed.toFloat() / totalDays) - 0.10f -> Pace.RELAXED
            else -> Pace.NORMAL
        }

        val dailyAllowance = (remaining / remainingDays).coerceAtLeast(0)

        val message = when {
            remaining < 0 ->
                "${unit}已超支 ${"%.2f".format(Math.abs(remaining) / 100.0)} 元，看看哪里能省"
            pace == Pace.FAST && delta > 0 ->
                "日均可用 ${"%.0f".format(dailyAllowance / 100.0)} 元；按当前节奏${unit}末将超支 " +
                    "${"%.0f".format(delta / 100.0)} 元，建议日控制在 ${"%.0f".format(dailyAllowance / 100.0)} 元内"
            else ->
                "剩余 ${"%.0f".format(remaining / 100.0)} 元 · 日均可用 ${"%.0f".format(dailyAllowance / 100.0)} 元 · 还剩 $remainingDays 天"
        }

        return Advice(
            remaining = remaining,
            dailyAllowance = dailyAllowance,
            projectedDelta = delta,
            pace = pace,
            message = message,
            remainingDays = remainingDays,
            dailyAvg = dailyAvg,
        )
    }
}
