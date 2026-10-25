package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.data.TxEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 明细页：月份切换 + 搜索（备注/分类/来源）+ 分类筛选 + 按日分组账单。
 * 日分组头带当日支/收小计并吸顶，组内为彩色圆徽交易行。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecordsTab(
    txs: List<TxEntity>,
    contentPad: PaddingValues,
    year: Int,
    month: Int,
    onMonthChange: (Int, Int) -> Unit,
    onItemClick: (TxEntity) -> Unit,
    onDelete: (TxEntity) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    val filtered = remember(txs, searchQuery, categoryFilter) {
        val q = searchQuery.trim()
        txs.filter { tx ->
            val matchQuery = q.isEmpty() ||
                tx.note.contains(q, ignoreCase = true) ||
                tx.category.contains(q, ignoreCase = true) ||
                tx.source.contains(q, ignoreCase = true)
            val matchCategory = categoryFilter.isEmpty() || tx.category == categoryFilter
            matchQuery && matchCategory
        }
    }
    val expenseTotal = filtered.sumOf { if (it.isIncome) 0L else it.amountCents }
    val incomeTotal = filtered.sumOf { if (it.isIncome) it.amountCents else 0L }

    Column(
        // 键盘弹出时列表收缩到可见区（v3.5.0）
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
    ) {
        // 顶部：大标题 + 月份切换 + 搜索
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("账单", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthSwitcher(
                    title = "$year 年 $month 月",
                    onPrev = { if (month == 1) onMonthChange(year - 1, 12) else onMonthChange(year, month - 1) },
                    onNext = { if (month == 12) onMonthChange(year + 1, 1) else onMonthChange(year, month + 1) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "支 ${fmtCents(expenseTotal)} · 收 ${fmtCents(incomeTotal)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            SearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // 分类筛选（v3.2）：横向 chips，与搜索叠加过滤
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip("全部", categoryFilter.isEmpty()) { categoryFilter = "" }
                }
                items(com.yang.jizhang.CategoryRules.ALL_CATEGORIES.size) { i ->
                    val cat = com.yang.jizhang.CategoryRules.ALL_CATEGORIES[i]
                    FilterChip(cat, categoryFilter == cat) { categoryFilter = if (categoryFilter == cat) "" else cat }
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        // 分组列表
        val groups = remember(filtered) { groupByDay(filtered) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 4.dp,
                bottom = contentPad.calculateBottomPadding() + 96.dp,
            ),
        ) {
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        if (searchQuery.isBlank() && categoryFilter.isBlank()) {
                            EmptyState("本月暂无记录", "点右下角 + 记一笔，支付后也会自动记录")
                        } else {
                            EmptyState("没有匹配的记录", "试试清空搜索或分类筛选")
                        }
                    }
                }
            }
            groups.forEach { g ->
                // 日期头吸顶（v3.13.0）：滚动时贴在列表顶，长列表随时知道在哪天
                stickyHeader(key = "head_${g.key}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            g.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (g.expense > 0) {
                            Text("支 ${fmtCents(g.expense)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (g.income > 0) {
                            Spacer(Modifier.width(10.dp))
                            Text("收 ${fmtCents(g.income)}", fontSize = 12.sp, color = incomeColor())
                        }
                    }
                }
                item(key = "card_${g.key}") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, MaterialTheme.shapes.large)
                            .animateItem(),
                    ) {
                        Column {
                            g.items.forEachIndexed { i, tx ->
                                TxRow(
                                    tx = tx,
                                    showDivider = i < g.items.lastIndex,
                                    onClick = { onItemClick(tx) },
                                    onDelete = { onDelete(tx) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DayGroup(val key: Int, val label: String, val items: List<TxEntity>, val expense: Long, val income: Long)

private fun groupByDay(txs: List<TxEntity>): List<DayGroup> {
    val dayFormat = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)
    return txs
        .groupBy { dayKey(it.time) }
        .map { (key, list) ->
            DayGroup(
                key = key,
                label = dayFormat.format(list.first().time),
                items = list,
                expense = list.sumOf { if (it.isIncome) 0L else it.amountCents },
                income = list.sumOf { if (it.isIncome) it.amountCents else 0L },
            )
        }
        .sortedByDescending { it.items.first().time }
}

private fun dayKey(millis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return c.get(Calendar.YEAR) * 10000 + c.get(Calendar.MONTH) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 15.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "搜索备注 / 分类 / 来源",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
