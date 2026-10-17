package com.expenso.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.expenso.app.data.*
import com.expenso.app.ui.components.IconMapper
import com.expenso.app.ui.components.ExpensoMenuItem
import com.expenso.app.ui.components.AnimatedEntranceItem
import com.expenso.app.ui.theme.AppTheme
import com.expenso.app.ui.theme.getFriendlyName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoreScreen(
    viewModel: ExpensoViewModel,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onNavigateToAddBudget: () -> Unit,
    onNavigateToAddGoal: () -> Unit,
    onNavigateToAddRecurring: () -> Unit,
    onEditSavingsGoal: (SavingsGoal) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings by viewModel.settings.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val budgets by viewModel.allBudgets.collectAsState()
    val recurringRules by viewModel.allRecurringTransactions.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()

    var showBudgetListSheet by remember { mutableStateOf(false) }
    var showScheduledListSheet by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var showImportOptions by remember { mutableStateOf(false) }
    val exportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val importSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()

    val currencySymbol = settings?.currency ?: "₹"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        com.expenso.app.ui.components.ExpensoTopBar(title = "Expenso")

        // SECTION 1: FINANCIAL MANAGEMENT HOOKS
        Text("Management Hub", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                ExpensoMenuItem(
                    icon = Icons.AutoMirrored.Rounded.List,
                    title = "Transactions",
                    subtitle = "View and manage all records",
                    iconTint = MaterialTheme.colorScheme.secondary,
                    iconBgColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    onClick = { viewModel.selectTab(0) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                
                ExpensoMenuItem(
                    icon = Icons.Rounded.Category,
                    title = "Categories",
                    subtitle = "Organize transactions",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    iconBgColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    onClick = { onNavigateToCategories() }
                )
            }
        }

        // SECTION 2: APP PREFERENCES
        Text("Preferences", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Theme Mode Choice (Sleek Horizontal Selector instead of ugly dropdown)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Appearance Theme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Adjust dark or light theme preferences", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Selector Row
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
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentMode = settings?.themeMode ?: "system"
                            listOf("system", "light", "dark").forEach { mode ->
                                val isSelected = currentMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            viewModel.updateSettings(mode, settings?.currency ?: "₹", settings?.notificationsEnabled ?: true)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.uppercase(),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Theme Color Palette Selection
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Theme Color Palette", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Select your preferred color scheme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentPalette = settings?.colorPalette
                        val activePalette = when (currentPalette) {
                            "Cosmic Slate", null -> "Default"
                            else -> currentPalette
                        }
                        val currentPalettes = listOf(
                            Triple("Default", Color(0xFF005FAF), Color(0xFF60A5FA)),
                            Triple("Deep Indigo", Color(0xFF4255A8), Color(0xFFBAC3FF)),
                            Triple("Lavender Dream", Color(0xFF6750A4), Color(0xFFD0BCFF))
                        )
                        val newPalettes = AppTheme.values().map { 
                            Triple(it.getFriendlyName(), it.primaryLight, it.primaryDark)
                        }
                        val palettes = currentPalettes + newPalettes
                        
                        val chunks = palettes.chunked(4)
                        chunks.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (paletteName, lightColor, darkColor) ->
                                    val isSelected = activePalette == paletteName
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                    ) {
                                        Surface(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.updateColorPalette(paletteName)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Large color preview icon
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                ) {
                                                    // Left half: Light theme primary color
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(0.5f)
                                                            .background(lightColor)
                                                            .align(Alignment.CenterStart)
                                                    )
                                                    // Right half: Dark theme primary color
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(0.5f)
                                                            .background(darkColor)
                                                            .align(Alignment.CenterEnd)
                                                    )
                                                }
                                                
                                                // Theme name below the icon
                                                Text(
                                                    text = paletteName,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // Spacer placeholders to maintain grid columns
                                if (rowItems.size < 4) {
                                    repeat(4 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Currency Symbol Pick
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Currency", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Select active coin visual symbol", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    
                    Button(
                        onClick = { showCurrencyDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(settings?.currency ?: "₹", fontWeight = FontWeight.Bold)
                    }
                }

            }
        }

        // SECTION 3: SYSTEM PORTABILITY & DATA BACKUPS
        Text("System Portability & Backups", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                // Export Data
                ExpensoMenuItem(
                    icon = Icons.Rounded.Share,
                    title = "Export Data",
                    subtitle = "Export to CSV spreadsheet or PDF document",
                    onClick = { showExportOptions = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Import Data
                ExpensoMenuItem(
                    icon = Icons.Rounded.FileOpen,
                    title = "Import Data",
                    subtitle = "Import from CSV spreadsheet",
                    onClick = { showImportOptions = true }
                )
            }
        }


        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showExportOptions) {
        ModalBottomSheet(
            onDismissRequest = { showExportOptions = false },
            sheetState = exportSheetState,
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Export Data",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // CSV Spreadsheet option (.csv)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        coroutineScope.launch {
                            exportSheetState.hide()
                            showExportOptions = false
                            onExportCsv()
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "CSV Spreadsheet (.csv)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // PDF Document option (.pdf)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        coroutineScope.launch {
                            exportSheetState.hide()
                            showExportOptions = false
                            onExportPdf()
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PDF Document Statement (.pdf)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    if (showImportOptions) {
        ModalBottomSheet(
            onDismissRequest = { showImportOptions = false },
            sheetState = importSheetState,
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Import Data",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // CSV Spreadsheet option (.csv)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        coroutineScope.launch {
                            importSheetState.hide()
                            showImportOptions = false
                            onImportCsv()
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Import from CSV (.csv)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    // Searchable Currency Dialog
    if (showCurrencyDialog) {
        Dialog(onDismissRequest = { showCurrencyDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 500.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Select Default Currency",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    var searchCurQuery by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = searchCurQuery,
                        onValueChange = { searchCurQuery = it },
                        placeholder = { Text("Search currency...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        shape = CircleShape,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                    
                    val currencyOptions = listOf(
                        Pair("$", "USD - US Dollar"),
                        Pair("₹", "INR - Indian Rupee"),
                        Pair("€", "EUR - Euro"),
                        Pair("£", "GBP - British Pound"),
                        Pair("¥", "JPY - Japanese Yen"),
                        Pair("₩", "KRW - South Korean Won"),
                        Pair("A$", "AUD - Australian Dollar"),
                        Pair("C$", "CAD - Canadian Dollar"),
                        Pair("CHF", "CHF - Swiss Franc"),
                        Pair("₺", "TRY - Turkish Lira"),
                        Pair("₽", "RUB - Russian Ruble"),
                        Pair("R$", "BRL - Brazilian Real"),
                        Pair("฿", "THB - Thai Baht"),
                        Pair("₫", "VND - Vietnamese Dong"),
                        Pair("₪", "ILS - Israeli Shekel"),
                        Pair("₱", "PHP - Philippine Peso"),
                        Pair("RM", "MYR - Malaysian Ringgit"),
                        Pair("Rp", "IDR - Indonesian Rupiah"),
                        Pair("kr", "SEK - Swedish Krona"),
                        Pair("R", "ZAR - South African Rand"),
                        Pair("Ksh", "KES - Kenyan Shilling")
                    )
                    
                    val filteredCurrencies = remember(searchCurQuery) {
                        currencyOptions.filter {
                            it.second.lowercase().contains(searchCurQuery.lowercase()) ||
                            it.first.lowercase().contains(searchCurQuery.lowercase())
                        }
                    }
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredCurrencies) { option ->
                            val isSelected = settings?.currency == option.first
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        viewModel.updateSettings(
                                            settings?.themeMode ?: "system",
                                            option.first,
                                            settings?.notificationsEnabled ?: true
                                        )
                                        showCurrencyDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        option.first,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        option.second,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { showCurrencyDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // LIST DIALOG FOR BUDGETS
    if (showBudgetListSheet) {
        Dialog(
            onDismissRequest = { showBudgetListSheet = false },
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
                                title = "Configured Budgets",
                                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                                onNavigationClick = { showBudgetListSheet = false },
                                navigationContentDescription = "Back",
                                actionIcon = Icons.Rounded.Add,
                                onActionClick = {
                                    onNavigateToAddBudget()
                                    showBudgetListSheet = false
                                },
                                actionContentDescription = "New Budget"
                            )
                        }
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = innerPadding.calculateBottomPadding() + 100.dp,
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (budgets.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Rounded.DonutLarge, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No Budgets Configured", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                    Text("Set limits to track your spending", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                        itemsIndexed(budgets) { idx, budget ->
                            val matchingCat = categories.firstOrNull { it.id == budget.categoryId }
                            val labelName = matchingCat?.name ?: "Overall"
                            val color = try { Color(android.graphics.Color.parseColor(matchingCat?.color ?: "#009688")) } catch (e: Exception) { Color.Gray }

                            val spentAmount = transactions.filter {
                                com.expenso.app.ui.screens.isTransactionInBudget(it, budget)
                            }.sumOf { it.amount }
                            val progress = if (budget.budgetAmount > 0) (spentAmount / budget.budgetAmount).toFloat().coerceIn(0f, 1f) else 0f
                            val isOverBudget = spentAmount > budget.budgetAmount

                            AnimatedEntranceItem(index = idx) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(color),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    IconMapper.CategoryIcon(
                                                        icon = matchingCat?.icon ?: "trending_down",
                                                        categoryName = matchingCat?.name ?: "",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column {
                                                    Text(if (!budget.budgetName.isNullOrBlank()) budget.budgetName!! else labelName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                                    val monthNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"); val sdf = java.text.SimpleDateFormat("dd MMM ''yy", java.util.Locale.getDefault())
                                                    val dateStr = if (budget.startDate != null && budget.endDate != null) {
                                                        if (budget.budgetType == "MONTHLY") "${if (budget.month in 1..12) monthNames[budget.month - 1] else "Unknown"}, ${budget.year}" else "${sdf.format(java.util.Date(budget.startDate))} - ${sdf.format(java.util.Date(budget.endDate))}"
                                                    } else {
                                                        if (budget.budgetType == "MONTHLY") "${if (budget.month in 1..12) monthNames[budget.month - 1] else "Unknown"}, ${budget.year}" else "Budget"
                                                    }
                                                    val hasCustomName = !budget.budgetName.isNullOrBlank()
                                                    val nameSuffix = if (hasCustomName && labelName != "Overall") " • $labelName" else ""
                                                    Text("Limit: $currencySymbol${budget.budgetAmount}$nameSuffix • $dateStr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium)
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteBudget(budget)
                                                    viewModel.showSnackbar("Budget deleted")
                                                },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                            ) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(16.dp)),
                                            color = if (isOverBudget) MaterialTheme.colorScheme.error else color,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Spent: $currencySymbol${String.format("%.1f", spentAmount)}", style = MaterialTheme.typography.bodySmall, color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                            Text("Remaining: $currencySymbol${String.format("%.1f", (budget.budgetAmount - spentAmount).coerceAtLeast(0.0))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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

    // LIST DIALOG FOR SCHEDULED TXNS
    if (showScheduledListSheet) {
        Dialog(
            onDismissRequest = { showScheduledListSheet = false },
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
                                title = "Scheduled Txns",
                                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                                onNavigationClick = { showScheduledListSheet = false },
                                navigationContentDescription = "Back",
                                actionIcon = Icons.Rounded.Add,
                                onActionClick = {
                                    onNavigateToAddRecurring()
                                    showScheduledListSheet = false
                                },
                                actionContentDescription = "New Scheduled"
                            )
                        }
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = innerPadding.calculateBottomPadding() + 100.dp,
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (recurringRules.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Rounded.Update, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No Scheduled Txns", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                    Text("Automate your repeating expenses", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                        itemsIndexed(recurringRules) { idx, rule ->
                            val matchingCat = categories.firstOrNull { it.id == rule.categoryId }
                            val color = try { Color(android.graphics.Color.parseColor(matchingCat?.color ?: "#60A5FA")) } catch (e: Exception) { Color.Gray }
                            val isIncome = rule.type == "income"

                            AnimatedEntranceItem(index = idx) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(color),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    IconMapper.CategoryIcon(
                                                        icon = matchingCat?.icon ?: "trending_up",
                                                        categoryName = matchingCat?.name ?: "",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column {
                                                    Text(rule.note.ifEmpty { matchingCat?.name ?: "Scheduled" }, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("${rule.frequency.uppercase()} • Next: ${rule.nextExecutionDate}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium)
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteRecurringRule(rule)
                                                    viewModel.showSnackbar("Scheduled Txn deleted")
                                                },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                            ) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isIncome) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(if (isIncome) "Income" else "Expense", color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("${if (isIncome) "+" else "-"}$currencySymbol${String.format("%.1f", rule.amount)}", color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
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
