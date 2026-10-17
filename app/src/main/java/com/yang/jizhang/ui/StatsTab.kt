package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxEntity

/** 统计页：iOS 分组卡片 + 分类占比 */
@Composable
fun StatsTab(txs: List<TxEntity>, contentPad: PaddingValues) {
    val cardColor = MaterialTheme.colorScheme.surface
    val cardShape = RoundedCornerShape(16.dp)

    val expense = txs.filter { !it.isIncome }
    val expenseTotal = expense.sumOf { it.amountCents }
    val incomeTotal = txs.filter { it.isIncome }.sumOf { it.amountCents }

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
            Surface(color = cardColor, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("本月结余", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        (if (incomeTotal - expenseTotal >= 0) "+" else "-") + fmtCents(Math.abs(incomeTotal - expenseTotal)),
                        fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "支出 ${fmtCents(expenseTotal)} · 收入 ${fmtCents(incomeTotal)} · 共 ${txs.size} 笔",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "cat_title") {
            Text(
                "支出分类",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
        if (byCategory.isEmpty()) {
            item {
                Surface(color = cardColor, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
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
                Surface(color = cardColor, shape = cardShape, modifier = Modifier.fillMaxWidth()) {
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
