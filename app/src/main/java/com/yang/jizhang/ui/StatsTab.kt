package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity
import java.util.Calendar

/**
 * 统计页：月度浏览 + 本月结余总览 + 近 6 个月支出柱状图 + 收入分类占比环 + 本月支出分类。
 * 计算口径与 v2.x 相同，展示全部换新。
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
    val expense = txs.filter { !it.isIncome }
    val expenseTotal = expense.sumOf { it.amountCents }
    val incomeTotal = txs.filter { it.isIncome }.sumOf { it.amountCents }

    // 近 6 个月（含浏览月）边界
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

    // 柱色：当前月满色、其余降透明（色在组合期取好，remember 内不再读主题）
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

    // 收入分类占比（近 6 个月，超 6 类聚合「其他」）
    val incomeByCategory = remember(sixMonthTxs) {
        sixMonthTxs.filter { it.isIncome }
            .groupBy { it.category }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.amountCents }, list.size) }
            .sortedByDescending { it.second }
    }
    val incomeTop = remember(incomeByCategory) {
        if (incomeByCategory.size <= 6) incomeByCategory
        else incomeByCategory.take(5) + Triple(
            "其他",
            incomeByCategory.drop(5).sumOf { it.second },
            incomeByCategory.drop(5).sumOf { it.third },
        )
    }
    val incomeTotal6 = incomeTop.sumOf { it.second }
    val pieData = incomeTop.mapIndexed { i, (cat, cents, _) ->
        ChartEntry(cat, cents.toDouble(), JzChartColors[i % JzChartColors.size])
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
            top = 0.dp,
            bottom = contentPad.calculateBottomPadding() + 96.dp,
        ),
    ) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("统计", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                Spacer(Modifier.height(12.dp))
                MonthSwitcher(
                    title = "$year 年 $month 月",
                    onPrev = { if (month == 1) onMonthChange(year - 1, 12) else onMonthChange(year, month - 1) },
                    onNext = { if (month == 12) onMonthChange(year + 1, 1) else onMonthChange(year, month + 1) },
                )
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
                    val balance = incomeTotal - expenseTotal
                    Text(
                        "结余",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (balance >= 0) "+" else "-") + fmtMoney(Math.abs(balance)),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                        color = if (balance >= 0) incomeColor() else expenseColor(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "支出 ${fmtCents(expenseTotal)} · 收入 ${fmtCents(incomeTotal)} · 共 ${txs.size} 笔",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── 近 6 个月支出 ──
        item(key = "bar_label") { SectionLabel("近 6 个月支出", Modifier.padding(top = 8.dp)) }
        item(key = "bar_card") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (expenseByMonth.all { it.value <= 0.0 }) {
                    Text(
                        "暂无支出记录",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    BarChart(
                        data = expenseByMonth,
                        modifier = Modifier.fillMaxWidth().height(170.dp).padding(10.dp),
                    )
                }
            }
        }

        // ── 收入分类占比 ──
        item(key = "pie_label") { SectionLabel("收入分类（近 6 个月）") }
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
                                        if (incomeTotal6 > 0) "%.0f%%".format(cents.toFloat() / incomeTotal6 * 100) else "0%",
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
        item(key = "cat_label") { SectionLabel("支出分类（本月）") }
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
                                                    if (expenseTotal > 0) (cents.toFloat() / expenseTotal).coerceIn(0.02f, 1f) else 0f,
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
