package com.expenso.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expenso.app.data.TransactionWithDetails
import java.text.SimpleDateFormat
import java.util.*

data class SectorMonthData(
    val monthName: String, // e.g. "Jan", "Feb"
    val year: Int,
    val totalExpense: Float,
    val categoryBreakdown: Map<Int, Float> // categoryId -> amount
)

data class SectorCategoryLegend(
    val id: Int,
    val name: String,
    val color: Color,
    val isVisible: Boolean
)

@Composable
fun RechartsSectorSpendingChart(
    transactions: List<TransactionWithDetails>,
    currency: String,
    modifier: Modifier = Modifier
) {
    // 1. Prepare Last 6 Months range
    val last6Months = remember(transactions) {
        val cal = Calendar.getInstance()
        val sdfMonth = SimpleDateFormat("MMM", Locale.getDefault())
        val list = mutableListOf<Pair<Int, Int>>() // Pair(month 0-11, year)
        
        for (i in 5 downTo 0) {
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.MONTH, -i)
            list.add(Pair(tempCal.get(Calendar.MONTH), tempCal.get(Calendar.YEAR)))
        }
        list
    }

    // 2. Extract unique categories that are expenses in these transactions
    val categoryMap = remember(transactions) {
        transactions.filter { it.type == "expense" }
            .associateBy({ it.categoryId }, { Pair(it.categoryName, it.categoryColor) })
    }

    // State for toggling individual category visibility in the chart
    var visibleCategoryIds by remember(categoryMap) {
        mutableStateOf(categoryMap.keys.toSet())
    }

    // 3. Group expense transactions by month and category
    val chartData = remember(transactions, last6Months, visibleCategoryIds) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        last6Months.map { (m, y) ->
            val monthTransactions = transactions.filter { tx ->
                if (tx.type != "expense") return@filter false
                try {
                    val dateStr = tx.transactionDate.split("T").first()
                    val date = sdf.parse(dateStr)
                    val cal = Calendar.getInstance().apply { if (date != null) time = date }
                    cal.get(Calendar.MONTH) == m && cal.get(Calendar.YEAR) == y
                } catch (e: Exception) {
                    false
                }
            }

            val breakdown = mutableMapOf<Int, Float>()
            var total = 0f
            
            monthTransactions.forEach { tx ->
                if (visibleCategoryIds.contains(tx.categoryId)) {
                    val amt = tx.amount.toFloat()
                    breakdown[tx.categoryId] = (breakdown[tx.categoryId] ?: 0f) + amt
                    total += amt
                }
            }

            val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            SectorMonthData(
                monthName = monthNames[m],
                year = y,
                totalExpense = total,
                categoryBreakdown = breakdown
            )
        }
    }

    // Maximum height calculations for scaling
    val maxMonthlyExpense = remember(chartData) {
        chartData.maxOfOrNull { it.totalExpense }?.coerceAtLeast(100f) ?: 100f
    }

    // Animated progression for smooth chart entrance
    val animScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = ExpensoAnimations.defaultTween(),
        label = "chartScale"
    )

    // Interactive tooltip state
    var selectedMonthIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sector Analysis Dashboard",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Monthly expenditure categorized by commercial sector",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Canvas container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Background grid lines and bars
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(chartData) {
                            detectTapGestures { offset ->
                                val colWidth = size.width / chartData.size
                                val tappedIndex = (offset.x / colWidth).toInt()
                                if (tappedIndex in chartData.indices) {
                                    selectedMonthIndex = if (selectedMonthIndex == tappedIndex) null else tappedIndex
                                }
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val colCount = chartData.size
                    val colWidth = canvasWidth / colCount
                    val barWidth = colWidth * 0.45f

                    // 1. Draw dashed horizontal grid lines (Recharts style)
                    val gridLineCount = 4
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    
                    for (i in 0..gridLineCount) {
                        val y = (canvasHeight / gridLineCount) * i
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.35f),
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 2f,
                            pathEffect = pathEffect
                        )
                    }

                    // 2. Draw Stacked Bars
                    chartData.forEachIndexed { colIdx, monthData ->
                        val colCenterX = colIdx * colWidth + (colWidth / 2)
                        var currentYBottom = canvasHeight // start from bottom

                        // Sort segments to make rendering consistent
                        val sortedBreakdown = monthData.categoryBreakdown.entries.sortedBy { it.key }
                        
                        sortedBreakdown.forEach { (catId, amount) ->
                            val colorStr = categoryMap[catId]?.second ?: "#9E9E9E"
                            val color = try { Color(android.graphics.Color.parseColor(colorStr)) } catch (e: Exception) { Color.Gray }
                            
                            val segmentHeight = (amount / maxMonthlyExpense) * canvasHeight * animScale
                            val rectTop = currentYBottom - segmentHeight
                            
                            // Draw each stacked segment as a smooth rounded rectangle
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(colCenterX - (barWidth / 2), rectTop),
                                size = Size(barWidth, segmentHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            currentYBottom = rectTop
                        }

                        // Highlight selected column with a soft backdrop (Recharts hover bar)
                        if (selectedMonthIndex == colIdx) {
                            drawRoundRect(
                                color = Color.Gray.copy(alpha = 0.08f),
                                topLeft = Offset(colIdx * colWidth + 4f, 0f),
                                size = Size(colWidth - 8f, canvasHeight),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                        }
                    }
                }
            }

            // Month Labels Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                chartData.forEachIndexed { index, data ->
                    Text(
                        text = data.monthName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selectedMonthIndex == index) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (selectedMonthIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // FLOATING TOOLTIP (Exactly like Recharts Tooltip)
            if (selectedMonthIndex != null) {
                val sIdx = selectedMonthIndex!!
                val data = chartData[sIdx]
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${data.monthName} ${data.year} Total",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$currency${String.format("%,.2f", data.totalExpense)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        if (data.categoryBreakdown.isEmpty()) {
                            Text(
                                text = "No sector expenses recorded in this period",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            data.categoryBreakdown.entries.sortedByDescending { it.value }.forEach { (catId, amount) ->
                                val catName = categoryMap[catId]?.first ?: "Other"
                                val colorStr = categoryMap[catId]?.second ?: "#9E9E9E"
                                val color = try { Color(android.graphics.Color.parseColor(colorStr)) } catch (e: Exception) { Color.Gray }
                                
                                val pct = if (data.totalExpense > 0) (amount / data.totalExpense * 100).toInt() else 0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = catName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "$currency${String.format("%,.0f", amount)} ($pct%)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Informational prompt when no column is selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap on any monthly column above to view detailed sector breakdown",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // RECHARTS LEGENDS WITH TOGGLE INTERACTIVITY
            Text(
                text = "Interactive Legend (Tap to filter sectors)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categoryMap.entries.toList()) { (catId, catInfo) ->
                    val catName = catInfo.first
                    val colorStr = catInfo.second
                    val color = try { Color(android.graphics.Color.parseColor(colorStr)) } catch (e: Exception) { Color.Gray }
                    val isVisible = visibleCategoryIds.contains(catId)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isVisible) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isVisible) color.copy(alpha = 0.4f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable {
                            val nextSet = visibleCategoryIds.toMutableSet()
                            if (nextSet.contains(catId)) {
                                if (nextSet.size > 1) { // keep at least one visible
                                    nextSet.remove(catId)
                                }
                            } else {
                                nextSet.add(catId)
                            }
                            visibleCategoryIds = nextSet
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isVisible) color else Color.Gray.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isVisible) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RechartsPieChart(
    transactions: List<TransactionWithDetails>,
    currency: String,
    modifier: Modifier = Modifier
) {
    // 1. Filter expenses for the current month
    val currentMonthTransactions = remember(transactions) {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        transactions.filter { tx ->
            if (tx.type != "expense") return@filter false
            try {
                val dateStr = tx.transactionDate.split("T").first()
                val date = sdf.parse(dateStr)
                val txCal = Calendar.getInstance().apply { if (date != null) time = date }
                txCal.get(Calendar.MONTH) == currentMonth && txCal.get(Calendar.YEAR) == currentYear
            } catch (e: Exception) {
                false
            }
        }
    }

    // 2. Group by category
    val categoryTotals = remember(currentMonthTransactions) {
        currentMonthTransactions.groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount }.toFloat() }
            .filterValues { it > 0f }
            .toList()
            .sortedByDescending { it.second }
    }

    val totalExpense = remember(categoryTotals) {
        categoryTotals.sumOf { it.second.toDouble() }.toFloat()
    }

    val categoryColors = remember(currentMonthTransactions) {
        currentMonthTransactions.associateBy({ it.categoryName }, { it.categoryColor })
    }

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = ExpensoAnimations.defaultTween(),
        label = "pieScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Month Distribution",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Spending distribution across categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (categoryTotals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No expenses this month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pie Chart Canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.minDimension / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val strokeWidth = radius * 0.4f

                            var startAngle = -90f
                            categoryTotals.forEach { (catName, amount) ->
                                val sweepAngle = (amount / totalExpense) * 360f * animProgress
                                val colorStr = categoryColors[catName] ?: "#808080"
                                val color = try { Color(android.graphics.Color.parseColor(colorStr)) } catch (e: Exception) { Color.Gray }

                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth),
                                    topLeft = Offset(center.x - radius + strokeWidth / 2f, center.y - radius + strokeWidth / 2f),
                                    size = Size(radius * 2 - strokeWidth, radius * 2 - strokeWidth)
                                )
                                startAngle += sweepAngle
                            }
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "$currency${String.format("%,.0f", totalExpense)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Legend
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryTotals.forEach { (catName, amount) ->
                            val colorStr = categoryColors[catName] ?: "#808080"
                            val color = try { Color(android.graphics.Color.parseColor(colorStr)) } catch (e: Exception) { Color.Gray }
                            val pct = if (totalExpense > 0f) ((amount / totalExpense) * 100f).toInt() else 0

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = catName,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$currency${String.format("%,.0f", amount)} ($pct%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
