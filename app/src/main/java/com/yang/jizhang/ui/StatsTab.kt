package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity
import java.util.Calendar

/**
 * 统计页（v3.2 起支持月 / 年双模式）：
 * 月模式：本月结余总览 + 近 6 个月支出柱状图 + 收入分类占比环 + 本月支出分类条。
 * 年模式：年度结余总览 + 近 6 年支出柱状图 + 年度收入分类占比环 + 年度支出分类条。
 */
@Composable
fun StatsTab(
    txs: List<TxEntity>,
    contentPad: PaddingValues,
    dao: TxDao,
    year: Int,
    month: Int,
    onMonthChange: (Int, Int) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("month") }
    val isYear = mode == "year"

    // ── 月模式数据 ──
    val expense = txs.filter { !it.isIncome }
    val expenseTotal = expense.sumOf { it.amountCents }
    val incomeTotal = txs.filter { it.isIncome }.sumOf { it.amountCents }

    val months = remember(year, month) {
        val cal = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -5)
        }
        (0 until 6).map {
            val start = cal.timeInMillis
            val label = "${cal.get(Calendar.MONTH) + 1}月"
            cal.add(Calendar.MONTH, 1)
            Triple(start, cal.timeInMillis, label)
        }
    }
    val sixMonthTxs by dao.watchSince(months.first().first)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val barCurrent = accentColor()
    val barOther = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    val expenseByMonth = remember(sixMonthTxs, months, barCurrent, barOther) {
        months.mapIndexed { i, (s, e, label) ->
            ChartEntry(
                label = label,
                value = sixMonthTxs.filter { !it.isIncome && it.time in s until e }
                    .sumOf { it.amountCents }.toDouble(),
                color = if (i == 5) barCurrent else barOther,
            )
        }
    }

    val incomeByCategoryMonth = remember(sixMonthTxs) {
        sixMonthTxs.filter { it.isIncome }
            .groupBy { it.category }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.amountCents }, list.size) }
            .sortedByDescending { it.second }
    }
    val incomeTopMonth = remember(incomeByCategoryMonth) { aggregateTop(incomeByCategoryMonth) }

    val byCategoryMonth = remember(expense) {
        expense.groupBy { it.category }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.amountCents }, list.size) }
            .sortedByDescending { it.second }
    }

    // ── 年模式数据 ──
    val yearRange = remember(year) {
        val cal = Calendar.getInstance().apply {
            set(year, 0, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        longArrayOf(start, cal.timeInMillis)
    }
    val yearTxs by dao.watchRange(yearRange[0], yearRange[1] - 1)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val yearExpenseTotal = yearTxs.filter { !it.isIncome }.sumOf { it.amountCents }
    val yearIncomeTotal = yearTxs.filter { it.isIncome }.sumOf { it.amountCents }

    val years = remember(year) {
        val cal = Calendar.getInstance().apply {
            set(year - 5, 0, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        (0 until 6).map {
            val start = cal.timeInMillis
            val label = "${cal.get(Calendar.YEAR)}"
            cal.add(Calendar.YEAR, 1)
            Triple(start, cal.timeInMillis, label)
        }
    }
    val sixYearTxs by dao.watchSince(years.first().first)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val expenseByYear = remember(sixYearTxs, years, barCurrent, barOther) {
        years.mapIndexed { i, (s, e, label) ->
            ChartEntry(
                label = label,
                value = sixYearTxs.filter { !it.isIncome && it.time in s until e }
                    .sumOf { it.amountCents }.toDouble(),
                color = if (i == 5) barCurrent else barOther,
            )
        }
    }
    val incomeByCategoryYear = remember(yearTxs) {
        yearTxs.filter { it.isIncome }
            .groupBy { it.category }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.amountCents }, list.size) }
            .sortedByDescending { it.second }
    }
    val incomeTopYear = remember(incomeByCategoryYear) { aggregateTop(incomeByCategoryYear) }
    val byCategoryYear = remember(yearTxs) {
        yearTxs.filter { !it.isIncome }
            .groupBy { it.category }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.amountCents }, list.size) }
            .sortedByDescending { it.second }
    }

    // ── 汇总当前口径 ──
    val viewExpense = if (isYear) yearExpenseTotal else expenseTotal
    val viewIncome = if (isYear) yearIncomeTotal else incomeTotal
    val viewTxs = if (isYear) yearTxs else txs

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 0.dp,
            bottom = contentPad.calculateBottomPadding() + 96.dp,
        ),
    ) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("统计", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonthSwitcher(
                        title = if (isYear) "$year 年" else "$year 年 $month 月",
                        onPrev = {
                            if (isYear) onMonthChange(year - 1, month)
                            else if (month == 1) onMonthChange(year - 1, 12) else onMonthChange(year, month - 1)
                        },
                        onNext = {
                            if (isYear) onMonthChange(year + 1, month)
                            else if (month == 12) onMonthChange(year + 1, 1) else onMonthChange(year, month + 1)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    ModeToggle(isYear) { mode = if (it) "year" else "month" }
                }
            }
        }

        // ── 结余总览 ──
        item(key = "summary") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    val balance = viewIncome - viewExpense
                    Text(
                        if (isYear) "年度结余" else "结余",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    AnimatedAmountText(
                        cents = balance,
                        fontSize = 30.sp,
                        color = if (balance >= 0) incomeColor() else expenseColor(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "支出 ${fmtCents(viewExpense)} · 收入 ${fmtCents(viewIncome)} · 共 ${viewTxs.size} 笔",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── 支出趋势柱状 ──
        item(key = "bar_label") {
            SectionLabel(if (isYear) "近 6 年支出" else "近 6 个月支出", Modifier.padding(top = 8.dp))
        }
        item(key = "bar_card") {
            val bars = if (isYear) expenseByYear else expenseByMonth
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (bars.all { it.value <= 0.0 }) {
                    Text(
                        "暂无支出记录",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    BarChart(
                        data = bars,
                        modifier = Modifier.fillMaxWidth().height(170.dp).padding(10.dp),
                    )
                }
            }
        }

        // ── 消费日历热力图（v3.10.0，仅月模式） ──
        if (!isYear) {
            item(key = "calendar_label") { SectionLabel("消费日历（本月）") }
            item(key = "calendar_card") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        ExpenseCalendar(year = year, month1 = month, txs = txs)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "浅 → 深 = 当日支出由少到多",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "今日加边框",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        // ── 月度对比（v3.10.0，仅月模式：本月 vs 上月分类涨跌） ──
        if (!isYear) {
            item(key = "compare_label") { SectionLabel("对比上月") }
            item(key = "compare_card") {
                val lastMonthRange = remember(year, month) {
                    val cal = Calendar.getInstance().apply {
                        set(year, month - 1, 1, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.MONTH, -1)
                    }
                    val s = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    s to cal.timeInMillis
                }
                val lastMonthTxs = remember(sixMonthTxs, lastMonthRange) {
                    sixMonthTxs.filter { it.time in lastMonthRange.first until lastMonthRange.second }
                }
                val thisExpense = remember(expense) {
                    expense.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
                }
                val lastExpense = remember(lastMonthTxs) {
                    lastMonthTxs.filter { !it.isIncome }
                        .groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val thisTotal = thisExpense.values.sum()
                        val lastTotal = lastExpense.values.sum()
                        val delta = thisTotal - lastTotal
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("本月支出 ${fmtCents(thisTotal)}", fontSize = 13.sp)
                                Text("上月支出 ${fmtCents(lastTotal)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (lastTotal == 0L) "上月无记录"
                                else (if (delta >= 0) "▲ +" else "▼ -") + fmtCents(Math.abs(delta)) +
                                    "（%+.0f%%".format(if (lastTotal > 0) delta.toFloat() / lastTotal * 100 else 0f) + "）",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (delta > 0) expenseColor() else incomeColor(),
                            )
                        }
                        // 分类涨跌（按差额绝对值排前 6）
                        val cats = (thisExpense.keys + lastExpense.keys).distinct()
                        val rows = cats.map { c ->
                            Triple(c, thisExpense[c] ?: 0L, lastExpense[c] ?: 0L)
                        }.filter { it.second != it.third }
                            .sortedByDescending { Math.abs(it.second - it.third) }
                            .take(6)
                        if (rows.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outline))
                            Spacer(Modifier.height(6.dp))
                            rows.forEach { (cat, now, before) ->
                                val diff = now - before
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CategoryCircle(cat, size = 28.dp)
                                    Spacer(Modifier.width(9.dp))
                                    Text(cat, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text(
                                        "${fmtCents(before)} → ${fmtCents(now)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        (if (diff >= 0) "+" else "-") + fmtMoney(Math.abs(diff)),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (diff > 0) expenseColor() else incomeColor(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 收入分类占比 ──
        item(key = "pie_label") {
            SectionLabel(if (isYear) "收入分类（本年）" else "收入分类（近 6 个月）")
        }
        val incomeTop = if (isYear) incomeTopYear else incomeTopMonth
        if (incomeTop.isEmpty()) {
            item(key = "pie_empty") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "暂无收入记录",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            item(key = "pie_card") {
                val incomeTotalView = incomeTop.sumOf { it.second }
                val pieData = incomeTop.mapIndexed { i, (cat, cents, _) ->
                    ChartEntry(cat, cents.toDouble(), JzChartColors[i % JzChartColors.size])
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                            PieChart(data = pieData, modifier = Modifier.size(130.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            incomeTop.forEachIndexed { i, (cat, cents, _) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(JzChartColors[i % JzChartColors.size]),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(cat, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                    Text(fmtCents(cents), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (incomeTotalView > 0) "%.0f%%".format(cents.toFloat() / incomeTotalView * 100) else "0%",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 支出分类 ──
        item(key = "cat_label") {
            SectionLabel(if (isYear) "支出分类（本年）" else "支出分类（本月）")
        }
        val byCategory = if (isYear) byCategoryYear else byCategoryMonth
        val catTotal = if (isYear) yearExpenseTotal else expenseTotal
        if (byCategory.isEmpty()) {
            item(key = "cat_empty") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "暂无支出记录",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            item(key = "cat_card") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        byCategory.forEachIndexed { index, (cat, cents, count) ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryCircle(cat, size = 36.dp)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(cat, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text(fmtCents(cents), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "$count 笔",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(
                                                    if (catTotal > 0) (cents.toFloat() / catTotal).coerceIn(0.02f, 1f) else 0f,
                                                )
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(categoryHue(cat)),
                                        )
                                    }
                                }
                            }
                            if (index < byCategory.lastIndex) {
                                Box(
                                    Modifier
                                        .padding(start = 63.dp)
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(MaterialTheme.colorScheme.outline),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 超 6 类时聚合为「其他」 */
private fun aggregateTop(
    list: List<Triple<String, Long, Int>>,
): List<Triple<String, Long, Int>> =
    if (list.size <= 6) list
    else list.take(5) + Triple(
        "其他",
        list.drop(5).sumOf { it.second },
        list.drop(5).sumOf { it.third },
    )

/** 月 / 年模式切换（胶囊分段） */
@Composable
private fun ModeToggle(yearSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Row(Modifier.padding(4.dp)) {
            listOf(false to "月", true to "年").forEach { (isYear, label) ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (yearSelected == isYear) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { onSelect(isYear) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (yearSelected == isYear) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
