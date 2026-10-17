package com.yang.jizhang.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 图表组件（柱状图 / 饼图 / 进度环），抄自 SmartLedger Charts.kt 与 Expenso Charts.kt。
 */

/** 柱状图数据项 */
data class ChartEntry(
    val label: String,
    val value: Double,
    val color: Color? = null
)

/** 柱状图组件：圆角柱子 + 底部标签，带入场动画 */
@Composable
fun BarChart(
    data: List<ChartEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    animate: Boolean = true
) {
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.value }.toFloat()
    if (maxValue <= 0) return

    var animationProgress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 800),
        label = "bar_anim"
    )

    LaunchedEffect(data) {
        animationProgress = 1f
    }

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val barCount = data.size
        val totalWidth = size.width
        val totalHeight = size.height
        val bottomPadding = 40f
        val topPadding = 16f
        val chartHeight = totalHeight - bottomPadding - topPadding
        val barWidth = (totalWidth / barCount) * 0.6f
        val gap = (totalWidth / barCount) * 0.4f

        data.forEachIndexed { index, entry ->
            val barHeight = (entry.value.toFloat() / maxValue) * chartHeight * animatedProgress
            val x = index * (barWidth + gap) + gap / 2
            val y = topPadding + chartHeight - barHeight

            // 画圆角柱子
            drawRoundRect(
                color = entry.color ?: barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // 画标签
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = textColor.hashCode()
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(
                    entry.label,
                    x + barWidth / 2,
                    totalHeight - 4f,
                    paint
                )
            }
        }
    }
}

/** 饼图组件：按占比画扇区，带入场动画 */
@Composable
fun PieChart(
    data: List<ChartEntry>,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    if (data.isEmpty()) return

    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0) return

    var animationProgress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 800),
        label = "pie_anim"
    )

    LaunchedEffect(data) {
        animationProgress = 1f
    }

    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height)
        val radius = diameter / 2
        val center = Offset(size.width / 2, size.height / 2)
        val rect = androidx.compose.ui.geometry.Rect(
            center.x - radius,
            center.y - radius,
            center.x + radius,
            center.y + radius
        )

        var startAngle = -90f
        data.forEach { entry ->
            val sweepAngle = (entry.value.toFloat() / total) * 360f * animatedProgress
            drawArc(
                color = entry.color ?: Color.Gray,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height)
            )
            startAngle += sweepAngle
        }
    }
}

/**
 * 预算进度环：track + 圆头进度弧 + 中心百分比。
 * 抄自 Expenso ProgressRing（progress 可为 >1，超出会全满并显示实际百分比）。
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 18f,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "ring_anim",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val trackColor = color.copy(alpha = 0.15f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val radius = (width.coerceAtMost(height) - strokeWidth) / 2
            val center = Offset(width / 2, height / 2)
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
        }
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
