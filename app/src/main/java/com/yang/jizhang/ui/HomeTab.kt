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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import java.time.YearMonth
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
    screenTracking: Boolean,
    pendingCount: Int,
    onOpenReview: () -> Unit,
    onEdit: (TxEntity) -> Unit,
    onGoRecords: () -> Unit,
    onOpenIssue: (Int, Int) -> Unit,
    onOpenBudgets: () -> Unit,
    onArchive: (TxEntity) -> Unit,
) {
    val context = LocalContext.current
    val language = LocalVoyagerLanguage.current
    val voyager = LocalJzStyle.current == JzStyle.VOYAGER

    // 「本月」随日历翻转（v3.6.0 修复：跨月零点后结余/预算仍停在旧月的粘滞问题）
    var dayStamp by remember { mutableIntStateOf(currentDayStamp()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            dayStamp = currentDayStamp()
        }
    }
    val monthStamp = dayStamp / 100
    val yearNow = monthStamp / 100
    val monthNow = monthStamp % 100
    val monthLabel = language.month(yearNow, monthNow)

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
    val dailyExpenses = remember(voyager, monthStamp, monthTxs) {
        if (!voyager) emptyList() else {
            val days = MutableList(YearMonth.of(yearNow, monthNow).lengthOfMonth()) { 0L }
            val calendar = Calendar.getInstance()
            for (tx in monthTxs) {
                if (tx.isIncome) continue
                calendar.timeInMillis = tx.time
                if (calendar.get(Calendar.YEAR) == yearNow && calendar.get(Calendar.MONTH) + 1 == monthNow) {
                    val index = calendar.get(Calendar.DAY_OF_MONTH) - 1
                    days[index] += tx.amountCents
                }
            }
            days
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().then(if (voyager) Modifier.statusBarsPadding() else Modifier),
        contentPadding = PaddingValues(
            top = contentPad.calculateTopPadding(),
            bottom = contentPad.calculateBottomPadding() + 96.dp,
        ),
    ) {
        item(key = "header") {
            if (voyager) {
                VoyagerHomeHeader(monthLabel, incomeTotal, expenseTotal, dailyExpenses, dayStamp % 100) {
                    onOpenIssue(yearNow, monthNow)
                }
            } else {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    PageTitle(language("账本", "Ledger"))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        language("$monthLabel · 花完钱自动记账", "$monthLabel · Automatic tracking"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!screenTracking && listenerState != ListenerHealth.DisplayState.CONNECTED) {
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
                            Text(language("$pendingCount 条待确认账单", "Transactions to review: $pendingCount"), fontWeight = FontWeight.SemiBold)
                            Text(language("核对金额和收支后入账", "Review the amount and transaction type before saving"), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(language("查看 ›", "Review ›"), fontSize = 13.sp)
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
                        language("本月结余", "Monthly balance"),
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
                            label = language("收入", "Income"),
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
                            label = language("支出", "Expenses"),
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
                    language("最近交易", "Recent transactions"),
                    fontFamily = if (voyager) MaterialTheme.typography.titleLarge.fontFamily else null,
                    fontSize = if (voyager) 20.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).then(if (voyager) Modifier.semantics { heading() } else Modifier),
                )
                Text(
                    language("全部 ›", "See all ›"),
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
                    EmptyState(
                        language("还没有记录", "No transactions yet"),
                        language("点右下角 + 记一笔，支付后也会自动记录", "Tap + to add a transaction. Payments may also be recorded automatically."),
                    )
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
                            val day = remember(tx.id, language) { formatDay(tx.time, language) }
                            TxRow(
                                tx = tx,
                                showDivider = i < recent.lastIndex,
                                onClick = { onEdit(tx) },
                                subLabel = day,
                                onArchive = { onArchive(tx) },
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

private fun currentDayStamp(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

private fun formatDay(millis: Long, language: VoyagerLanguage): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    if (language == VoyagerLanguage.ENGLISH) {
        val today = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return java.text.SimpleDateFormat(if (today) "'Today' HH:mm" else "MMM d HH:mm", language.locale).format(millis)
    }
    return when {
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "今天 " +
            "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        else -> "%d月%d日".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) +
            " %02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}

internal val VoyagerGold = Color(0xFFE5C38A)
internal val VoyagerIvory = Color(0xFFF6F0FC)

@Composable
private fun VoyagerHomeHeader(
    period: String,
    income: Long,
    expense: Long,
    dailyExpenses: List<Long>,
    today: Int,
    onOpen: () -> Unit,
) {
    val language = LocalVoyagerLanguage.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        color = scheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(period, modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium)
                VoyagerPvWordmark(
                    modifier = Modifier.width(108.dp),
                    tint = if (LocalJzDark.current) null else scheme.primary,
                )
            }
            VoyagerMusicStaff(Modifier.fillMaxWidth(), fullPhrase = true)
            Text(language("本月结余", "Monthly balance"), Modifier.padding(top = 5.dp), color = scheme.onSurfaceVariant, fontSize = 13.sp)
            val balance = income - expense
            Text(
                (if (balance < 0) "−" else "") + fmtCents(balance),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 40.sp,
                color = scheme.onSurface,
            )
            VoyagerCashFlow(income, expense)
            HorizontalDivider(color = scheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                VoyagerExpenseRegister(dailyExpenses, today, Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(language("本月明细", "This month"), color = scheme.primary, style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null,
                        tint = scheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VoyagerExpenseRegister(expenses: List<Long>, today: Int, modifier: Modifier = Modifier) {
    val language = LocalVoyagerLanguage.current
    val scheme = MaterialTheme.colorScheme
    val peak = expenses.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val spentDays = expenses.count { it > 0L }
    Column(modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(language("每日支出", "Daily spending"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(language("${today}日", "Day $today"), style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 3.dp).height(18.dp).semantics {
            contentDescription = language("本月已有 ${spentDays} 天记有支出", "Days with expenses this month: $spentDays")
        }) {
            val cellWidth = size.width / expenses.size
            val quiet = scheme.onSurfaceVariant.copy(alpha = .46f)
            expenses.forEachIndexed { index, amount ->
                val active = index + 1 == today
                val fraction = (amount.toDouble() / peak).coerceIn(0.0, 1.0).toFloat()
                val height = if (amount == 0L) 2.dp.toPx() else 5.dp.toPx() + (size.height - 5.dp.toPx()) * fraction
                val barWidth = cellWidth * .57f
                drawRect(
                    color = if (active) scheme.primary else if (amount > 0L) scheme.onSurfaceVariant else quiet,
                    topLeft = Offset(cellWidth * index + (cellWidth - barWidth) / 2, size.height - height),
                    size = androidx.compose.ui.geometry.Size(barWidth, height),
                )
            }
        }
    }
}

@Composable
private fun VoyagerCashFlow(income: Long, expense: Long) {
    val language = LocalVoyagerLanguage.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        val scale = LocalDensity.current.fontScale
        if (maxWidth < (244 * scale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VoyagerTotal(language("本月收入", "Monthly income"), income)
                VoyagerTotal(language("本月支出", "Monthly expenses"), expense)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                VoyagerTotal(language("本月收入", "Monthly income"), income, Modifier.weight(1f))
                VoyagerTotal(language("本月支出", "Monthly expenses"), expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VoyagerTotal(label: String, cents: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmtCents(cents), style = MaterialTheme.typography.displaySmall, fontSize = 19.sp)
    }
}

@Composable
internal fun VoyagerStarMotif(modifier: Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width * 0.86f, size.height * 0.25f)
        val radius = minOf(size.width * 0.29f, size.height * 0.49f).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF7462BD).copy(alpha = 0.36f), Color.Transparent),
                center, radius,
            ),
            radius = radius,
            center = center,
        )

        val orbit = Path().apply {
            moveTo(center.x - radius * 1.24f, center.y + radius * 0.57f)
            cubicTo(
                center.x - radius * 1.12f, center.y - radius * 0.09f,
                center.x + radius * 0.36f, center.y + radius * 0.61f,
                center.x + radius * 0.83f, center.y - radius * 0.53f,
            )
        }
        drawPath(orbit, Color(0xFFAA94E7).copy(alpha = 0.46f), style = Stroke(0.8.dp.toPx()))

        val tip = radius * 0.34f
        val star = Path().apply {
            repeat(16) { index ->
                val angle = (-90 + index * 22.5) * Math.PI / 180
                val length = when {
                    index % 4 == 0 -> tip
                    index % 2 == 0 -> tip * 0.62f
                    else -> tip * 0.23f
                }
                val x = center.x + kotlin.math.cos(angle).toFloat() * length
                val y = center.y + kotlin.math.sin(angle).toFloat() * length
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(star, VoyagerGold)
        drawCircle(VoyagerIvory, tip * 0.15f, center)
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
    val language = LocalVoyagerLanguage.current
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
                    language("预算", "Budgets"),
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
                Text(language("设置预算", "Set a budget"), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    language("总预算或分类预算，每月 / 每周自动重置", "Set an overall or category budget that resets weekly or monthly"),
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
                                language("总预算 · ", "Overall budget · ") + Periods.label(total.budget.period) + (if (over) language(" · 已超支", " · Over budget") else ""),
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
                            language("已支 ${fmtCents(total.spent)}", "Spent ${fmtCents(total.spent)}"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            language("预算 ${fmtCents(total.budget.amountCents)}", "Budget ${fmtCents(total.budget.amountCents)}"),
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
                                            language("${Periods.label(p.budget.period)} · 已用 ${fmtCents(p.spent)} / ${fmtCents(p.budget.amountCents)}", "${language.periodLabel(p.budget.period)} · ${fmtCents(p.spent)} of ${fmtCents(p.budget.amountCents)}"),
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
                            language("管理预算 ›", "Manage budgets ›"),
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
