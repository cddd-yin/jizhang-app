package com.expenso.app.ui.screens

import android.widget.Toast
import java.util.Locale
import androidx.compose.animation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush.Companion.horizontalGradient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.expenso.app.data.Account
import com.expenso.app.data.ExpensoViewModel
import com.expenso.app.ui.components.IconMapper
import com.expenso.app.ui.components.AnimatedEntranceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: ExpensoViewModel,
    onAddAccountClick: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onAddTransactionClick: (Account?) -> Unit
) {
    val context = LocalContext.current
    val accounts by viewModel.allAccounts.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedAccountForHistory by remember { mutableStateOf<Account?>(null) }
    
    BackHandler(enabled = selectedAccountForHistory != null) {
        selectedAccountForHistory = null
    }
    
    val currencySymbol = settings?.currency ?: "₹"
    val (netWorth, totalIncome, totalExpense) = remember(accounts, transactions) {
        val nw = accounts.sumOf { it.currentBalance }
        val inc = transactions.filter { it.type == "income" }.sumOf { it.amount }
        val exp = transactions.filter { it.type == "expense" }.sumOf { it.amount }
        Triple(nw, inc, exp)
    }

    val animNetWorth by androidx.compose.animation.core.animateFloatAsState(targetValue = netWorth.toFloat(), label = "netWorth", animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween())
    val animIncome by androidx.compose.animation.core.animateFloatAsState(targetValue = totalIncome.toFloat(), label = "totalIncome", animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween())
    val animExpense by androidx.compose.animation.core.animateFloatAsState(targetValue = totalExpense.toFloat(), label = "totalExpense", animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Summary
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            com.expenso.app.ui.components.ExpensoTopBar(
                title = "Expenso",
                actionIcon = Icons.Rounded.Add,
                onActionClick = onAddAccountClick,
                actionContentDescription = "Add account"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Balance Card
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.4f), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Total Balance Header and Amount Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Balance",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val netWorthColor = when {
                                netWorth < 0.0 -> Color(0xFFFF7575)
                                netWorth > 0.0 -> Color(0xFF81C784)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = formatCurrency(animNetWorth.toDouble(), currencySymbol),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = netWorthColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Side-by-Side Income and Expense displays
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Income Info
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE2F6EA).copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Income",
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatCurrency(animIncome.toDouble(), currencySymbol),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF81C784)
                                )
                            }
                        }

                        VerticalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Expense Info
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFEBEE).copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Expense",
                                    tint = Color(0xFFFF7575),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Expense",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatCurrency(animExpense.toDouble(), currencySymbol),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFFFF7575)
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)

        // ACCOUNT CARDS LIST
        if (accounts.isEmpty()) {
            com.expenso.app.ui.components.EmptyState(
                icon = androidx.compose.material.icons.Icons.Rounded.AccountBox,
                title = "No Accounts",
                message = "You haven't added any accounts yet.\nTap Add to get started.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(accounts) { idx, account ->
                    AnimatedEntranceItem(index = idx) {
                        AccountListItem(
                            account = account,
                            currency = currencySymbol,
                            onClick = { onEditAccount(account) }
                        )
                    }
                }
            }
        }
    }

    // ACCOUNT DETAILS & TRANSACTION HISTORY DIALOG
    if (selectedAccountForHistory != null) {
        val account = selectedAccountForHistory!!
        val filteredTxs = transactions.filter { it.accountId == account.id }
        var selectedTab by remember { mutableStateOf("All") }

        Dialog(
            onDismissRequest = { selectedAccountForHistory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            com.expenso.app.ui.components.ExpensoTopBar(
                                title = account.name,
                                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                                onNavigationClick = { selectedAccountForHistory = null },
                                navigationContentDescription = "Back",
                                actionIcon = Icons.Rounded.Add,
                                onActionClick = { onAddTransactionClick(account); selectedAccountForHistory = null },
                                actionContentDescription = "Add"
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Balance Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Available Balance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatCurrency(account.currentBalance, currencySymbol),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
                                    color = if (account.currentBalance < 0.0) Color(0xFFFF7575) else if (account.currentBalance > 0.0) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Incorrect balance? ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Edit",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.clickable { 
                                            onEditAccount(account)
                                            selectedAccountForHistory = null
                                        }
                                    )
                                }
                            }
                        }

                        // Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("All", "Credit", "Debit", "Adjustments").forEach { tab ->
                                Column(
                                    modifier = Modifier.clickable { selectedTab = tab },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = tab,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selectedTab == tab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (selectedTab == tab) {
                                        Box(
                                            modifier = Modifier
                                                .height(2.dp)
                                                .fillMaxWidth(0.2f)
                                                .background(MaterialTheme.colorScheme.onSurface)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }
                        }

                        val currentList = when (selectedTab) {
                            "Credit" -> filteredTxs.filter { it.type == "income" }
                            "Debit" -> filteredTxs.filter { it.type == "expense" }
                            else -> filteredTxs // Assuming 'Adjustments' would have its own logic, or just empty for now
                        }

                        // History List
                        if (currentList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No transactions",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(currentList) { idx, tx ->
                                    AnimatedEntranceItem(index = idx) {
                                        val isIncome = tx.type == "income"
                                        val amtColor = if (isIncome) Color(0xFF81C784) else Color(0xFFFF7575)
                                        val amtSign = if (isIncome) "+" else "-"
    
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                        ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    if (tx.note.isBlank() || tx.note == "Expense Entry" || tx.note == "Income Entry") tx.categoryName else tx.note,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    tx.categoryName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            Text(
                                                "$amtSign$currencySymbol${String.format("%.2f", tx.amount)}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                                                color = amtColor
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
    }
}

@Composable
fun AccountListItem(
    account: Account,
    currency: String,
    onClick: () -> Unit
) {
    val accountColor = try { Color(android.graphics.Color.parseColor(account.color)) } catch (e: Exception) { Color.Gray }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accountColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconMapper.getIcon(account.icon ?: "wallet"),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val balanceColor = when {
                    account.currentBalance < 0.0 -> Color(0xFFFF7575)
                    account.currentBalance > 0.0 -> Color(0xFF81C784)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = formatCurrency(account.currentBalance, currency),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = balanceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatCurrency(amount: Double, currency: String): String {
    val isNegative = amount < 0.0
    val absAmount = kotlin.math.abs(amount)
    val formatted = String.format(Locale.getDefault(), "%,.2f", absAmount)
    return if (isNegative) "-$currency$formatted" else "$currency$formatted"
}
