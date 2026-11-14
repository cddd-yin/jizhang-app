package com.yang.jizhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import kotlin.math.roundToLong
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.engine.keepalive.ListenerHealth
import java.util.Calendar

/**
 * v3.0.0 共享组件库：分类圆徽 / 月份切换 / 状态横幅 / 交易行 / 图表 / 预算仪表。
 * 全部为新写的 Expenso 风格组件，不复用 v2.x 旧组件。
 */

// ── 分类圆徽 ──

/**
 * 分类 → Material 线性图标（v4.4.0：极简线条替代 emoji，官方图标语义保识别）。
 */
fun categoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector = when (category) {
    "餐饮美食" -> Icons.Outlined.Restaurant
    "交通出行" -> Icons.Outlined.DirectionsBus
    "购物消费" -> Icons.Outlined.ShoppingBag
    "生活缴费" -> Icons.Outlined.Bolt
    "居住住房" -> Icons.Outlined.Home
    "娱乐休闲" -> Icons.Outlined.SportsEsports
    "医疗健康" -> Icons.Outlined.MedicalServices
    "学习教育" -> Icons.Outlined.MenuBook
    "红包转账" -> Icons.Outlined.CardGiftcard
    "收入进账" -> Icons.Outlined.Paid
    "其他" -> Icons.Outlined.Category
    else -> Icons.Outlined.Label
}

/** 每个分类的专属色相（彩底圆徽 / 统计条共用） */
fun categoryHue(category: String): Color = when (category) {
    "餐饮美食" -> Color(0xFFF5A623)
    "交通出行" -> Color(0xFF3E8EF7)
    "购物消费" -> Color(0xFFEC6AA8)
    "生活缴费" -> Color(0xFF22C4DD)
    "居住住房" -> Color(0xFF14B8A6)
    "娱乐休闲" -> Color(0xFFA05CF0)
    "医疗健康" -> Color(0xFFEF5350)
    "学习教育" -> Color(0xFF6472F3)
    "红包转账" -> Color(0xFFFB6F92)
    "收入进账" -> Color(0xFF22C55E)
    else -> Color(0xFF8E97A8)
}

/** 彩色分类圆徽：色相底 + emoji 图标（Expenso 标志性元素） */
@Composable
fun CategoryCircle(category: String, size: Dp = 42.dp, modifier: Modifier = Modifier) {
    val dark = LocalJzDark.current
    val hue = categoryHue(category)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(hue.copy(alpha = if (dark) 0.20f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            categoryIcon(category),
            contentDescription = category,
            tint = hue,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

// ── 月份切换（Expenso 顶部分段胶囊同款） ──

@Composable
fun MonthSwitcher(
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwitchChevron("‹", onPrev)
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SwitchChevron("›", onNext)
        }
    }
}

@Composable
private fun SwitchChevron(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
    }
}

// ── 状态横幅（自动记账监听状态） ──

