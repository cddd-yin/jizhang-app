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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.data.BudgetDao
import com.yang.jizhang.data.BudgetEntity
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.notify.keepalive.ListenerStatus
import com.yang.jizhang.util.Periods
import java.util.Calendar

/**
 * 账本首页：结余大卡 + 预算区（总预算仪表 + 分类/周期预算条）+ 最近交易。
 * 恒显当前自然月；跨月浏览在「明细 / 统计」进行。
 */
@Composable
fun HomeTab(
    dao: TxDao,
    budgetDao: BudgetDao,
    contentPad: PaddingValues,
    listenerState: ListenerStatus.DisplayState,
    onEdit: (TxEntity) -> Unit,
    onGoRecords: () -> Unit,
    onOpenBudgets: () -> Unit,
    onDelete: (TxEntity) -> Unit,
) {
    val context = LocalContext.current

    // 「本月」随日历翻转（v3.6.0 修复：跨月零点后结余/预算仍停在旧月的粘滞问题）
    var monthStamp by remember { mutableIntStateOf(currentMonthStamp()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            val s = currentMonthStamp()
            if (s != monthStamp) monthStamp = s
        }
    }
    val yearNow = monthStamp / 100
    val monthNow = monthStamp % 100
    val monthLabel = "$yearNow 年 $monthNow 月"

    // 当前自然月全量流水（结余口径）与最近交易（列表口径）分开取
    val monthRange = remember(monthStamp) { com.yang.jizhang.CategoryRules.monthRange(yearNow, monthNow) }
    val monthTxs by dao.watchRange(monthRange[0], monthRange[1] - 1)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recent by dao.watchRecent(8).collectAsStateWithLifecycle(initialValue = emptyList())

    // 预算（v3.1）：启用中的预算列表 + 最早窗口起的流水（各预算按自己的当前窗口求已用）
    val budgets by budgetDao.watchEnabled().collectAsStateWithLifecycle(initialValue = emptyList())
    val earliestWinStart = remember(budgets) {
        budgets.minOfOrNull { Periods.currentWindow(it.period)[0] } ?: monthRange[0]
    }
    val budgetTxs by dao.watchSince(earliestWinStart).collectAsStateWithLifecycle(initialValue = emptyList())

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
                    AnimatedAmountText(
                        cents = balance,
                        fontSize = 34.sp,
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

        // ── 预算区（总预算仪表 + 分类/周期预算条） ──
        item(key = "budget") {
            BudgetCard(
                budgets = budgets,
                budgetTxs = budgetTxs,
                monthLabel = monthLabel,
                onClick = onOpenBudgets,
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
                                onDelete = { onDelete(tx) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun currentMonthStamp(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
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

/** 预算卡（v3.1）：总预算半圆仪表 + 分类/周期预算进度条，无预算引导设置 */
@Composable
private fun BudgetCard(
    budgets: List<BudgetEntity>,
    budgetTxs: List<TxEntity>,
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
                    "预算",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(monthLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            class Progress(val budget: BudgetEntity, val spent: Long)
            val progresses = remember(budgets, budgetTxs) {
                budgets.map { b ->
                    val win = Periods.currentWindow(b.period)
                    val spent = budgetTxs
                        .filter { !it.isIncome && it.time in win[0] until win[1] }
                        .filter { b.category.isEmpty() || it.category == b.category }
                        .sumOf { it.amountCents }
                    Progress(b, spent)
                }
            }
            val total = progresses.firstOrNull { it.budget.category.isEmpty() }
            val cats = progresses.filter { it.budget.category.isNotEmpty() }

            if (progresses.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("🧭", fontSize = 34.sp)
                Spacer(Modifier.height(8.dp))
                Text("设置预算", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "总预算或分类预算，每月 / 每周自动重置",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            } else {
                // 总预算仪表（任意周期）
                if (total != null) {
                    val over = total.spent > total.budget.amountCents
                    val remaining = total.budget.amountCents - total.spent
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BudgetGauge(
                            usedFraction = total.spent.toFloat() / total.budget.amountCents,
                            progressColor = if (over) expenseColor() else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "总预算 · " + Periods.label(total.budget.period) + (if (over) " · 已超支" else ""),
                                fontSize = 12.sp,
                                color = if (over) expenseColor() else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            "已支 ${fmtCents(total.spent)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "预算 ${fmtCents(total.budget.amountCents)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 分类/周期预算条
                if (cats.isNotEmpty()) {
                    Spacer(Modifier.height(if (total != null) 10.dp else 4.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        cats.forEach { p ->
                            val over = p.spent > p.budget.amountCents
                            val frac = if (p.budget.amountCents > 0) {
                                (p.spent.toFloat() / p.budget.amountCents).coerceIn(0f, 1f)
                            } else 0f
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryCircle(p.budget.category, size = 26.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            p.budget.category,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${Periods.label(p.budget.period)} · 已用 ${fmtCents(p.spent)} / ${fmtCents(p.budget.amountCents)}",
                                            fontSize = 11.sp,
                                            color = if (over) expenseColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier.fillMaxWidth().height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth(frac).height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (over) expenseColor() else MaterialTheme.colorScheme.primary),
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "管理预算 ›",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
