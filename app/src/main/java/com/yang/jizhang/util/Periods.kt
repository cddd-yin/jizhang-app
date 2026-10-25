package com.yang.jizhang.util

import java.util.Calendar

/**
 * 预算/周期记账的周期工具（v3.1.0）。
 * monthly：自然月（1 日 00:00 起）；weekly：自然周（周一 00:00 起）。
 */
object Periods {
    const val MONTHLY = "monthly"
    const val WEEKLY = "weekly"

    /** 当前周期窗口 [start, end) */
    fun currentWindow(period: String, now: Long = System.currentTimeMillis()): LongArray {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return when (period) {
            WEEKLY -> {
                setMidnight(cal)
                val iso = isoDayOfWeek(cal)
                cal.add(Calendar.DAY_OF_YEAR, -(iso - 1))
                val start = cal.timeInMillis
                longArrayOf(start, start + 7L * 24 * 3600 * 1000)
            }
            else -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                setMidnight(cal)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                longArrayOf(start, cal.timeInMillis)
            }
        }
    }

    /** 周期中文名 */
    fun label(period: String): String = if (period == WEEKLY) "每周" else "每月"

    /** 锚点中文名：每月 5 日 / 每周三 */
    fun anchorLabel(period: String, anchor: Int): String =
        if (period == WEEKLY) "每周${WEEK_NAMES[anchor.coerceIn(1, 7) - 1]}"
        else "每月 ${anchor.coerceIn(1, 28)} 日"

    /** 每周锚点选择项（周一..周日） */
    val WEEK_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

    /**
     * 在 [from, from + 45 天] 内寻找下一次锚点时刻（锚点当天 08:00）。
     * 规则新建当天锚点已过 → 返回下一周期的锚点。
     */
    fun nextAnchorAt(period: String, anchor: Int, from: Long = System.currentTimeMillis()): Long {
        val a = anchor.coerceIn(1, if (period == WEEKLY) 7 else 28)
        var probe = from
        repeat(3) {
            val win = currentWindow(period, probe)
            val t = anchorTimeInWindow(period, a, win[0])
            if (t > from) return t
            probe = win[1] + 1
        }
        return from + 7L * 24 * 3600 * 1000
    }

    /** 某周期窗口内的锚点时刻（当天 08:00），monthly 超出月末自动取月末 */
    private fun anchorTimeInWindow(period: String, anchor: Int, windowStart: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = windowStart }
        return when (period) {
            WEEKLY -> {
                cal.add(Calendar.DAY_OF_YEAR, anchor - 1)
                atEight(cal)
            }
            else -> {
                cal.set(Calendar.DAY_OF_MONTH, anchor)
                atEight(cal)
            }
        }
    }

    private fun setMidnight(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun atEight(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 8)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Calendar.DAY_OF_WEEK（周日=1）→ ISO 周几（周一=1..周日=7） */
    private fun isoDayOfWeek(cal: Calendar): Int {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }
}
