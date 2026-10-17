package com.expenso.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expenso.app.data.ExpensoViewModel
import com.expenso.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: ExpensoViewModel,
    onSearchClick: () -> Unit
) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val budgets by viewModel.allBudgets.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val accounts by viewModel.allAccounts.collectAsState(initial = emptyList())
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    val allTags by viewModel.allTags.collectAsState(initial = emptySet())
    val activeFilterType by viewModel.filterType.collectAsState()
    val activeFilterCategories by viewModel.filterCategoryIds.collectAsState()
    val activeFilterAccounts by viewModel.filterAccountIds.collectAsState()
    val activeFilterTags by viewModel.filterTags.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var currentMonthCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDatePeriod by remember { mutableStateOf("All Time") }
    var showCustomDateRangeDialog by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }

    val currency = settings?.currency ?: "₹"

    LaunchedEffect(currentMonthCalendar) {
        val startCal = (currentMonthCalendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (currentMonthCalendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        viewModel.filterDateRange.value = Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    // Calculations
    val (incomeSum, expenseSum, netBalance) = remember(transactions) {
        val inc = transactions.filter { it.type == "income" }.sumOf { it.amount }.toFloat()
        val exp = transactions.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()
        Triple(inc, exp, inc - exp)
    }

    // Group expenses by category for Pie Chart
    val categorySegments = remember(transactions) {
        val expenses = transactions.filter { it.type == "expense" }
        val categoryTotals = expenses.groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount }.toFloat() }

        categoryTotals.map { (catId, total) ->
            val sampleTx = expenses.firstOrNull { it.categoryId == catId }
            val name = sampleTx?.categoryName ?: "Other"
            val colorStr = sampleTx?.categoryColor ?: "#9E9E9E"
            val color = try { Color(android.graphics.Color.parseColor(colorStr)) } catch (e: Exception) { Color.Gray }
            PieSegment(value = total, color = color, name = name)
        }
    }

    val topSpendingCategory = remember(categorySegments) {
        categorySegments.maxByOrNull { it.value }?.name ?: "N/A"
    }

    // Historical trends for Line Chart (net balances)
    val trendPoints = remember(transactions) {
        val sorted = transactions.sortedBy { it.transactionDate }
        var balanceAccumulator = 0f
        sorted.map { tx ->
            if (tx.type == "income") balanceAccumulator += tx.amount.toFloat()
            else balanceAccumulator -= tx.amount.toFloat()
            balanceAccumulator
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp)
            .padding(horizontal = 16.dp)
    ) {
        // ROW 1: Search Button at top right (when search is inactive) or Full Search Bar (when active)
        if (false) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search transactions...", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search Icon", modifier = Modifier.size(24.dp)) },
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.searchQuery.value = ""
                    }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Close search", modifier = Modifier.size(24.dp))
                    }
                },
                shape = CircleShape,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
        } else {
            com.expenso.app.ui.components.ExpensoTopBar(
                title = "Expenso",
                actionIcon = Icons.Rounded.Search,
                onActionClick = onSearchClick,
                actionContentDescription = "Search"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter controls
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        currentMonthCalendar = (currentMonthCalendar.clone() as Calendar).apply {
                            add(Calendar.MONTH, -1)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Previous Month",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                val sdfMonthYear = SimpleDateFormat("MMMM, yyyy", Locale.getDefault())

                AnimatedContent(
                    targetState = currentMonthCalendar.timeInMillis,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                        } else {
                            slideInHorizontally(initialOffsetX = { -it }) + fadeIn() togetherWith slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                        }.using(SizeTransform(clip = false))
                    },
                    label = "month_transition"
                ) { targetTimeInMillis ->
                    val targetCalendar = Calendar.getInstance().apply { timeInMillis = targetTimeInMillis }
                    val formattedMonthYearStr = sdfMonthYear.format(targetCalendar.time)
                    Text(
                        text = formattedMonthYearStr,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = {
                        currentMonthCalendar = (currentMonthCalendar.clone() as Calendar).apply {
                            add(Calendar.MONTH, 1)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Next Month",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )

                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Filter Records",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // CONTENT AREA
        if (transactions.isEmpty()) {
            com.expenso.app.ui.components.EmptyState(
                icon = androidx.compose.material.icons.Icons.Rounded.BarChart,
                title = "No Data Registered",
                message = "Record expenses or income to view visual analytics.",
                modifier = Modifier.weight(1f)
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // CURRENT MONTH PIE CHART COMPONENT
                AnimatedEntranceItem(index = 0) {
                    RechartsPieChart(
                        transactions = transactions,
                        currency = currency,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // RECHARTS SECTOR SPENDING DASHBOARD COMPONENT (NEW)
                AnimatedEntranceItem(index = 1) {
                    RechartsSectorSpendingChart(
                        transactions = transactions,
                        currency = currency,
                        modifier = Modifier.fillMaxWidth()
                    )
                }


                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // BOTTOM FILTER SHEET
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.25f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Filter Transactions",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(
                        onClick = {
                            viewModel.filterType.value = "All"
                            viewModel.filterCategoryIds.value = emptySet()
                            viewModel.filterAccountIds.value = emptySet()
                            viewModel.filterDateRange.value = null
                            viewModel.clearFilterTags()
                            selectedDatePeriod = "All Time"
                            customStartDate = null
                            customEndDate = null
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showFilterSheet = false
                                }
                            }
                        }
                    ) {
                        Text("Reset All")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Filters are cleared. New filters will be added here soon.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showFilterSheet = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // CUSTOM DATE RANGE DIALOG
    if (showCustomDateRangeDialog) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustomDateRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        customStartDate = dateRangePickerState.selectedStartDateMillis
                        customEndDate = dateRangePickerState.selectedEndDateMillis
                        
                        if (customStartDate != null && customEndDate != null) {
                            // Ensure time is at end of day for end date
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = customEndDate!!
                            cal.set(Calendar.HOUR_OF_DAY, 23)
                            cal.set(Calendar.MINUTE, 59)
                            cal.set(Calendar.SECOND, 59)
                            val finalEnd = cal.timeInMillis
                            customEndDate = finalEnd
                        } else {
                            selectedDatePeriod = "All Time"
                        }
                        showCustomDateRangeDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showCustomDateRangeDialog = false 
                        selectedDatePeriod = "All Time"
                    }
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text(text = "Select period", modifier = Modifier.padding(16.dp))
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
