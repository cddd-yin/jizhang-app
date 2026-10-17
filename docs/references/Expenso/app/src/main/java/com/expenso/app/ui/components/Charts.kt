package com.expenso.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- PROGRESS RING FOR SAVINGS GOALS ---
@Composable
fun ProgressRing(
    progress: Float, // 0.0 to 1.0+
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 24f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = ExpensoAnimations.defaultTween(),
        label = "progress"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val trackColor = color.copy(alpha = 0.15f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val radius = (width.coerceAtMost(height) - strokeWidth) / 2
            val center = Offset(width / 2, height / 2)

            // Draw track
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // Draw progress arc
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
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// --- HORIZONTAL PROGRESS BAR FOR BUDGETS ---
@Composable
fun HorizontalProgressBar(
    progress: Float, // 0.0 to 1.0+
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = ExpensoAnimations.defaultTween(),
        label = "progress"
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = animatedProgress)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
            )
        }
    }
}

// --- EXPENSE PIE CHART ---
data class PieSegment(
    val value: Float,
    val color: Color,
    val name: String
)

@Composable
fun PieChart(
    segments: List<PieSegment>,
    modifier: Modifier = Modifier
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = ExpensoAnimations.defaultTween(),
        label = "pie_chart_anim"
    )
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (total == 0f) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Canvas(modifier = Modifier.size(130.dp)) {
                var startAngle = -90f
                segments.forEach { segment ->
                    val sweepAngle = (segment.value / total) * 360f * animatedProgress
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweepAngle
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                segments.sortedByDescending { it.value }.take(5).forEach { segment ->
                    val pct = if (total > 0) (segment.value / total * 100).toInt() else 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(segment.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${segment.name} ($pct%)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// --- INCOME VS EXPENSE BAR CHART ---
@Composable
fun BarChart(
    income: Float,
    expense: Float,
    modifier: Modifier = Modifier
) {
    val maxVal = maxOf(income, expense, 1f)
    val incomeHeightRatio = income / maxVal
    val expenseHeightRatio = expense / maxVal

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = ExpensoAnimations.defaultTween(),
        label = "bar_chart_anim"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Income Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    text = String.format("%.0f", income),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight(fraction = (incomeHeightRatio * animatedProgress).coerceAtLeast(0.05f))
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(Color(0xFF4CAF50))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Income",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expense Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    text = String.format("%.0f", expense),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF44336)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight(fraction = (expenseHeightRatio * animatedProgress).coerceAtLeast(0.05f))
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(Color(0xFFF44336))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Expense",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- MONTHLY NET TRENDS LINE CHART ---
@Composable
fun LineChart(
    points: List<Float>, // daily values or monthly points
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No Trend Data Available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxVal = points.maxOrNull()?.takeIf { it > 0 } ?: 1f
    val minVal = points.minOrNull() ?: 0f
    val range = maxVal - minVal

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = ExpensoAnimations.defaultTween(),
        label = "line_chart_anim"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val pointCount = points.size

        if (pointCount > 1) {
            val dx = width / (pointCount - 1)
            var prevX = 0f
            var prevY = height - ((points[0] - minVal) / (if (range == 0f) 1f else range)) * height

            val animatedWidth = width * animatedProgress

            for (i in 1 until pointCount) {
                val nextX = i * dx
                val nextY = height - ((points[i] - minVal) / (if (range == 0f) 1f else range)) * height
                
                if (nextX <= animatedWidth) {
                    drawLine(
                        color = Color(0xFF03A9F4),
                        start = Offset(prevX, prevY),
                        end = Offset(nextX, nextY),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                } else if (prevX < animatedWidth) {
                    val t = (animatedWidth - prevX) / (nextX - prevX)
                    val partialX = prevX + t * (nextX - prevX)
                    val partialY = prevY + t * (nextY - prevY)
                    drawLine(
                        color = Color(0xFF03A9F4),
                        start = Offset(prevX, prevY),
                        end = Offset(partialX, partialY),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }
                
                prevX = nextX
                prevY = nextY
            }
        } else {
            // Draw a single center dot if only one point
            drawCircle(
                color = Color(0xFF03A9F4),
                radius = 12f * animatedProgress,
                center = Offset(width / 2, height / 2)
            )
        }
    }
}
