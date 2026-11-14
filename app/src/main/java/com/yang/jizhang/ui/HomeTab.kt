package com.yang.jizhang.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.data.BudgetDao
import com.yang.jizhang.data.BudgetEntity
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.engine.keepalive.ListenerHealth
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
    listenerState: ListenerHealth.DisplayState,
    pendingCount: Int,
    onOpenReview: () -> Unit,
    onEdit: (TxEntity) -> Unit,
    onGoRecords: () -> Unit,
    onOpenBudgets: () -> Unit,
    onDelete: (TxEntity) -> Unit,
) {
    val context = LocalContext.current
    val voyager = LocalJzStyle.current == JzStyle.VOYAGER

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
        Modifier.fillMaxSize().then(if (voyager) Modifier.statusBarsPadding() else Modifier),
        contentPadding = PaddingValues(
            top = contentPad.calculateTopPadding(),
            bottom = contentPad.calculateBottomPadding() + 96.dp,
        ),
    ) {
        item(key = "header") {
            if (voyager) {
                VoyagerHomeHeader(monthLabel, incomeTotal, expenseTotal)
            } else {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    PageTitle("账本")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$monthLabel · 花完钱自动记账",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (listenerState != ListenerHealth.DisplayState.CONNECTED) {
            item(key = "banner") {
                StatusBanner(
                    state = listenerState,
                    onClick = {
                        if (listenerState == ListenerHealth.DisplayState.NEED_PERMISSION) {
                            openListenerSettings(context)
                        } else {
                            forceReconnect(context)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                )
            }
        }

        if (pendingCount > 0) {
            item(key = "pending_bills") {
                Surface(
                    onClick = onOpenReview,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("$pendingCount 条待确认账单", fontWeight = FontWeight.SemiBold)
                            Text("核对金额和收支后入账", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("查看 ›", fontSize = 13.sp)
                    }
                }
            }
        }

        // ── 本月结余大卡（v3.13.0：主题渐变强调卡，皮肤联动） ──
        if (!voyager) item(key = "balance") {
            val style = LocalJzStyle.current
            val scheme = MaterialTheme.colorScheme
            val accent = scheme.primary
            val onAccent = scheme.onPrimary
            val flat = style.usesFlatBalance
            val cardText = if (flat) scheme.onSurface else onAccent
            val shadow = if (flat) 0.dp else 8.dp
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (flat) scheme.surface else Color.Transparent,
                border = if (flat) androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(shadow, MaterialTheme.shapes.large),
            ) {
                val fill = if (flat) Modifier else Modifier.background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(accent, accent.copy(alpha = 0.9f).compositeOver(scheme.background)),
                    ),
                    MaterialTheme.shapes.large,
                )
                Column(fill.padding(if (style == JzStyle.ORIGIN_OS) 22.dp else 18.dp)) {
                    Text(
                        "本月结余",
                        fontSize = 13.sp,
                        color = cardText.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(4.dp))
                    val balance = incomeTotal - expenseTotal
                    AnimatedAmountText(
                        cents = balance,
                        fontSize = if (style == JzStyle.ORIGIN_OS) 42.sp else 36.sp,
                        color = cardText,
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(cardText.copy(alpha = 0.25f)),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowStat(
                            icon = { Icon(Icons.Rounded.ArrowUpward, null, tint = cardText, modifier = Modifier.size(16.dp)) },
                            iconBg = cardText.copy(alpha = 0.16f),
                            label = "收入",
                            amount = fmtCents(incomeTotal),
                            amountColor = cardText,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .width(0.5.dp)
                                .height(34.dp)
                                .background(cardText.copy(alpha = 0.25f)),
                        )
                        FlowStat(
                            icon = { Icon(Icons.Rounded.ArrowDownward, null, tint = cardText, modifier = Modifier.size(16.dp)) },
                            iconBg = cardText.copy(alpha = 0.16f),
                            label = "支出",
                            amount = fmtCents(expenseTotal),
                            amountColor = cardText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ── 预算区（总预算仪表 + 分类/周期预算条） ──
        if (!voyager) item(key = "budget") {
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
                    fontFamily = if (voyager) FontFamily.Serif else null,
                    fontSize = if (voyager) 20.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).then(if (voyager) Modifier.semantics { heading() } else Modifier),
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .shadow(if (voyager) 0.dp else 3.dp, MaterialTheme.shapes.large),
                ) {
                    EmptyState("还没有记录", "点右下角 + 记一笔，支付后也会自动记录")
                }
            }
        } else {
            item(key = "recent_card") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .shadow(if (voyager) 0.dp else 3.dp, MaterialTheme.shapes.large),
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
        // ── 预算区（总预算仪表 + 分类/周期预算条） ──
        if (voyager) item(key = "budget") {
            BudgetCard(
                budgets = budgets,
                budgetTxs = budgetTxs,
                monthLabel = monthLabel,
                onClick = onOpenBudgets,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
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

private val VoyagerNight = Color(0xFF17182D)
internal val VoyagerGold = Color(0xFFE5C38A)
internal val VoyagerIvory = Color(0xFFF6F0FC)

@Composable
private fun VoyagerHomeHeader(period: String, income: Long, expense: Long) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 7.dp)) {
            PageTitle("Voyager", Modifier.semantics { heading() })
            Text(period, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(11.dp))
        Surface(
            color = VoyagerNight,
            contentColor = VoyagerIvory,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, Color(0xFF6D5C97)),
        ) {
            Box(
                Modifier.fillMaxWidth().clipToBounds()
                    .background(Brush.linearGradient(listOf(Color(0xFF241C45), Color(0xFF3D2C67), Color(0xFF17182F)))),
            ) {
                VoyagerRecord(Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(horizontal = 23.dp, vertical = 21.dp)) {
                    Text("本月结余", fontSize = 13.sp, color = VoyagerGold)
                    Spacer(Modifier.height(8.dp))
                    val balance = income - expense
                    Text(
                        if (balance < 0) "-${fmtCents(balance)}" else fmtCents(balance),
                        fontSize = 44.sp, color = VoyagerIvory, fontFamily = FontFamily.Serif,
                    )
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = VoyagerGold.copy(alpha = 0.34f))
                    VoyagerCashFlow(income, expense)
                }
            }
        }
    }
}

@Composable
private fun VoyagerCashFlow(income: Long, expense: Long) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 13.dp)) {
        val scale = LocalDensity.current.fontScale
        if (maxWidth < (244 * scale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VoyagerTotal("本月收入", income)
                VoyagerTotal("本月支出", expense)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                VoyagerTotal("本月收入", income, Modifier.weight(1f))
                VoyagerTotal("本月支出", expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VoyagerTotal(label: String, cents: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = VoyagerGold)
        Spacer(Modifier.height(3.dp))
        Text(fmtCents(cents), fontFamily = FontFamily.Serif, fontSize = 20.sp, color = VoyagerIvory)
    }
}

@Composable
internal fun VoyagerRecord(modifier: Modifier) {
    Canvas(modifier) {
        val spindle = Offset(size.width * 0.94f, size.height * 0.21f)
        val radius = minOf(size.height * 0.72f, size.width * 0.38f).coerceAtLeast(1f)
        drawCircle(Color(0xFF292046).copy(alpha = 0.76f), radius, spindle)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF5A487C).copy(alpha = 0.8f), Color(0xFF372858).copy(alpha = 0.32f)), spindle, radius),
            radius, spindle,
        )
        for (groove in 0..7) {
            drawCircle(
                Color(0xFFB2A3D4).copy(alpha = if (groove % 3 == 0) 0.25f else 0.12f),
                radius * (0.98f - groove * 0.093f), spindle, style = Stroke(0.7.dp.toPx()),
            )
        }
        drawCircle(VoyagerGold.copy(alpha = 0.6f), radius * 0.21f, spindle, style = Stroke(0.8.dp.toPx()))
        val tip = radius * 0.15f
        val inset = tip * 0.18f
        val (x, y) = spindle
        val star = Path().apply {
            moveTo(x, y - tip)
            quadraticTo(x + inset, y - inset, x + tip, y)
            quadraticTo(x + inset, y + inset, x, y + tip)
            quadraticTo(x - inset, y + inset, x - tip, y)
            quadraticTo(x - inset, y - inset, x, y - tip)
            close()
        }
        drawPath(star, VoyagerGold)
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
        modifier = modifier.fillMaxWidth().shadow(3.dp, MaterialTheme.shapes.large),
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
                Spacer(Modifier.height(6.dp))
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
                    // v5.1.0 内置算法建议行：日均可用 / 期末外推 / 超支预警
                    val advice = remember(total.budget, total.spent) {
                        com.yang.jizhang.util.BudgetAdvisor.advise(total.budget, total.spent)
                    }
                    Text(
                        advice.message,
                        fontSize = 12.sp,
                        color = when (advice.pace) {
                            com.yang.jizhang.util.BudgetAdvisor.Pace.OVER,
                            com.yang.jizhang.util.BudgetAdvisor.Pace.FAST,
                            -> expenseColor()
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp),
                    )
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
