package com.yang.jizhang.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxEntity

/** 统计页：iOS 风格半透明卡片 + 分类占比 */
@Composable
fun StatsTab(txs: List<TxEntity>, contentPad: PaddingValues) {
    val dark = isSystemInDarkTheme()
    val cardColor = (if (dark) Color(0xFF1C1C1E) else Color.White).copy(alpha = 0.72f)
    val cardShape = RoundedCornerShape(22.dp)
    val cardBorder = BorderStroke(0.5.dp, Color.White.copy(alpha = if (dark) 0.08f else 0.7f))

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
            top = contentPad.calculateTopPadding() + 6.dp,
            bottom = contentPad.calculateBottomPadding() + 92.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            Surface(color = cardColor, shape = cardShape, border = cardBorder, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("本月结余", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (if (incomeTotal - expenseTotal >= 0) "+" else "-") + fmtCents(Math.abs(incomeTotal - expenseTotal)),
                        fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "支出 ${fmtCents(expenseTotal)} · 收入 ${fmtCents(incomeTotal)} · 共 ${txs.size} 笔",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "cat_title") {
            Text(
                "支出分类",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
        }
        if (byCategory.isEmpty()) {
            item {
                Surface(color = cardColor, shape = cardShape, border = cardBorder, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "暂无支出记录",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        byCategory.forEach { (cat, cents, count) ->
            item(key = cat) {
                Surface(color = cardColor, shape = cardShape, border = cardBorder, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(CategoryRules.icon(cat), fontSize = 16.sp)
                            Text("  $cat", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("${fmtCents(cents)} · $count 笔", fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (expenseTotal > 0) cents.toFloat() / expenseTotal else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
