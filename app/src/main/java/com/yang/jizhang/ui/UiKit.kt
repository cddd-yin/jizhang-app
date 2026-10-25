package com.yang.jizhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import kotlin.math.roundToLong
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.notify.keepalive.ListenerStatus

/**
 * v3.0.0 共享组件库：分类圆徽 / 月份切换 / 状态横幅 / 交易行 / 图表 / 预算仪表。
 * 全部为新写的 Expenso 风格组件，不复用 v2.x 旧组件。
 */

// ── 分类圆徽 ──

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
        Text(
            CategoryRules.icon(category),
            fontSize = (size.value * 0.46f).sp,
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
    state: ListenerStatus.DisplayState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val needPermission = state == ListenerStatus.DisplayState.NEED_PERMISSION
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

/**
 * 交易行。onDelete 非空时支持左滑删除（v3.3.0 交互）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TxRow(
    tx: TxEntity,
    showDivider: Boolean,
    onClick: () -> Unit,
    subLabel: String = "${tx.category} · ${tx.source}",
    onDelete: (() -> Unit)? = null,
) {
    Column {
        if (onDelete != null) {
            val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                        onDelete()
                        true
                    } else false
                },
            )
            androidx.compose.material3.SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .background(expenseColor().copy(alpha = 0.14f))
                            .padding(end = 20.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("删除", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = expenseColor())
                    }
                },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TxRowContent(tx, subLabel)
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TxRowContent(tx, subLabel)
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = 68.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
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
fun EmptyState(emoji: String, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 42.sp)
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
    val animated = remember { Animatable(cents, centsConverter) }
    androidx.compose.runtime.LaunchedEffect(cents) {
        animated.animateTo(cents, tween(800, easing = FastOutSlowInEasing))
    }
    Text(
        fmtCents(animated.value),
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        color = color,
        modifier = modifier,
    )
}

// ── 图表数据 ──

data class ChartEntry(val label: String, val value: Double, val color: Color)

/** 柱状图：圆角柱 + 月份标签，当前期满色、其余降透明；出现/数据变化时柱子生长动画（v3.3.0） */
@Composable
fun BarChart(
    data: List<ChartEntry>,
    modifier: Modifier = Modifier,
) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val axis = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    // 标签布局结果缓存（v3.9.0：避免动画每帧重复 measure）
    val labels = remember(data) { data.map { measurer.measure(it.label, TextStyle(fontSize = 11.sp, color = sub)) } }
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(data) {
        grow.snapTo(0f)
        grow.animateTo(1f, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Canvas(modifier) {
        if (data.isEmpty()) return@Canvas
        val maxV = data.maxOf { it.value }.coerceAtLeast(1.0)
        val labelH = 22f
        val chartH = size.height - labelH
        val gap = 14f
        val barW = (size.width - gap * (data.size + 1)) / data.size
        // 基线
        drawLine(axis, Offset(0f, chartH), Offset(size.width, chartH), 1.5f)
        data.forEachIndexed { i, e ->
            val hRatio = (e.value / maxV).toFloat() * grow.value
            val barH = (chartH - 26f) * hRatio
            val x = gap + i * (barW + gap)
            drawRoundRect(
                color = e.color,
                topLeft = Offset(x, chartH - barH),
                size = Size(barW, barH.coerceAtLeast(if (e.value > 0) 6f else 2f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2.4f, barW / 2.4f),
            )
            val label = labels[i]
            drawText(
                label,
                topLeft = Offset(x + (barW - label.size.width) / 2, chartH + 8f),
            )
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
    val animated = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(usedFraction) {
        animated.animateTo(
            usedFraction.coerceIn(0f, 1f),
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
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
        val frac = animated.value
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
