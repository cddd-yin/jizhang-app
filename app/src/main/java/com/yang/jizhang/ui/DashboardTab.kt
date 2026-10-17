package com.yang.jizhang.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.data.TxEntity

/**
 * 首页 Dashboard（高级感改版）：
 * 本月结余大卡 + 收支双卡 + 月度预算进度环 + 最近交易。
 * 布局与金额样式借鉴 wallee（Compose-Expense）BalanceSummary，预算环抄 Expenso。
 */
@Composable
fun DashboardTab(
    txs: List<TxEntity>,
    recent: List<TxEntity>,
    year: Int, month: Int,
    contentPad: PaddingValues,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEdit: (TxEntity) -> Unit,
    onGoRecords: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE) }
    var budgetCents by remember { mutableStateOf(prefs.getLong("monthly_budget_cents", 0L)) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    val expenseTotal = txs.filter { !it.isIncome }.sumOf { it.amountCents }
    val incomeTotal = txs.filter { it.isIncome }.sumOf { it.amountCents }
    val balance = incomeTotal - expenseTotal
    val overBudget = budgetCents > 0 && expenseTotal > budgetCents
    val ringColor = if (overBudget) expenseColor() else MaterialTheme.colorScheme.primary

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPad.calculateTopPadding() + 4.dp,
            bottom = contentPad.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PgHeadline1("账本", modifier = Modifier.weight(1f))
                Text(
                    "$year 年 $month 月",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp),
                )
                IosChevron("‹", onPrev)
                Spacer(Modifier.width(4.dp))
                IosChevron("›", onNext)
            }
        }

        item(key = "balance") {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    PgContentTitle("本月结余", color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(6.dp))
                    val negative = balance < 0
                    PgAmountLabel(
                        amount = fmtMoney(balance),
                        symbol = if (negative) "-¥" else "¥",
                        amountFontSize = 44.sp,
                        symbolFontSize = 24.sp,
                        color = Color.White,
                    )
                }
            }
        }

        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("本月支出", expenseTotal, expenseColor(), Modifier.weight(1f))
                StatCard("本月收入", incomeTotal, incomeColor(), Modifier.weight(1f))
            }
        }

        item(key = "budget") {
            BudgetCard(
                budgetCents = budgetCents,
                expenseTotal = expenseTotal,
                onClick = { showBudgetDialog = true },
            )
        }

        item(key = "recent_head") {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PgHeadline2("最近交易", modifier = Modifier.weight(1f))
                Text(
                    "查看全部 ›",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onGoRecords() },
                )
            }
        }

        val showList = if (recent.isNotEmpty()) recent else txs
        if (showList.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🪙", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("还没有账单", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "完成一笔支付会自动记入；也可去明细页点 + 记一笔",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item(key = "recent_card") {
                Surface(
                    color = iosCard(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        showList.take(6).forEachIndexed { index, tx ->
                            TxRow(tx, showDivider = index < showList.take(6).lastIndex) { onEdit(tx) }
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            current = budgetCents,
            onSave = { cents ->
                budgetCents = cents
                prefs.edit().putLong("monthly_budget_cents", cents).apply()
            },
            onClose = { showBudgetDialog = false },
        )
    }
}

/** 支出 / 收入统计小卡 */
@Composable
private fun StatCard(label: String, cents: Long, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = iosCard(),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            PgDateLabel(label)
            Spacer(Modifier.height(4.dp))
            Text(
                fmtCents(cents),
                color = valueColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 月度预算卡：无预算 → 引导设置；有预算 → 进度环 + 已用/剩余 */
@Composable
private fun BudgetCard(budgetCents: Long, expenseTotal: Long, onClick: () -> Unit) {
    Surface(
        color = iosCard(),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        if (budgetCents <= 0) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                PgContentTitle("设置月度预算", modifier = Modifier.weight(1f))
                Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val over = expenseTotal > budgetCents
            val progress = if (budgetCents > 0) expenseTotal.toFloat() / budgetCents else 0f
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressRing(
                    progress = progress,
                    color = if (over) expenseColor() else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(84.dp),
                    strokeWidth = 13f,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    PgHeadline2("月度预算")
                    Spacer(Modifier.height(4.dp))
                    PgContentTitle(
                        "已用 ${fmtCents(expenseTotal)} / ${fmtCents(budgetCents)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (over) {
                        Spacer(Modifier.height(2.dp))
                        PgContentTitle(
                            "已超支 ${fmtCents(expenseTotal - budgetCents)}",
                            color = expenseColor(),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 预算设置弹窗（输入 0 即清除预算） */
@Composable
private fun BudgetDialog(current: Long, onSave: (Long) -> Unit, onClose: () -> Unit) {
    var text by remember {
        mutableStateOf(if (current > 0) "%.2f".format(current / 100.0) else "")
    }
    val cents = text.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(16.dp),
        containerColor = iosCard(),
        title = {
            Text(
                "月度预算",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { s ->
                        if (s.length <= 10 && (s.isEmpty() || Regex("""^\d*(\.\d{0,2})?$""").matches(s))) {
                            text = s
                        }
                    },
                    label = { Text("每月预算（元）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "输入 0 可清除预算；超支时首页进度环会变红提示",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = cents >= 0, onClick = { onSave(cents) }) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("取消") }
        },
    )
}
