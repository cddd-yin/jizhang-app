package com.yang.jizhang.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity
import java.util.Calendar

/** 图表配色（ZCode usage-chart 天蓝色板，随深浅色切换） */
@Composable
private fun zcodeChartColors(): List<Color> =
    if (isSystemInDarkTheme()) {
        listOf(
            Color(0xFF00A6F4), // sky-500
            Color(0xFF00D5BE), // teal-400
            Color(0xFFA684FF), // violet-400
            Color(0xFFFF637E), // rose-400
            Color(0xFF7C86FF), // indigo-400
            Color(0xFF00D3F2), // cyan-400
        )
    } else {
        listOf(
            Color(0xFF0084D1), // sky-600
            Color(0xFF009689), // teal-600
            Color(0xFF7F22FE), // violet-600
            Color(0xFFFF2056), // rose-500
            Color(0xFF615FFF), // indigo-500
            Color(0xFF0092B8), // cyan-600
        )
    }

/** 统计页：本月结余 + 近 6 个月支出柱状图 + 收入分类占比 + 本月支出分类 */
@Composable
fun StatsTab(txs: List<TxEntity>, contentPad: PaddingValues, dao: TxDao, year: Int, month: Int) {
    val cardColor = iosCard()
    val cardShape = MaterialTheme.shapes.medium
    val chartColors = zcodeChartColors()

    val expense = txs.filter { !it.isIncome }
    val expenseTotal = expense.sumOf { it.amountCents }
    val incomeTotal = txs.filter { it.isIncome }.sumOf { it.amountCents }

    // 近 6 个月（含当前浏览月）边界：oldest..newest
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

    // 每月支出 → 柱状图
    val expenseByMonth = remember(sixMonthTxs, months) {
        months.map { (s, e, label) ->
            ChartEntry(
                label = label,
                value = sixMonthTxs.filter { !it.isIncome && it.time in s until e }
                    .sumOf { it.amountCents }.toDouble(),
            )
        }
    }

    // 收入分类占比（近 6 个月，类别过多时聚合为「其他」）
    val incomeByCategory = remember(sixMonthTxs) {
        sixMonthTxs.filter { it.isIncome }
            .groupBy { it.category }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.amountCents }, list.size) }
            .sortedByDescending { it.second }
    }
    val incomeTop = remember(incomeByCategory) {
        if (incomeByCategory.size <= 6) incomeByCategory
        else {
            val head = incomeByCategory.take(5)
            head + Triple(
                "其他",
                incomeByCategory.drop(5).sumOf { it.second },
                incomeByCategory.drop(5).sumOf { it.third },
            )
        }
    }
    val incomeTotal6 = incomeTop.sumOf { it.second }
    val pieData = incomeTop.mapIndexed { i, (cat, cents, _) ->
        ChartEntry(cat, cents.toDouble(), chartColors[i % chartColors.size])
    }

    // 本月支出分类
    val byCategory = remember(expense) {
        expense.groupBy { it.category }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.amountCents }, list.size) }
            .sortedByDescending { it.second }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPad.calculateTopPadding() + 4.dp,
            bottom = contentPad.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            Surface(
                color = cardColor, shape = cardShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("本月结余", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    val balance = incomeTotal - expenseTotal
                    Text(
                        (if (balance >= 0) "+" else "-") + fmtCents(Math.abs(balance)),
                        fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                        color = if (balance >= 0) incomeColor() else expenseColor(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "支出 ${fmtCents(expenseTotal)} · 收入 ${fmtCents(incomeTotal)} · 共 ${txs.size} 笔",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "chart_title") {
            Text(
                "近 6 个月支出",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
        item(key = "chart_card") {
            Surface(
                color = cardColor, shape = cardShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (expenseByMonth.all { it.value <= 0 }) {
                    Text(
                        "暂无支出记录",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    BarChart(
                        data = expenseByMonth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item(key = "income_title") {
            Text(
                "收入分类（近 6 个月）",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
        if (incomeTop.isEmpty()) {
            item {
                Surface(
                    color = cardColor, shape = cardShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "暂无收入记录",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            item(key = "income_card") {
                Surface(
                    color = cardColor, shape = cardShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PieChart(
                            data = pieData,
                            modifier = Modifier.size(136.dp),
                        )
                        Spacer(Modifier.width(18.dp))
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
                                            .background(chartColors[i % chartColors.size]),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(cat, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                    Text(fmtCents(cents), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (incomeTotal6 > 0) "%.0f%%".format(cents.toFloat() / incomeTotal6 * 100)
                                        else "0%",
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

        item(key = "cat_title") {
            Text(
                "支出分类（本月）",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
        if (byCategory.isEmpty()) {
            item {
                Surface(
                    color = cardColor, shape = cardShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "暂无支出记录",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (byCategory.isNotEmpty()) {
            item(key = "cat_card") {
                Surface(
                    color = cardColor, shape = cardShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        byCategory.forEachIndexed { index, (cat, cents, count) ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .size(34.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(CategoryRules.icon(cat), fontSize = 17.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(cat, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                    Text(
                                        fmtCents(cents),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "$count 笔",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { if (expenseTotal > 0) cents.toFloat() / expenseTotal else 0f },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            if (index < byCategory.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 60.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
