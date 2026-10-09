package com.yang.jizhang.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxEntity

/** 统计页：本月分类支出占比 + 收支概览 */
@Composable
fun StatsTab(txs: List<TxEntity>) {
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
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("本月结余", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (if (incomeTotal - expenseTotal >= 0) "+" else "-") + fmtCents(Math.abs(incomeTotal - expenseTotal)),
                        fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支出 ${fmtCents(expenseTotal)} · 收入 ${fmtCents(incomeTotal)} · 共 ${txs.size} 笔",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("支出分类", fontWeight = FontWeight.SemiBold)
            if (byCategory.isEmpty()) {
                Text("暂无支出记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        byCategory.forEach { (cat, cents, count) ->
            item(key = cat) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(CategoryRules.icon(cat), fontSize = 16.sp)
                        Text("  $cat", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${fmtCents(cents)} · $count 笔", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (expenseTotal > 0) cents.toFloat() / expenseTotal else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }
            }
        }
    }
}
