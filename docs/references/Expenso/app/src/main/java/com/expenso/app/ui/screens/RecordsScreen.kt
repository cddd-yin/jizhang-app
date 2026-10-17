package com.expenso.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import com.expenso.app.data.ExpensoViewModel
import com.expenso.app.data.TransactionWithDetails
import com.expenso.app.ui.components.IconMapper
import com.expenso.app.ui.components.AnimatedEntranceItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    viewModel: ExpensoViewModel,
    onEditTransaction: (TransactionWithDetails) -> Unit,
    onAddTransactionClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    val accounts by viewModel.allAccounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilterType by viewModel.filterType.collectAsState()
    val activeFilterCategories by viewModel.filterCategoryIds.collectAsState()
    val activeFilterAccounts by viewModel.filterAccountIds.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val activeFilterTags by viewModel.filterTags.collectAsState()

    var currentMonthCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var isLoading by remember { mutableStateOf(!viewModel.isRecordsInitialLoadDone) }
    val coroutineScope = rememberCoroutineScope()

    var showCustomDateRangeDialog by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    var selectedDatePeriod by remember { mutableStateOf("All Time") }

    val activeHomeDateFilter by viewModel.selectedHomeDateFilter.collectAsState()

    fun isToday(cal: Calendar): Boolean {
        val today = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
               cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    fun isYesterday(cal: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
               cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    }

    LaunchedEffect(activeHomeDateFilter, currentMonthCalendar, customStartDate, customEndDate) {
        val startCal = (currentMonthCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (currentMonthCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        when (activeHomeDateFilter) {
            "Daily" -> {
                viewModel.filterDateRange.value = Pair(startCal.timeInMillis, endCal.timeInMillis)
            }
            "Weekly" -> {
                val firstDayOfWeek = Calendar.MONDAY
                startCal.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                val endOfWeekCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 6)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                viewModel.filterDateRange.value = Pair(startCal.timeInMillis, endOfWeekCal.timeInMillis)
            }
            "Monthly" -> {
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                viewModel.filterDateRange.value = Pair(startCal.timeInMillis, endCal.timeInMillis)
            }
            "6 Months" -> {
                startCal.add(Calendar.MONTH, -5)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                viewModel.filterDateRange.value = Pair(startCal.timeInMillis, endCal.timeInMillis)
            }
            "Yearly" -> {
                startCal.set(Calendar.MONTH, Calendar.JANUARY)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                endCal.set(Calendar.MONTH, Calendar.DECEMBER)
                endCal.set(Calendar.DAY_OF_MONTH, 31)
                viewModel.filterDateRange.value = Pair(startCal.timeInMillis, endCal.timeInMillis)
            }
            "Custom" -> {
                if (customStartDate != null && customEndDate != null) {
                    val calEnd = Calendar.getInstance().apply {
                        timeInMillis = customEndDate!!
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    viewModel.filterDateRange.value = Pair(customStartDate, calEnd.timeInMillis)
                } else {
                    viewModel.filterDateRange.value = Pair(startCal.timeInMillis, endCal.timeInMillis)
                }
            }
        }
        
        if (!viewModel.isRecordsInitialLoadDone) {
            isLoading = true
            kotlinx.coroutines.delay(600)
            isLoading = false
            viewModel.isRecordsInitialLoadDone = true
        } else {
            isLoading = false
        }
    }

    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val budgets by viewModel.allBudgets.collectAsState(initial = emptyList())

    val exceededBudgets = remember(allTransactions, budgets) {
        val exceededSet = mutableSetOf<Int>()
        budgets.forEach { budget ->
            val totalExpense = allTransactions.filter { 
                com.expenso.app.ui.screens.isTransactionInBudget(it, budget)
            }.sumOf { it.amount }
            
            if (totalExpense > budget.budgetAmount) {
                exceededSet.add(budget.id)
            }
        }
        exceededSet
    }

    val currencySymbol = settings?.currency ?: "₹"

    // Calculate Summary Metrics based on Current Transactions
    val (totalIncome, totalExpense, totalBalance) = remember(filteredTransactions) {
        val inc = filteredTransactions.filter { it.type == "income" }.sumOf { it.amount }
        val exp = filteredTransactions.filter { it.type == "expense" }.sumOf { it.amount }
        Triple(inc, exp, inc - exp)
    }

    // Group Transactions by Date String
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { tx ->
            try {
                val datePart = tx.transactionDate.split("T").first()
                val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val parsedDate = sdfInput.parse(datePart)
                val sdfDate = SimpleDateFormat("MMM d", Locale.getDefault())
                val sdfDay = SimpleDateFormat("EEEE", Locale.getDefault())
                parsedDate?.let { 
                    val dateStr = sdfDate.format(it)
                    val dayStr = sdfDay.format(it).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    "$dateStr, $dayStr"
                } ?: datePart
            } catch (e: Exception) {
                tx.transactionDate
            }
        }
    }

    var selectedTransactionIds by remember { mutableStateOf(setOf<Int>()) }

    BackHandler(enabled = selectedTransactionIds.isNotEmpty()) {
        selectedTransactionIds = emptySet()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(top = 32.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {

            // TOP SEARCH BAR & MONTH CONTROLS (ROW 1, ROW 2)
            AnimatedContent(
                targetState = selectedTransactionIds.isNotEmpty(),
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
                                .clickable { selectedTransactionIds = emptySet() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        }
                        
                        Text(
                            text = "${selectedTransactionIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )
                        
                        if (selectedTransactionIds.size == 1) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                    .clickable {
                                        val txId = selectedTransactionIds.first()
                                        val tx = allTransactions.find { it.id == txId }
                                        if (tx != null) {
                                            onEditTransaction(tx)
                                        }
                                        selectedTransactionIds = emptySet()
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
                                    val txsToDelete = allTransactions.filter { it.id in selectedTransactionIds }
                                    val deletedCount = selectedTransactionIds.size
                                    selectedTransactionIds.forEach { id ->
                                        viewModel.deleteTransaction(id)
                                    }
                                    viewModel.showSnackbar(
                                        message = "$deletedCount transaction(s) deleted",
                                        actionLabel = "Undo",
                                        onAction = {
                                            txsToDelete.forEach { tx ->
                                                viewModel.restoreTransaction(
                                                    com.expenso.app.data.Transaction(
                                                        id = tx.id,
                                                        amount = tx.amount,
                                                        type = tx.type,
                                                        categoryId = tx.categoryId,
                                                        accountId = tx.accountId,
                                                        note = tx.note,
                                                        transactionDate = tx.transactionDate,
                                                        createdAt = tx.createdAt,
                                                        updatedAt = tx.updatedAt,
                                                        attachmentPath = tx.attachmentPath,
                                                        tags = tx.tags
                                                    )
                                                )
                                            }
                                        }
                                    )
                                    selectedTransactionIds = emptySet()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    // Normal App Bar
                    com.expenso.app.ui.components.ExpensoTopBar(
                        title = "Expenso",
                        actionIcon = Icons.Rounded.Search,
                        onActionClick = onSearchClick,
                        actionContentDescription = "Search"
                    )
                }
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
                        if (activeHomeDateFilter != "Custom") {
                            IconButton(
                                onClick = {
                                    currentMonthCalendar = (currentMonthCalendar.clone() as Calendar).apply {
                                        when (activeHomeDateFilter) {
                                            "Daily" -> add(Calendar.DAY_OF_YEAR, -1)
                                            "Weekly" -> add(Calendar.WEEK_OF_YEAR, -1)
                                            "Monthly" -> add(Calendar.MONTH, -1)
                                            "6 Months" -> add(Calendar.MONTH, -6)
                                            "Yearly" -> add(Calendar.YEAR, -1)
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "Previous Period",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(36.dp))
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    if (activeHomeDateFilter == "Custom") {
                                        showCustomDateRangeDialog = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = Triple(activeHomeDateFilter, currentMonthCalendar.timeInMillis, "$customStartDate-$customEndDate"),
                                transitionSpec = {
                                    if (targetState.first != initialState.first) {
                                        fadeIn() togetherWith fadeOut()
                                    } else if (targetState.second > initialState.second) {
                                        slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                                    } else {
                                        slideInHorizontally(initialOffsetX = { -it }) + fadeIn() togetherWith slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                                    }.using(SizeTransform(clip = false))
                                },
                                label = "period_transition"
                            ) { target ->
                                val (targetFilter, targetTime, _) = target
                                val targetCalendar = java.util.Calendar.getInstance().apply { timeInMillis = targetTime }
                                val formattedPeriodText = when (targetFilter) {
                                    "Daily" -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(targetCalendar.time)
                                    "Weekly" -> {
                                        val firstDayOfWeek = java.util.Calendar.MONDAY
                                        val weekStart = (targetCalendar.clone() as java.util.Calendar).apply { set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek) }
                                        val weekEnd = (weekStart.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 6) }
                                        val sdfWeek = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                                        val sdfWeekYear = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                        if (weekStart.get(java.util.Calendar.YEAR) == weekEnd.get(java.util.Calendar.YEAR)) {
                                            "${sdfWeek.format(weekStart.time)} - ${sdfWeekYear.format(weekEnd.time)}"
                                        } else {
                                            "${sdfWeekYear.format(weekStart.time)} - ${sdfWeekYear.format(weekEnd.time)}"
                                        }
                                    }
                                    "Monthly" -> java.text.SimpleDateFormat("MMMM, yyyy", java.util.Locale.getDefault()).format(targetCalendar.time)
                                    "6 Months" -> {
                                        val start6 = (targetCalendar.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, -5) }
                                        val sdf6 = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                                        "${sdf6.format(start6.time)} - ${sdf6.format(targetCalendar.time)}"
                                    }
                                    "Yearly" -> java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(targetCalendar.time)
                                    "Custom" -> {
                                        if (customStartDate != null && customEndDate != null) {
                                            val sdfCustom = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                            "${sdfCustom.format(java.util.Date(customStartDate!!))} - ${sdfCustom.format(java.util.Date(customEndDate!!))}"
                                        } else {
                                            "Select Date Range"
                                        }
                                    }
                                    else -> "All Time"
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formattedPeriodText,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (targetFilter == "Custom") {
                                        Icon(
                                            imageVector = Icons.Rounded.DateRange,
                                            contentDescription = "Edit Range",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        if (activeHomeDateFilter != "Custom") {
                            IconButton(
                                onClick = {
                                    currentMonthCalendar = (currentMonthCalendar.clone() as Calendar).apply {
                                        when (activeHomeDateFilter) {
                                            "Daily" -> add(Calendar.DAY_OF_YEAR, 1)
                                            "Weekly" -> add(Calendar.WEEK_OF_YEAR, 1)
                                            "Monthly" -> add(Calendar.MONTH, 1)
                                            "6 Months" -> add(Calendar.MONTH, 6)
                                            "Yearly" -> add(Calendar.YEAR, 1)
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Next Period",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(36.dp))
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



            // LIST OF TRANSACTIONS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading) {
                    RecordsSkeletonList()
                } else if (groupedTransactions.isEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            com.expenso.app.ui.components.EmptyState(
                                icon = androidx.compose.material.icons.Icons.Rounded.Receipt,
                                title = "No Transactions",
                                message = "Click '+' below to start recording transactions.",
                                modifier = Modifier.fillParentMaxHeight()
                            )
                        }
                    }
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(listState) {
                        androidx.compose.runtime.snapshotFlow { 
                            listState.firstVisibleItemScrollOffset > 20 || listState.firstVisibleItemIndex > 0 
                        }
                        .collect { isScrolling ->
                            viewModel.setScrolling(isScrolling)
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var isFirstGroup = true
                            groupedTransactions.forEach { (dateGroup, transactionsInGroup) ->
                            val currentIsFirst = isFirstGroup
                            isFirstGroup = false

                            // Header Date Label
                            item {
                                Text(
                                    text = dateGroup,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = if (currentIsFirst) 0.dp else 10.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }

                            itemsIndexed(transactionsInGroup, key = { _, tx -> tx.id }) { idx, tx ->
                                val txKey = remember(tx) {
                                    try {
                                        val datePart = tx.transactionDate.split("T").first()
                                        val dateParts = datePart.split("-")
                                        val year = dateParts[0].toInt()
                                        val month = dateParts[1].toInt()
                                        "${tx.categoryId}-$month-$year"
                                    } catch (e: Exception) {
                                        ""
                                    }
                                }
                                val isOverBudget = remember(tx, exceededBudgets, budgets) {
                                    budgets.any { budget ->
                                        exceededBudgets.contains(budget.id) && com.expenso.app.ui.screens.isTransactionInBudget(tx, budget)
                                    }
                                }

                                AnimatedEntranceItem(index = idx) {
                                    MemoizedTransactionListItem(
                                        transaction = tx,
                                        currencySymbol = currencySymbol,
                                        isOverBudget = isOverBudget,
                                        isSelected = selectedTransactionIds.contains(tx.id),
                                        onClick = { clickedTx ->
                                            if (selectedTransactionIds.isNotEmpty()) {
                                                if (selectedTransactionIds.contains(clickedTx.id)) {
                                                    selectedTransactionIds = selectedTransactionIds - clickedTx.id
                                                } else {
                                                    selectedTransactionIds = selectedTransactionIds + clickedTx.id
                                                }
                                            } else {
                                                onEditTransaction(clickedTx) 
                                            }
                                        },
                                        onLongClick = { clickedTx ->
                                            if (selectedTransactionIds.contains(clickedTx.id)) {
                                                selectedTransactionIds = selectedTransactionIds - clickedTx.id
                                            } else {
                                                selectedTransactionIds = selectedTransactionIds + clickedTx.id
                                            }
                                        }
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
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Display Options",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                val filterOptions = listOf(
                    "Daily" to "Single day",
                    "Weekly" to "Current week",
                    "Monthly" to "Current month",
                    "6 Months" to "Last 6 months",
                    "Yearly" to "Current year",
                    "Custom" to "Select start & end"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterOptions.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { (option, description) ->
                                val isSelected = activeHomeDateFilter == option
                                Surface(
                                    onClick = {
                                        viewModel.setHomeDateFilterOption(option)
                                        if (option == "Custom") {
                                            showCustomDateRangeDialog = true
                                        } else {
                                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                                if (!sheetState.isVisible) {
                                                    showFilterSheet = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = option,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
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
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = customEndDate!!
                            cal.set(Calendar.HOUR_OF_DAY, 23)
                            cal.set(Calendar.MINUTE, 59)
                            cal.set(Calendar.SECOND, 59)
                            val finalEnd = cal.timeInMillis
                            
                            viewModel.filterDateRange.value = Pair(customStartDate, finalEnd)
                        } else {
                            viewModel.filterDateRange.value = null
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
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) },
                headline = { Text("Filter Transactions", modifier = Modifier.padding(16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * A highly-optimized, memoized wrapper for individual TransactionListItems.
 * It prevents unnecessary parent-triggered re-renders and re-paints during scroll
 * or multi-item selection by keeping lambda callback references stable across compositions.
 */
@Composable
fun MemoizedTransactionListItem(
    transaction: TransactionWithDetails,
    currencySymbol: String,
    isOverBudget: Boolean,
    isSelected: Boolean,
    onClick: (TransactionWithDetails) -> Unit,
    onLongClick: (TransactionWithDetails) -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    // Using remember(transaction.id) ensures the callback instance is completely stable,
    // only delegating to the latest lambda value at runtime when actually triggered.
    val stableOnClick = remember(transaction.id) {
        { currentOnClick(transaction) }
    }
    val stableOnLongClick = remember(transaction.id) {
        { currentOnLongClick(transaction) }
    }

    TransactionListItem(
        transaction = transaction,
        currency = currencySymbol,
        isOverBudget = isOverBudget,
        isSelected = isSelected,
        onClick = stableOnClick,
        onLongClick = stableOnLongClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionListItem(
    transaction: TransactionWithDetails,
    currency: String,
    isOverBudget: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val categoryColor = remember(transaction.categoryColor) {
        try { Color(android.graphics.Color.parseColor(transaction.categoryColor)) } catch (e: Exception) { Color.Gray }
    }
    
    val isIncome = transaction.type == "income"
    val signedAmount = if (isIncome) "+$currency${String.format("%.2f", transaction.amount)}" 
                       else "-$currency${String.format("%.2f", transaction.amount)}"
    val amountColor = if (isIncome) Color(0xFF81C784) else Color(0xFFFF7575)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Category Icon Badge - Circle shape with solid vibrant category color
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(categoryColor),
                contentAlignment = Alignment.Center
            ) {
                IconMapper.CategoryIcon(
                    icon = transaction.categoryIcon,
                    categoryName = transaction.categoryName,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.categoryName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = transaction.accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    if (isOverBudget) {
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Over Budget",
                                tint = Color(0xFFFF7575),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Over Limit",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF7575),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                val txTags = remember(transaction.tags) {
                    transaction.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
                if (txTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        txTags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount Column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = signedAmount,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = amountColor
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ShimmerBrush(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

@Composable
fun RecordsSkeletonList() {
    val shimmerBrush = ShimmerBrush()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        // Group Header 1
        item {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                    .width(100.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }
        
        items(3) {
            SkeletonTransactionItem(shimmerBrush)
        }

        // Group Header 2
        item {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                    .width(120.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }

        items(2) {
            SkeletonTransactionItem(shimmerBrush)
        }
    }
}

@Composable
fun SkeletonTransactionItem(brush: Brush) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(brush)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction Info Column
            Column(modifier = Modifier.weight(1f)) {
                // Category name
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Account name
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount Column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}