@Composable
fun StatusBanner(
    state: ListenerHealth.DisplayState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val needPermission = state == ListenerHealth.DisplayState.NEED_PERMISSION
    val tint = if (needPermission) expenseColor() else warnColor()
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (needPermission) "自动记账未开启，点此去授权"
                else "支付监听已断开，点此重新连接",
                fontSize = 13.sp,
                color = tint,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text("›", fontSize = 17.sp, color = tint, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── 交易行（明细/最近列表通用） ──

/** 左滑仅露出删除操作，右滑收起。按账单 ID 隔离手势状态。 */
@Composable
fun TxRow(
    tx: TxEntity,
    showDivider: Boolean,
    onClick: () -> Unit,
    subLabel: String = "${tx.category} · ${tx.source}",
    onDelete: (() -> Unit)? = null,
) = key(tx.id) {
    val actionWidth = 80.dp
    val density = LocalDensity.current
    val limit = with(density) { actionWidth.toPx() }
    val fling = with(density) { 500.dp.toPx() }
    var revealed by remember { mutableFloatStateOf(0f) }
    Column {
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            if (onDelete != null && revealed > 0f) {
                Box(
                    Modifier.align(Alignment.CenterEnd).matchParentSize()
                        .background(expenseColor().copy(alpha = 0.13f)),
                )
                Box(
                    Modifier.align(Alignment.CenterEnd).width(actionWidth).height(48.dp)
                        .clickable(role = Role.Button) {
                            revealed = 0f
                            onDelete()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("删除", color = expenseColor(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val drag = if (onDelete != null) {
                Modifier.draggable(
                    state = rememberDraggableState { delta -> revealed = (revealed - delta).coerceIn(0f, limit) },
                    orientation = Orientation.Horizontal,
                    reverseDirection = androidx.compose.ui.platform.LocalLayoutDirection.current ==
                        androidx.compose.ui.unit.LayoutDirection.Rtl,
                    onDragStopped = { velocity ->
                        val threshold = limit / 2f
                        revealed = when {
                            velocity > fling -> 0f
                            velocity < -fling -> limit
                            revealed >= threshold -> limit
                            else -> 0f
                        }
                    },
                )
            } else Modifier
            Row(
                Modifier.fillMaxWidth()
                    .offset { IntOffset(-revealed.roundToInt(), 0) }
                    .then(drag)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { if (revealed > 0f) revealed = 0f else onClick() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TxRowContent(tx, subLabel)
            }
        }
        if (showDivider) {
            Box(
                Modifier.padding(start = 68.dp).fillMaxWidth().height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TxRowContent(tx: TxEntity, subLabel: String) {
    CategoryCircle(tx.category)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text(
            tx.note.ifBlank { tx.category },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(subLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        fmtSigned(tx.amountCents, tx.isIncome),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (tx.isIncome) incomeColor() else MaterialTheme.colorScheme.onSurface,
    )
}

// ── 空状态 ──

@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("¥", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            hint,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── 区块标题 ──

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    if (LocalJzStyle.current == JzStyle.VOYAGER) {
        Row(
            modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, top = 6.dp),
    )
}

// ── 滚动数字（金额变化时平滑滚动，v3.3.0） ──

@Composable
fun AnimatedAmountText(
    cents: Long,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 34.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    letterSpacing: androidx.compose.ui.unit.TextUnit = (-0.5).sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
) {
    // 分→显示值的 Long 转换器（animation-core 无内置 Long converter）
    val centsConverter = remember {
        object : androidx.compose.animation.core.TwoWayConverter<Long, androidx.compose.animation.core.AnimationVector1D> {
            override val convertToVector: (Long) -> androidx.compose.animation.core.AnimationVector1D = {
                androidx.compose.animation.core.AnimationVector1D(it.toFloat())
            }
            override val convertFromVector: (androidx.compose.animation.core.AnimationVector1D) -> Long = {
                it.value.roundToLong()
            }
        }
    }
    val shown = if (LocalPowerSaving.current) cents else {
        val animated = remember { Animatable(cents, centsConverter) }
        androidx.compose.runtime.LaunchedEffect(cents) {
            animated.animateTo(cents, tween(800, easing = FastOutSlowInEasing))
        }
        animated.value
    }
    Text(
        if (shown < 0) "-${fmtCents(shown)}" else fmtCents(shown),
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        color = color,
        modifier = modifier,
    )
}

// ── 消费日历热力图（v3.10.0，GitHub 贡献图风格） ──

/**
 * 某月按日支出热力（v4.3.0 改正宗 GitHub 贡献图形式）：
 * 每行一周，圆形日期按支出金额分色，今日描边。
 */
@Composable
fun ExpenseCalendar(
    year: Int,
    month1: Int,
    txs: List<TxEntity>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.surfaceVariant
    val daySpend = remember(txs, year, month1) {
        val cal = Calendar.getInstance()
        txs.asSequence()
            .filter { !it.isIncome }
            .groupBy { tx ->
                cal.timeInMillis = tx.time
                cal.get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
    }
    val weeks = remember(year, month1) { buildCalendarGrid(year, month1) }
    val maxSpend = remember(daySpend) { (daySpend.values.maxOrNull() ?: 0L).coerceAtLeast(1L) }
    val today = remember { Calendar.getInstance() }
    val isCurrentMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) + 1 == month1
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    // GitHub 五档色阶（无支出 → 主题色四档渐深）
    fun levelColor(spend: Long?): androidx.compose.ui.graphics.Color = when {
        spend == null || spend <= 0 -> faint.copy(alpha = 0.45f)
        spend <= maxSpend / 4 -> accent.copy(alpha = 0.30f)
        spend <= maxSpend / 2 -> accent.copy(alpha = 0.55f)
        spend <= maxSpend - maxSpend / 4 -> accent.copy(alpha = 0.80f)
        else -> accent
    }

    Column(modifier) {
        val days = listOf("一", "二", "三", "四", "五", "六", "日")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            days.forEach {
                Text(it, fontSize = 11.sp, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { day ->
                        val spend = daySpend[day] ?: 0L
                        val color = if (day == null) Color.Transparent else levelColor(spend)
                        val onColor = if (spend > 0L) readableOn(color.compositeOver(MaterialTheme.colorScheme.surface))
                            else MaterialTheme.colorScheme.onSurface
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(color)
                                .then(if (day != null && isCurrentMonth && day == todayDay) {
                                    Modifier.border(1.5.dp, accent, CircleShape)
                                } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                Text(day.toString(), color = onColor, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        // 支出色阶图例
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("少", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            listOf(0.45f, 0.30f, 0.55f, 0.80f, 1f).forEachIndexed { i, a ->
                Box(
                    Modifier
                        .padding(horizontal = 1.5.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == 0) faint.copy(alpha = 0.45f)
                            else accent.copy(alpha = a)
                        ),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("多", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 构建某月日历网格：返回周列表（周一为第一列），月初/月末空位为 null */
private fun buildCalendarGrid(year: Int, month1: Int): List<List<Int?>> {
    val cal = Calendar.getInstance().apply {
        set(year, month1 - 1, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstIso = cal.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }
    val cells: List<Int?> = List(firstIso - 1) { null } + (1..daysInMonth).toList()
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    return padded.chunked(7)
}

// ── 图表数据 ──


data class ChartEntry(val label: String, val value: Double, val color: Color)

/** 趋势图保留分为单位的原始金额；每列都是可访问的选择项。 */
@Composable
internal fun ExpenseTrend(
    periods: List<Pair<String, Long>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    annual: Boolean = false,
) {
    val active = periods.getOrNull(selectedIndex) ?: return
    val scheme = MaterialTheme.colorScheme
    val archive = LocalJzStyle.current == JzStyle.VOYAGER
    val max = (periods.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Column(modifier) {
        val period = if (annual) "${active.first} 年" else active.first
        Text("${period}支出", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Text(
            fmtCents(active.second),
            fontSize = 27.sp,
            fontFamily = if (archive) androidx.compose.ui.text.font.FontFamily.Serif else null,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            periods.forEachIndexed { index, (label, cents) ->
                val selected = index == selectedIndex
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                        .background(if (selected) scheme.primaryContainer else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.BottomCenter) {
                        val fraction = (cents.toDouble() / max).toFloat().coerceIn(0f, 1f)
                        Box(
                            Modifier.fillMaxWidth(0.56f)
                                .height(if (cents > 0L) (84 * fraction).coerceAtLeast(4f).dp else 2.dp)
                                .background(
                                    scheme.primary.copy(alpha = if (selected) 1f else 0.42f),
                                    if (archive) RoundedCornerShape(3.dp) else CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** 饼图（环形） */
@Composable
fun PieChart(
    data: List<ChartEntry>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val total = data.sumOf { it.value }
        if (total <= 0.0) return@Canvas
        val stroke = Stroke(width = size.minDimension * 0.22f, cap = StrokeCap.Butt)
        val inset = stroke.width / 2 + 2f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        var start = -90f
        // 留 2° 缝隙让分片可辨
        val gapSweep = if (data.size > 1) 3f else 0f
        data.forEach { e ->
            val sweep = (e.value / total * 360f).toFloat()
            if (sweep > gapSweep) {
                drawArc(
                    color = e.color,
                    startAngle = start + gapSweep / 2,
                    sweepAngle = (sweep - gapSweep).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
            start += sweep
        }
    }
}

/** 半圆预算仪表：中间显示剩余/超支（Expenso Budget Overview 同款）；进度变化带补间动画（v3.3.0） */
@Composable
fun BudgetGauge(
    usedFraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val target = usedFraction.coerceIn(0f, 1f)
    val animated = if (LocalPowerSaving.current) null else {
        val animation = remember { Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(target) {
            animation.animateTo(target, tween(900, easing = FastOutSlowInEasing))
        }
        animation
    }
    Canvas(modifier) {
        val strokeW = size.minDimension * 0.14f
        val inset = strokeW / 2 + 2f
        val arcSize = Size(size.width - inset * 2, (size.height - inset * 2))
        val cap = StrokeCap.Round
        // 轨道：左端 180° → 右端 0°
        drawArc(
            color = trackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(strokeW, cap = cap),
        )
        val frac = animated?.value ?: target
        if (frac > 0.005f) {
            drawArc(
                color = progressColor,
                startAngle = 180f,
                sweepAngle = 180f * frac,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokeW, cap = cap),
            )
        }
    }
}
