package com.expenso.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import com.expenso.app.data.*
import com.expenso.app.ui.components.AnimatedEntranceItem
import com.expenso.app.ui.components.IconMapper
import com.expenso.app.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BudgetsScreen(
    viewModel: ExpensoViewModel,
    onNavigateToAddBudget: () -> Unit,
    onEditBudget: (com.expenso.app.data.Budget) -> Unit = {}
) {
    val budgets by viewModel.allBudgets.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currencySymbol = settings?.currency ?: "₹"

    var selectedFilter by remember { mutableStateOf("All") }
    val currentTime = System.currentTimeMillis()
    
    val activeBudgets = budgets.filter { budget ->
        if (budget.startDate != null && budget.endDate != null) {
            currentTime in budget.startDate..budget.endDate
        } else {
            val cal = Calendar.getInstance()
            budget.month == cal.get(Calendar.MONTH) + 1 && budget.year == cal.get(Calendar.YEAR)
        }
    }

    val filteredBudgets = activeBudgets.filter { budget ->
        when (selectedFilter) {
            "Weekly" -> budget.budgetType == "WEEKLY"
            "Monthly" -> budget.budgetType == "MONTHLY"
            "Custom" -> budget.budgetType == "CUSTOM"
            else -> true
        }
    }
    
    var selectedBudgetIds by remember { mutableStateOf(setOf<Int>()) }
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = selectedBudgetIds.isNotEmpty()) {
        selectedBudgetIds = emptySet()
    }

    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(top = 32.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            AnimatedContent(
                targetState = selectedBudgetIds.isNotEmpty(),
                label = "top_bar_animation"
            ) { isSelectionMode ->
                if (isSelectionMode) {
                    // Contextual Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                .clickable { selectedBudgetIds = emptySet() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        }
                        
                        Text(
                            text = "${selectedBudgetIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )
                        
                        if (selectedBudgetIds.size == 1) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                    .clickable {
                                        val budgetId = selectedBudgetIds.first()
                                        val budget = budgets.find { it.id == budgetId }
                                        if (budget != null) {
                                            onEditBudget(budget)
                                        }
                                        selectedBudgetIds = emptySet()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                .clickable {
                                    val budgetsToDelete = budgets.filter { it.id in selectedBudgetIds }
                                    val deletedCount = selectedBudgetIds.size
                                    selectedBudgetIds.forEach { id ->
                                        budgets.find { it.id == id }?.let { budget ->
                                            viewModel.deleteBudget(budget)
                                        }
                                    }
                                    viewModel.showSnackbar(
                                        message = "$deletedCount budget(s) deleted"
                                    )
                                    selectedBudgetIds = emptySet()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    com.expenso.app.ui.components.ExpensoTopBar(
                        title = "Expenso",
                        actionIcon = Icons.Rounded.Add,
                        onActionClick = onNavigateToAddBudget,
                        actionContentDescription = "New Budget"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Budget Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Monthly", "Custom").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val borderModifier = if (isSelected) {
                        Modifier
                    } else {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .then(borderModifier)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { selectedFilter = filter },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (filteredBudgets.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = 120.dp
                        )
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DonutLarge, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(40.dp), 
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "No Budgets Configured", 
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), 
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Set monthly spending limits for categories or overall expenses to keep your finances in check.", 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = onNavigateToAddBudget,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Create First Budget")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = 120.dp // To clear the FAB just in case
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                    // Dashboard calculations
                    item {
                        val totalBudgetAmount = filteredBudgets.sumOf { it.budgetAmount }
                        val totalSpentAmount = transactions.filter { tx ->
                            filteredBudgets.any { budget -> isTransactionInBudget(tx, budget) }
                        }.sumOf { it.amount }

                        val totalRemaining = (totalBudgetAmount - totalSpentAmount).coerceAtLeast(0.0)
                        val totalPercentageUsed = if (totalBudgetAmount > 0) (totalSpentAmount / totalBudgetAmount).toFloat() else 0f

                        val isOverBudget = totalSpentAmount > totalBudgetAmount
                        val statusColor = if (isOverBudget) Color(0xFFFF7575) else Color(0xFF81C784)
                        val progressColor = if (isOverBudget) Color(0xFFFF7575) else Color(0xFF81C784)
                        val centralAmount = if (isOverBudget) (totalSpentAmount - totalBudgetAmount) else (totalBudgetAmount - totalSpentAmount)

                        val targetPercentage = if (totalBudgetAmount > 0) (totalSpentAmount / totalBudgetAmount).toFloat().coerceIn(0f, 1f) else 0f
                        val animatedTotalProgress by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (animationPlayed) targetPercentage else 0f,
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween(),
                            label = "totalProgressAnimation"
                        )
                        
                        AnimatedEntranceItem(index = 0) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                                    Text(
                                        text = "Budget Overview",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        val density = LocalDensity.current
                                        val availableWidthDp = maxWidth
                                        val gaugeWidthDp = minOf(220.dp, availableWidthDp - 32.dp)
                                        val gaugeHeightDp = gaugeWidthDp / 2f + 12.dp

                                        Box(
                                            modifier = Modifier
                                                .width(gaugeWidthDp)
                                                .height(gaugeHeightDp),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                            Canvas(
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                val strokeWidth = 16.dp.toPx()
                                                val r = (size.width - strokeWidth) / 2f

                                                val rectLeft = strokeWidth / 2f
                                                val rectTop = strokeWidth / 2f
                                                val rectSize = size.width - strokeWidth

                                                // Draw track
                                                drawArc(
                                                    color = trackColor,
                                                    startAngle = 180f,
                                                    sweepAngle = 180f,
                                                    useCenter = false,
                                                    topLeft = Offset(rectLeft, rectTop),
                                                    size = Size(rectSize, rectSize),
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                                )

                                                // Draw progress
                                                drawArc(
                                                    color = progressColor,
                                                    startAngle = 180f,
                                                    sweepAngle = animatedTotalProgress * 180f,
                                                    useCenter = false,
                                                    topLeft = Offset(rectLeft, rectTop),
                                                    size = Size(rectSize, rectSize),
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                                )
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isOverBudget) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        tint = statusColor,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isOverBudget) "EXCEEDED" else "REMAINING",
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            letterSpacing = 1.2.sp,
                                                            fontSize = 11.sp
                                                        ),
                                                        color = statusColor
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(2.dp))

                                                Text(
                                                    text = "$currencySymbol${String.format(java.util.Locale.getDefault(), "%,.1f", centralAmount)}",
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 22.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Limit",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$currencySymbol${String.format(java.util.Locale.getDefault(), "%,.0f", totalBudgetAmount)}",
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Spent",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$currencySymbol${String.format(java.util.Locale.getDefault(), "%,.0f", totalSpentAmount)}",
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }

                    if (filteredBudgets.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Active Budgets",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        itemsIndexed(filteredBudgets, key = { _, budget -> budget.id }) { idx, budget ->
                            val isSelected = selectedBudgetIds.contains(budget.id)
                            BudgetListItem(
                                budget = budget,
                                categories = categories,
                                transactions = transactions,
                                currencySymbol = currencySymbol,
                                viewModel = viewModel,
                                idx = idx,
                                isSelected = isSelected,
                                onClick = {
                                    if (selectedBudgetIds.isNotEmpty()) {
                                        selectedBudgetIds = if (isSelected) {
                                            selectedBudgetIds - budget.id
                                        } else {
                                            selectedBudgetIds + budget.id
                                        }
                                    } else {
                                        onEditBudget(budget)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedBudgetIds = if (isSelected) {
                                        selectedBudgetIds - budget.id
                                    } else {
                                        selectedBudgetIds + budget.id
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No budgets found for this month.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
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

@OptIn(ExperimentalFoundationApi::class)
fun isTransactionInBudget(transaction: com.expenso.app.data.TransactionWithDetails, budget: com.expenso.app.data.Budget): Boolean {
    if (transaction.type != "expense") return false
    if (budget.categoryId != null && transaction.categoryId != budget.categoryId) return false
    
    val txDateStr = transaction.transactionDate // yyyy-MM-dd
    
    if (budget.budgetType == "CUSTOM" && budget.startDate != null && budget.endDate != null) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        try {
            val txTime = sdf.parse(txDateStr)?.time ?: 0L
            return txTime in budget.startDate..budget.endDate
        } catch (e: Exception) { return false }
    } else if (budget.budgetType == "WEEKLY") {
        val calendar = java.util.Calendar.getInstance()
        val currentWeek = calendar.get(java.util.Calendar.WEEK_OF_YEAR)
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        try {
            val txDate = sdf.parse(txDateStr)
            val txCal = java.util.Calendar.getInstance()
            if (txDate != null) txCal.time = txDate
            return txCal.get(java.util.Calendar.WEEK_OF_YEAR) == currentWeek && txCal.get(java.util.Calendar.YEAR) == currentYear
        } catch (e: Exception) { return false }
    } else {
        return txDateStr.startsWith("${budget.year}-${String.format("%02d", budget.month)}")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetListItem(
    budget: Budget,
    categories: List<Category>,
    transactions: List<TransactionWithDetails>,
    currencySymbol: String,
    viewModel: ExpensoViewModel,
    idx: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val matchingCat = categories.firstOrNull { it.id == budget.categoryId }
    val labelName = matchingCat?.name ?: "Overall"
    val color = try { 
        Color(android.graphics.Color.parseColor(matchingCat?.color ?: "#009688")) 
    } catch (e: Exception) { 
        MaterialTheme.colorScheme.primary 
    }

    val spentAmount = transactions.filter {
        isTransactionInBudget(it, budget)
    }.sumOf { it.amount }
    val progress = if (budget.budgetAmount > 0) (spentAmount / budget.budgetAmount).toFloat() else 0f
    
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val isOverBudget = spentAmount > budget.budgetAmount

    // Status indicators:
    val statusColor = when {
        progress > 1f -> Color(0xFFFF7575) // Soft Red
        progress >= 0.8f -> Color(0xFFFFB74D) // Soft Orange
        else -> Color(0xFF81C784) // Soft Green
    }

    AnimatedEntranceItem(index = idx + 2) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            IconMapper.CategoryIcon(
                                icon = matchingCat?.icon ?: "trending_down",
                                categoryName = matchingCat?.name ?: "Overall",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (!budget.budgetName.isNullOrBlank()) budget.budgetName!! else labelName, 
                                    fontWeight = FontWeight.Bold, 
                                    style = MaterialTheme.typography.titleMedium, 
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = budget.budgetType,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall / 2))
                            
                            val dateStr = if (budget.budgetType == "MONTHLY") {
                                val monthNames = arrayOf(
                                    "January", "February", "March", "April", "May", "June",
                                    "July", "August", "September", "October", "November", "December"
                                )
                                val monthStr = if (budget.month in 1..12) monthNames[budget.month - 1] else "Unknown"
                                "$monthStr, ${budget.year}"
                            } else if (budget.startDate != null && budget.endDate != null) {
                                val sdf = java.text.SimpleDateFormat("dd MMM ''yy", java.util.Locale.getDefault())
                                "${sdf.format(java.util.Date(budget.startDate))} - ${sdf.format(java.util.Date(budget.endDate))}"
                            } else {
                                "Budget"
                            }

                            val hasCustomName = !budget.budgetName.isNullOrBlank()
                            val nameSuffix = if (hasCustomName && labelName != "Overall") " • $labelName" else ""
                            Text(
                                    text = "Limit: $currencySymbol${budget.budgetAmount}$nameSuffix • $dateStr", 
                                    style = MaterialTheme.typography.bodySmall, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                    fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Edit Budget",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Alerts row
                if (progress >= 0.8f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                progress > 1f -> {
                                    val percent = (progress * 100).toInt()
                                    "Budget Exceeded! ($percent% used)"
                                }
                                progress >= 1f -> "100% of budget reached!"
                                else -> {
                                    val percent = (progress * 100).toInt()
                                    "$percent% of budget used!"
                                }
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = statusColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (animationPlayed) progress.coerceIn(0f, 1f).toFloat() else 0f,
                    animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween(),
                    label = "progressAnimation"
                )
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Spent: $currencySymbol${String.format("%.1f", spentAmount)}", 
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), 
                        color = if (isOverBudget) statusColor else MaterialTheme.colorScheme.onSurface
                    )
                    
                    val remaining = (budget.budgetAmount - spentAmount).coerceAtLeast(0.0)
                    Text(
                        text = "Remaining: $currencySymbol${String.format("%.1f", remaining)}", 
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), 
                        color = if (isOverBudget) statusColor else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
