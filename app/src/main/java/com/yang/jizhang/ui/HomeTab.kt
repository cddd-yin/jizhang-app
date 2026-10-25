package com.yang.jizhang.ui

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.notify.keepalive.ListenerStatus
import java.util.Calendar

/**
 * 账本首页：结余大卡 + 月度预算仪表 + 最近交易。
 * 恒显当前自然月；跨月浏览在「明细 / 统计」进行。
 */
@Composable
fun HomeTab(
    dao: TxDao,
    contentPad: PaddingValues,
    listenerState: ListenerStatus.DisplayState,
    onEdit: (TxEntity) -> Unit,
    onGoRecords: () -> Unit,
) {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }
    val monthLabel = "${now.get(Calendar.YEAR)} 年 ${now.get(Calendar.MONTH) + 1} 月"

    // 当前自然月全量流水（结余/预算口径）与最近交易（列表口径）分开取
    val monthRange = remember { com.yang.jizhang.CategoryRules.monthRange(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1) }
    val monthTxs by dao.watchRange(monthRange[0], monthRange[1] - 1)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recent by dao.watchRecent(8).collectAsStateWithLifecycle(initialValue = emptyList())

    var budgetCents by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
        while (true) {
            budgetCents = prefs.getLong("monthly_budget_cents", 0L)
            kotlinx.coroutines.delay(2_000)
        }
    }
    var showBudgetDialog by remember { mutableStateOf(false) }

    val expenseTotal = monthTxs.sumOf { if (it.isIncome) 0L else it.amountCents }
    val incomeTotal = monthTxs.sumOf { if (it.isIncome) it.amountCents else 0L }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPad.calculateTopPadding(),
            bottom = contentPad.calculateBottomPadding() + 96.dp,
        ),
    ) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("账本", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "$monthLabel · 花完钱自动记账",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (listenerState != ListenerStatus.DisplayState.CONNECTED) {
            item(key = "banner") {
                StatusBanner(
                    state = listenerState,
                    onClick = {
                        if (listenerState == ListenerStatus.DisplayState.NEED_PERMISSION) {
                            openListenerSettings(context)
                        } else {
                            forceReconnect(context)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                )
            }
        }

        // ── 本月结余大卡 ──
        item(key = "balance") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "本月结余",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    val balance = incomeTotal - expenseTotal
                    Text(
                        fmtCents(balance),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                        color = if (balance >= 0) incomeColor() else expenseColor(),
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowStat(
                            icon = { Icon(Icons.Rounded.ArrowUpward, null, tint = incomeColor(), modifier = Modifier.size(16.dp)) },
                            iconBg = incomeColor().copy(alpha = 0.14f),
                            label = "收入",
                            amount = fmtCents(incomeTotal),
                            amountColor = incomeColor(),
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .width(0.5.dp)
                                .height(34.dp)
                                .background(MaterialTheme.colorScheme.outline),
                        )
                        FlowStat(
                            icon = { Icon(Icons.Rounded.ArrowDownward, null, tint = expenseColor(), modifier = Modifier.size(16.dp)) },
                            iconBg = expenseColor().copy(alpha = 0.14f),
                            label = "支出",
                            amount = fmtCents(expenseTotal),
                            amountColor = expenseColor(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ── 月度预算仪表 ──
        item(key = "budget") {
            BudgetCard(
                budgetCents = budgetCents,
                expenseTotal = expenseTotal,
                monthLabel = monthLabel,
                onClick = { showBudgetDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        // ── 最近交易 ──
        item(key = "recent_label") {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "最近交易",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "全部 ›",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onGoRecords).padding(4.dp),
                )
            }
        }
        if (recent.isEmpty()) {
            item(key = "recent_empty") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    EmptyState("🪙", "还没有记录", "点右下角 + 记一笔，支付后也会自动记录")
                }
            }
        } else {
            item(key = "recent_card") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column {
                        recent.forEachIndexed { i, tx ->
                            val day = remember(tx.id) { formatDay(tx.time) }
                            TxRow(
                                tx = tx,
                                showDivider = i < recent.lastIndex,
                                onClick = { onEdit(tx) },
                                subLabel = day,
                            )
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
                context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
                    .edit().putLong("monthly_budget_cents", cents).apply()
                showBudgetDialog = false
            },
            onClose = { showBudgetDialog = false },
        )
    }
}

private fun formatDay(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    return when {
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "今天 " +
            "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        else -> "%d月%d日".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) +
            " %02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}

@Composable
private fun FlowStat(
    icon: @Composable () -> Unit,
    iconBg: Color,
    label: String,
    amount: String,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).background(iconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = amountColor)
        }
    }
}

/** 月度预算卡：无预算 → 引导设置；有预算 → 半圆仪表（剩余/超支）+ 已支/预算 */
@Composable
private fun BudgetCard(
    budgetCents: Long,
    expenseTotal: Long,
    monthLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.clickable(onClick = onClick).padding(vertical = 18.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "月度预算",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(monthLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (budgetCents <= 0) {
                Spacer(Modifier.height(10.dp))
                Text("🧭", fontSize = 34.sp)
                Spacer(Modifier.height(8.dp))
                Text("设置月度预算", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "点此设定，超支自动提醒",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            } else {
                val over = expenseTotal > budgetCents
                val remaining = budgetCents - expenseTotal
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BudgetGauge(
                        usedFraction = if (budgetCents > 0) expenseTotal.toFloat() / budgetCents else 0f,
                        progressColor = if (over) expenseColor() else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (over) "已超支" else "剩余",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            fmtCents(if (over) -remaining else remaining),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (over) expenseColor() else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "已支 ${fmtCents(expenseTotal)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "预算 ${fmtCents(budgetCents)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetDialog(
    current: Long,
    onSave: (Long) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember {
        mutableStateOf(
            if (current <= 0) "" else (current / 100.0).let { if (it == Math.floor(it)) it.toLong().toString() else "%.2f".format(it) }
        )
    }
    val cents = text.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    AlertDialog(
        onDismissRequest = onClose,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("月度预算", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "设为本月支出上限，留空或 0 表示不设预算。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { s ->
                        if (s.length <= 10 && (s.isEmpty() || Regex("""^\d*(\.\d{0,2})?$""").matches(s))) text = s
                    },
                    label = { Text("预算金额（元）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = true, onClick = { onSave(cents) }) {
                Text("保存", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("取消") }
        },
    )
}
