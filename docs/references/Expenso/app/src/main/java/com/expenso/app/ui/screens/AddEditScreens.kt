package com.expenso.app.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.expenso.app.ui.theme.spacing
import com.expenso.app.ui.theme.GoogleSansFlexFontFamily
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.expenso.app.data.*
import com.expenso.app.ui.components.IconMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- EXPESSION EVALUATOR ---
private fun evaluateMathExpression(str: String): Double {
    return object : Any() {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.code)) {
                    var y = parseTerm(isAddingSub = true, baseValue = x)
                    x += y
                } else if (eat('-'.code)) {
                    var y = parseTerm(isAddingSub = true, baseValue = x)
                    x -= y
                } else return x
            }
        }

        fun parseTerm(isAddingSub: Boolean = false, baseValue: Double = 0.0): Double {
            var x = parseFactor()
            if (eat('%'.code)) {
                if (isAddingSub) {
                    x = baseValue * (x / 100.0)
                } else {
                    x /= 100.0
                }
            }
            while (true) {
                if (eat('*'.code)) {
                    var y = parseFactor()
                    if (eat('%'.code)) y /= 100.0
                    x *= y
                } else if (eat('/'.code)) {
                    var y = parseFactor()
                    if (eat('%'.code)) y /= 100.0
                    if (y == 0.0) throw ArithmeticException("Division by zero")
                    x /= y
                } else if (ch >= '0'.code && ch <= '9'.code || ch == '('.code) {
                    var y = parseFactor()
                    if (eat('%'.code)) y /= 100.0
                    x *= y
                } else return x
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) {
                while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
                val numStr = str.substring(startPos, pos)
                x = numStr.toDoubleOrNull() ?: 0.0
            } else {
                throw RuntimeException("Unexpected: " + ch.toChar())
            }

            return x
        }
    }.parse()
}

// --- TRANSCTION FORM ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionForm(
    viewModel: ExpensoViewModel,
    transactionToEdit: TransactionWithDetails? = null,
    onNavigateToAddCategory: () -> Unit = {},
    onNavigateToAddAccount: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val accounts by viewModel.allAccounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currencySymbol = settings?.currency ?: "₹"
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkAppTheme = when (settings?.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }

    val localSnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isExpense by remember(transactionToEdit) { mutableStateOf(transactionToEdit?.type != "income") }
    var isTransfer by remember(transactionToEdit) { mutableStateOf(transactionToEdit?.note?.startsWith("[Transfer]") == true) }
    var amountError by remember(transactionToEdit) { mutableStateOf<String?>(null) }
    var amountTextFieldValue by remember(transactionToEdit) {
        val initialText = transactionToEdit?.amount?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        } ?: ""
        mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(initialText.length)))
    }
    var amountStr = amountTextFieldValue.text

    var note by remember(transactionToEdit) {
        val rawNote = transactionToEdit?.note?.replace("[Transfer] ", "") ?: ""
        val isDefault = rawNote == "Expense Entry" || rawNote == "Income Entry" || rawNote == transactionToEdit?.categoryName
        mutableStateOf(if (isDefault) "" else rawNote)
    }
    var selectedDate by remember(transactionToEdit) {
        mutableStateOf(
            transactionToEdit?.transactionDate?.split("T")?.firstOrNull()
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }
    var selectedTime by remember(transactionToEdit) {
        mutableStateOf(
            transactionToEdit?.transactionDate?.split("T")?.getOrNull(1)?.substring(0, 5)
            ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
    }

    var selectedAccountId by remember(transactionToEdit) {
        mutableStateOf(transactionToEdit?.accountId ?: 0)
    }
    var selectedCategoryId by remember(transactionToEdit) {
        mutableStateOf(transactionToEdit?.categoryId ?: 0)
    }

    LaunchedEffect(selectedAccountId, selectedCategoryId) {
        amountError = null
    }

    val activeTags = remember(transactionToEdit) { 
        mutableStateListOf<String>().apply {
            transactionToEdit?.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
                addAll(it)
            }
        }
    }

    // Dynamic Quick Adding and Selection triggers
    var showAccountSelectionDialog by remember { mutableStateOf(false) }
    var showCategorySelectionDialog by remember { mutableStateOf(false) }

    // Filter categories corresponding to active selection (Income or Expense)
    val filteredCategories = categories.filter {
        it.type == (if (isExpense) "expense" else "income")
    }

    // Auto-update default category if selected is invalid or mismatch
    LaunchedEffect(isExpense, categories) {
        if (transactionToEdit == null || transactionToEdit.type != (if (isExpense) "expense" else "income")) {
            selectedCategoryId = 0
        }
    }

    // Auto-select newly added items
    var previousAccountsSize by remember { mutableStateOf(accounts.size) }
    LaunchedEffect(accounts) {
        if (accounts.size > previousAccountsSize) {
            accounts.maxByOrNull { it.id }?.let {
                selectedAccountId = it.id
            }
        }
        previousAccountsSize = accounts.size
    }

    var previousCategoriesSize by remember { mutableStateOf(categories.size) }
    LaunchedEffect(categories) {
        if (categories.size > previousCategoriesSize) {
            filteredCategories.maxByOrNull { it.id }?.let {
                selectedCategoryId = it.id
            }
        }
        previousCategoriesSize = categories.size
    }

    val themeColor = MaterialTheme.colorScheme.primary

    fun onKeyPress(key: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        amountError = null
        var text = amountTextFieldValue.text
        var selStart = amountTextFieldValue.selection.start
        if (selStart < 0) selStart = text.length

        fun insertText(newStr: String) {
            text = text.substring(0, selStart) + newStr + text.substring(selStart)
            selStart += newStr.length
        }

        fun deleteBack() {
            if (selStart > 0) {
                text = text.substring(0, selStart - 1) + text.substring(selStart)
                selStart--
            }
        }

        when (key) {
            "AC", "C" -> {
                text = ""
                selStart = 0
            }
            "⌫" -> {
                if (text.isNotEmpty()) {
                    deleteBack()
                }
            }
            "=" -> {
                try {
                    var mathExpr = text.replace("×", "*").replace("x", "*").replace("÷", "/").replace("−", "-")
                    while (mathExpr.isNotEmpty() && (mathExpr.last() == '+' || mathExpr.last() == '-' || mathExpr.last() == '*' || mathExpr.last() == '/')) {
                        mathExpr = mathExpr.dropLast(1)
                    }
                    val openCount = mathExpr.count { it == '(' }
                    val closeCount = mathExpr.count { it == ')' }
                    if (openCount > closeCount) {
                        mathExpr += ")".repeat(openCount - closeCount)
                    }

                    if (mathExpr.isNotEmpty()) {
                        val res = evaluateMathExpression(mathExpr)
                        if (!res.isNaN() && !res.isInfinite()) {
                            text = if (res % 1 == 0.0) res.toInt().toString() else String.format(Locale.US, "%.2f", res)
                            selStart = text.length
                        } else {
                            coroutineScope.launch {
                                localSnackbarHostState.showSnackbar("Invalid Expression")
                            }
                        }
                    }
                } catch (e: Exception) {
                    coroutineScope.launch {
                        localSnackbarHostState.showSnackbar("Invalid Expression")
                    }
                }
            }
            "+", "−", "-" -> {
                if (text.isNotEmpty()) {
                    val prevChar = if (selStart > 0) text[selStart - 1] else ' '
                    if (prevChar == '+' || prevChar == '−' || prevChar == '-' || prevChar == '×' || prevChar == 'x' || prevChar == '÷') {
                        deleteBack()
                        insertText(key)
                    } else {
                        insertText(key)
                    }
                } else {
                    insertText(key)
                }
            }
            "×", "x", "÷" -> {
                if (text.isNotEmpty()) {
                    val prevChar = if (selStart > 0) text[selStart - 1] else ' '
                    if (prevChar == '+' || prevChar == '−' || prevChar == '-' || prevChar == '×' || prevChar == 'x' || prevChar == '÷') {
                        deleteBack()
                        insertText(key)
                    } else if (prevChar != '(') {
                        insertText(key)
                    }
                }
            }
            "%" -> {
                if (text.isNotEmpty()) {
                    val prevChar = if (selStart > 0) text[selStart - 1] else ' '
                    if (prevChar.isDigit() || prevChar == ')' || prevChar == '%') {
                        insertText(key)
                    }
                }
            }
            "(", ")" -> {
                insertText(key)
            }
            "( )" -> {
                val openCount = text.substring(0, selStart).count { it == '(' }
                val closeCount = text.substring(0, selStart).count { it == ')' }
                insertText(if (openCount > closeCount) ")" else "(")
            }
            "." -> {
                val parts = text.substring(0, selStart).split('+', '−', '-', '×', 'x', '÷', '(', ')', '%')
                val lastPart = parts.lastOrNull() ?: ""
                if (!lastPart.contains('.')) {
                    insertText(if (lastPart.isEmpty()) "0." else ".")
                }
            }
            else -> {
                if (key == "0" && text == "0") return
                if (text == "0" && key != ".") {
                    text = key
                    selStart = key.length
                } else {
                    insertText(key)
                }
            }
        }
        amountTextFieldValue = TextFieldValue(text = text, selection = TextRange(selStart))
    }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { com.expenso.app.ui.components.SwipeableSnackbarHost(localSnackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = CircleShape
                        )
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title or Delete Action
                if (transactionToEdit != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                shape = CircleShape
                            )
                            .clickable {
                                viewModel.deleteTransaction(transactionToEdit.id)
                                coroutineScope.launch { localSnackbarHostState.showSnackbar("Transaction Deleted") }
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Done Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(themeColor)
                        .clickable {
                            try {
                                val mathExpr = amountTextFieldValue.text.replace("×", "*").replace("x", "*").replace("÷", "/").replace("−", "-")
                                if (mathExpr.isNotEmpty() && (mathExpr.contains("+") || mathExpr.contains("-") || mathExpr.contains("*") || mathExpr.contains("/"))) {
                                    val res = evaluateMathExpression(mathExpr)
                                    if (!res.isNaN() && !res.isInfinite()) {
                                        val finalStr = if (res % 1 == 0.0) res.toInt().toString() else String.format(Locale.US, "%.2f", res)
                                        amountTextFieldValue = TextFieldValue(text = finalStr, selection = TextRange(finalStr.length))
                                    } else {
                                        coroutineScope.launch {
                                            localSnackbarHostState.showSnackbar("Invalid Expression")
                                        }
                                        return@clickable
                                    }
                                }
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    localSnackbarHostState.showSnackbar("Invalid Expression")
                                }
                                return@clickable
                            }

                            val amount = amountTextFieldValue.text.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                coroutineScope.launch {
                                    localSnackbarHostState.showSnackbar("Please enter a valid amount > 0")
                                }
                                return@clickable
                            }
                            if (selectedAccountId == 0 || selectedAccountId == null) {
                                coroutineScope.launch {
                                    localSnackbarHostState.showSnackbar("Please select an account")
                                }
                                return@clickable
                            }
                            if (selectedCategoryId == 0 || selectedCategoryId == null) {
                                coroutineScope.launch {
                                    localSnackbarHostState.showSnackbar("Please select a category")
                                }
                                return@clickable
                            }

                            val saveType = when {
                                isTransfer -> "expense"
                                isExpense -> "expense"
                                else -> "income"
                            }
                            val finalNote = when {
                                isTransfer -> "[Transfer] " + (note.takeIf { it.isNotBlank() } ?: "Transfer Entry")
                                else -> note.trim()
                            }
                            val finalDateStr = "${selectedDate}T${selectedTime}:00"
                            val tagsString = activeTags.joinToString(",")

                            if (transactionToEdit == null) {
                                viewModel.insertTransaction(
                                    amount = amount,
                                    type = saveType,
                                    categoryId = selectedCategoryId,
                                    accountId = selectedAccountId,
                                    note = finalNote,
                                    dateStr = finalDateStr,
                                    tags = tagsString
                                )
                            } else {
                                viewModel.updateTransaction(
                                    id = transactionToEdit.id,
                                    amount = amount,
                                    type = saveType,
                                    categoryId = selectedCategoryId,
                                    accountId = selectedAccountId,
                                    note = finalNote,
                                    dateStr = finalDateStr,
                                    tags = tagsString
                                )
                            }
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scrollable Top Container
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Type Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = CircleShape
                        )
                        .padding(4.dp)
                ) {
                    // EXPENSE BUTTON
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                if (isExpense) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .clickable {
                                isExpense = true
                                isTransfer = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = if (isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "EXPENSE",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // INCOME BUTTON
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                if (!isExpense) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .clickable {
                                isExpense = false
                                isTransfer = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = null,
                                tint = if (!isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "INCOME",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (!isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ACCOUNT & CATEGORY SELECTORS SIDE-BY-SIDE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Account selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val selectedAccount = accounts.find { it.id == selectedAccountId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showAccountSelectionDialog = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val accountColor = selectedAccount?.color?.let { try { Color(android.graphics.Color.parseColor(it)) } catch(e: Exception){ themeColor } } ?: themeColor
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(accountColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = selectedAccount?.let { IconMapper.getIcon(it.icon) } ?: Icons.Rounded.Wallet,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = selectedAccount?.name ?: "Select Account",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Category selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val selectedCategory = categories.find { it.id == selectedCategoryId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showCategorySelectionDialog = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val categoryColor = selectedCategory?.color?.let { try { Color(android.graphics.Color.parseColor(it)) } catch(e: Exception) { themeColor } } ?: themeColor
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedCategory != null) {
                                    IconMapper.CategoryIcon(
                                        icon = selectedCategory!!.icon,
                                        categoryName = selectedCategory!!.name,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.LocalOffer,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = selectedCategory?.name ?: "Select Category",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Date // Date // DATE & TIME ROW (Moved up for cleaner visual layout) Time Time
                val displayDate = remember(selectedDate) {
                    try {
                        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val dateObj = sdfInput.parse(selectedDate)
                        if (dateObj != null) {
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(dateObj)
                        } else {
                            selectedDate
                        }
                    } catch (e: Exception) {
                        selectedDate
                    }
                }

                val displayTime = remember(selectedTime) {
                    try {
                        val sdfInput = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val dateObj = sdfInput.parse(selectedTime)
                        if (dateObj != null) {
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(dateObj)
                        } else {
                            selectedTime
                        }
                    } catch (e: Exception) {
                        selectedTime
                    }
                }

                // Date // Date // DATE & TIME ROW (Moved up for cleaner visual layout) Time Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Date",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val cal = Calendar.getInstance()
                                    val dateParts = selectedDate.split("-")
                                    if (dateParts.size == 3) {
                                        cal.set(Calendar.YEAR, dateParts[0].toInt())
                                        cal.set(Calendar.MONTH, dateParts[1].toInt() - 1)
                                        cal.set(Calendar.DAY_OF_MONTH, dateParts[2].toInt())
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d -> selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d) },
                                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(themeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = displayDate,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Time selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Time",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val cal = Calendar.getInstance()
                                    val timeParts = selectedTime.split(":")
                                    if (timeParts.size == 2) {
                                        cal.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                                        cal.set(Calendar.MINUTE, timeParts[1].toInt())
                                    }
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, hour, minute -> selectedTime = String.format("%02d:%02d", hour, minute) },
                                        cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(themeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccessTime,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = displayTime,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // NOTES INPUT
                var isNotesFocused by remember { mutableStateOf(false) }
                val notesScrollState = rememberScrollState()
                var notesLineCount by remember { mutableStateOf(1) }
                
                LaunchedEffect(notesScrollState.maxValue) {
                    notesScrollState.scrollTo(notesScrollState.maxValue)
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            2.dp,
                            if (isNotesFocused) themeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    SelectionContainer {
                        androidx.compose.foundation.text.BasicTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .padding(end = 12.dp)
                                .verticalScroll(notesScrollState)
                                .onFocusChanged { isNotesFocused = it.isFocused },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(themeColor),
                            onTextLayout = { notesLineCount = it.lineCount },
                            decorationBox = { innerTextField ->
                                if (note.isEmpty()) {
                                    Text("Add notes", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyLarge)
                                }
                                innerTextField()
                            }
                        )
                    }
                    
                    // Custom Scrollbar
                    if (notesScrollState.maxValue > 0 && notesLineCount >= 3) {
                        val scrollbarHeight = 32.dp
                        val scrollRatio = if (notesScrollState.maxValue > 0) notesScrollState.value.toFloat() / notesScrollState.maxValue else 0f
                        
                        BoxWithConstraints(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(vertical = 16.dp, horizontal = 4.dp)
                                .fillMaxHeight()
                                .width(4.dp)
                        ) {
                            val trackHeight = maxHeight - scrollbarHeight
                            val scrollbarY = trackHeight * scrollRatio
                            
                            Box(
                                modifier = Modifier
                                    .offset(y = scrollbarY)
                                    .width(4.dp)
                                    .height(scrollbarHeight)
                                    .background(themeColor.copy(alpha = 0.5f), CircleShape)
                            )
                        }
                    }
                }


            }

            // Fixed Bottom Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // AMOUNT DISPLAY
                val scrollState = rememberScrollState()
                LaunchedEffect(amountTextFieldValue.text) {
                    scrollState.scrollTo(scrollState.maxValue)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .background(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            BorderStroke(2.dp, if (amountError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontFamily = GoogleSansFlexFontFamily,
                            fontSize = 38.sp
                        ),
                        color = if (isDarkAppTheme) Color.White else Color.Black,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        val textStr = if (amountTextFieldValue.text.isEmpty()) "\u200B" else amountTextFieldValue.text
                    val sel = amountTextFieldValue.selection.start.coerceIn(0, textStr.length)
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    
                    var cursorVisible by remember { mutableStateOf(true) }
                    LaunchedEffect(amountTextFieldValue.selection, amountTextFieldValue.text) {
                        cursorVisible = true
                        while(true) {
                            delay(500)
                            cursorVisible = !cursorVisible
                        }
                    }

                    SelectionContainer {
                        Text(
                            text = textStr,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFamily = GoogleSansFlexFontFamily,
                                fontSize = 38.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .horizontalScroll(scrollState)
                                .drawWithContent {
                                    drawContent()
                                    if (cursorVisible) {
                                        textLayoutResult?.let { layout ->
                                            val cursorRect = layout.getCursorRect(sel)
                                            val cursorX = cursorRect.left.roundToInt().toFloat()
                                            val cursorWidth = 2.dp.toPx().roundToInt().toFloat().coerceAtLeast(1f)
                                            drawRect(
                                                color = themeColor,
                                                topLeft = Offset(cursorX, cursorRect.top),
                                                size = androidx.compose.ui.geometry.Size(cursorWidth, cursorRect.bottom - cursorRect.top)
                                            )
                                        } ?: run {
                                            // Draw cursor even if text is empty
                                            val cursorWidth = 2.dp.toPx().roundToInt().toFloat().coerceAtLeast(1f)
                                            drawRect(
                                                color = themeColor,
                                                topLeft = Offset(0f, 0f),
                                                size = androidx.compose.ui.geometry.Size(cursorWidth, size.height)
                                            )
                                        }
                                    }
                                }
                                .padding(end = 12.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { pos ->
                                        textLayoutResult?.let { layout ->
                                            val offset = layout.getOffsetForPosition(pos)
                                            amountTextFieldValue = amountTextFieldValue.copy(selection = TextRange(offset))
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        textLayoutResult?.let { layout ->
                                            val offset = layout.getOffsetForPosition(change.position)
                                            amountTextFieldValue = amountTextFieldValue.copy(selection = TextRange(offset))
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            // KEYPAD GRID
                val keypadKeys = listOf(
                    listOf("AC", "( )", "%", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "−"),
                    listOf("1", "2", "3", "+"),
                    listOf("0", ".", "⌫", "=")
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keypadKeys.forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { key ->
                                val isLightBlue = key in listOf("AC", "( )", "%", "÷", "×", "−", "+")
                                val isEqual = key == "="

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            color = if (isLightBlue) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else if (isEqual) {
                                                themeColor
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                            shape = CircleShape
                                        )
                                        .border(
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            shape = CircleShape
                                        )
                                        .clip(CircleShape)
                                        .clickable { onKeyPress(key) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "⌫") {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                            contentDescription = "Backspace",
                                            tint = if (isDarkAppTheme) Color.White else Color.Black,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Normal,
                                                fontFamily = GoogleSansFlexFontFamily,
                                                fontSize = 32.sp
                                            ),
                                            color = if (isEqual) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                if (isDarkAppTheme) Color.White else Color.Black
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

    // --- BOTTOM SHEET OVERLAYS FOR CUSTOM ACCOUNT & CATEGORY SELECTORS ---
    if (showAccountSelectionDialog) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSelectionDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.25f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                val accountGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = accountGridState,
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .gridScrollbar(accountGridState, color = themeColor.copy(alpha = 0.4f))
                ) {
                    gridItems(accounts) { account: Account ->
                        val isSelected = selectedAccountId == account.id
                        val accountColor = try {
                            Color(android.graphics.Color.parseColor(account.color))
                        } catch (e: Exception) {
                            themeColor
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) accountColor.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    selectedAccountId = account.id
                                    showAccountSelectionDialog = false
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(accountColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = IconMapper.getIcon(account.icon),
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FilledTonalButton(
                    onClick = {
                        showAccountSelectionDialog = false
                        onNavigateToAddAccount()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = themeColor.copy(alpha = 0.15f),
                        contentColor = themeColor
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Account", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCategorySelectionDialog) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySelectionDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.25f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Category",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                val categoryGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = categoryGridState,
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .gridScrollbar(categoryGridState, color = themeColor.copy(alpha = 0.4f))
                ) {
                    gridItems(filteredCategories) { category: Category ->
                        val isSelected = selectedCategoryId == category.id
                        val categoryColor = try {
                            Color(android.graphics.Color.parseColor(category.color))
                        } catch (e: Exception) {
                            themeColor
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) categoryColor.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    selectedCategoryId = category.id
                                    showCategorySelectionDialog = false
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                IconMapper.CategoryIcon(
                                    icon = category.icon,
                                    categoryName = category.name,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FilledTonalButton(
                    onClick = {
                        showCategorySelectionDialog = false
                        onNavigateToAddCategory()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = themeColor.copy(alpha = 0.15f),
                        contentColor = themeColor
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Category", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- ACCOUNT FORM ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountForm(
    viewModel: ExpensoViewModel,
    accountToEdit: Account? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val types by viewModel.accountTypes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currencySymbol = settings?.currency ?: "₹"
    
    var name by remember(accountToEdit) { mutableStateOf(accountToEdit?.name ?: "") }
    var type by remember(accountToEdit) { mutableStateOf(accountToEdit?.type ?: "Cash") }
    var openingBalStr by remember(accountToEdit) { mutableStateOf(accountToEdit?.openingBalance?.let { if (it == 0.0) "0.00" else it.toString() } ?: "0.00") }
    var selectedIcon by remember(accountToEdit) { mutableStateOf(accountToEdit?.icon ?: "wallet") }
    var selectedColor by remember(accountToEdit) { mutableStateOf(accountToEdit?.color ?: "#CAFFBF") }

    var showManageTypesDialog by remember { mutableStateOf(false) }

    // Check if selected icon is standard
    val isStandardIconSelected = IconMapper.groupedIcons.values.flatten().contains(selectedIcon.lowercase())
    val localSnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(types) {
        if (type.isEmpty() && types.isNotEmpty()) {
            type = types.first()
        }
    }

    Scaffold(
        snackbarHost = { com.expenso.app.ui.components.SwipeableSnackbarHost(localSnackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = CircleShape
                        )
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (accountToEdit == null) "Add account" else "Edit account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (accountToEdit != null) {
                    IconButton(
                        onClick = {
                            viewModel.deleteAccount(
                                account = accountToEdit,
                                onSuccess = {
                                    coroutineScope.launch { localSnackbarHostState.showSnackbar("Account Deleted") }
                                    onDismiss()
                                },
                                onFailure = { errorMsg ->
                                    coroutineScope.launch { localSnackbarHostState.showSnackbar(errorMsg) }
                                }
                            )
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                        if (name.isBlank()) {
                            coroutineScope.launch { localSnackbarHostState.showSnackbar("Please enter an account name") }
                            return@clickable
                        }
                        val openingBal = openingBalStr.toDoubleOrNull() ?: 0.0
                        val accountName = name
                        
                        if (accountToEdit == null) {
                            viewModel.insertAccount(accountName, type, openingBal, selectedColor, selectedIcon)
                            coroutineScope.launch { localSnackbarHostState.showSnackbar("Account Saved") }
                        } else {
                            // Keep balance math
                            val diff = openingBal - accountToEdit.openingBalance
                            val updatedCurrent = accountToEdit.currentBalance + diff
                            viewModel.updateAccount(accountToEdit.id, accountName, type, openingBal, updatedCurrent, selectedColor, selectedIcon)
                            coroutineScope.launch { localSnackbarHostState.showSnackbar("Account Updated") }
                        }
                        onDismiss()
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Save Account",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // PREVIEW ROW (Icon circle + Balance input)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val colorObj = try {
                    Color(android.graphics.Color.parseColor(selectedColor))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(colorObj, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconMapper.getIcon(selectedIcon),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Account Name",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = {
                            Text(
                                "Account Name",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // OPENING BALANCE
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Opening Balance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                OutlinedTextField(
                    value = openingBalStr,
                    onValueChange = { openingBalStr = it },
                    prefix = { 
                        Text(
                            text = "$currencySymbol ", 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                        ) 
                    },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ACCOUNT COLOR SECTION (Essential)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Account color",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                val chunks = IconMapper.availableColors.chunked(9)
                chunks.forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowColors.forEach { hexColor ->
                            val isSelected = selectedColor.lowercase() == hexColor.lowercase()
                            val circleColor = try {
                                Color(android.graphics.Color.parseColor(hexColor))
                            } catch (e: Exception) {
                                Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(circleColor)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                    .clickable { selectedColor = hexColor },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ACCOUNT ICON SECTION (Essential)
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Account icon",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                val essentialIcons = listOf(
                    "wallet", "credit_card", "savings", "payments", "account_balance",
                    "account_balance_wallet", "account_box", "account_circle", "assured_workload", "business_center",
                    "card_membership", "store",
                    "upi", "online_payment", "currency_exchange", "receipt_long",
                    "rupee", "price_check", "request_quote", "calculate", "pie_chart",
                    "analytics", "point_of_sale"
                )
                
                val iconChunks = essentialIcons.chunked(5)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    iconChunks.forEach { rowIcons ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowIcons.forEach { iconName ->
                                val isSelected = selectedIcon.lowercase() == iconName.lowercase()
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .border(if (isSelected) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)), CircleShape)
                                        .clickable { selectedIcon = iconName },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIcon(iconName),
                                        contentDescription = iconName,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            repeat(5 - rowIcons.size) {
                                Spacer(modifier = Modifier.size(56.dp))
                            }
                        }
                    }
                }
            }

        }
    }

    // --- DIALOG FOR MANAGING ACCOUNT TYPES ---
    if (showManageTypesDialog) {
        var newTypeInput by remember { mutableStateOf("") }
        var editingType by remember { mutableStateOf<String?>(null) }
        var editTypeInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManageTypesDialog = false },
            title = { Text("Manage Account Types", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // List of current types
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        types.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (editingType == t) {
                                    OutlinedTextField(
                                        value = editTypeInput,
                                        onValueChange = { editTypeInput = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (editTypeInput.isNotBlank()) {
                                                viewModel.editAccountType(t, editTypeInput.trim())
                                                editingType = null
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Text(t, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = {
                                            editingType = t
                                            editTypeInput = t
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Add new type input
                    Text("Add New Type", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newTypeInput,
                            onValueChange = { newTypeInput = it },
                            placeholder = { Text("e.g. Investment, Loan") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newTypeInput.isNotBlank()) {
                                    viewModel.addAccountType(newTypeInput.trim())
                                    newTypeInput = ""
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageTypesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// --- CATEGORY FORM ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryForm(
    viewModel: ExpensoViewModel,
    categoryToEdit: com.expenso.app.data.Category? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(categoryToEdit) { mutableStateOf(categoryToEdit?.name ?: "") }
    var isExpense by remember(categoryToEdit) { mutableStateOf(categoryToEdit?.type == "expense" || categoryToEdit == null) }
    val randomIcon = remember { IconMapper.groupedIcons.values.flatten().random() }
    var selectedIcon by remember(categoryToEdit) { mutableStateOf(categoryToEdit?.icon ?: randomIcon) }
    var selectedColor by remember(categoryToEdit) { mutableStateOf(categoryToEdit?.color ?: "#FFADAD") }

    // State for icon selection check
    val isStandardIconSelected = IconMapper.groupedIcons.values.flatten().contains(selectedIcon.lowercase())
    val localSnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val onSave = {
        if (name.isBlank()) {
            coroutineScope.launch { localSnackbarHostState.showSnackbar("Please enter a category name") }
        } else {
            if (categoryToEdit == null) {
                viewModel.insertCategory(
                    name = name,
                    type = if (isExpense) "expense" else "income",
                    color = selectedColor,
                    icon = selectedIcon
                )
                coroutineScope.launch { localSnackbarHostState.showSnackbar("Category Added") }
            } else {
                viewModel.updateCategory(
                    categoryToEdit.copy(
                        name = name,
                        type = if (isExpense) "expense" else "income",
                        color = selectedColor,
                        icon = selectedIcon
                    )
                )
                coroutineScope.launch { localSnackbarHostState.showSnackbar("Category Updated") }
            }
            onDismiss()
        }
    }

    Scaffold(
        snackbarHost = { com.expenso.app.ui.components.SwipeableSnackbarHost(localSnackbarHostState) },
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = CircleShape
                        )
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (categoryToEdit == null) "Add category" else "Edit category",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onSave() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ANIMATED TOGGLE COLORS
            val expenseBgColor by animateColorAsState(
                targetValue = if (isExpense) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                label = "expense_bg"
            )
            val expenseTextColor by animateColorAsState(
                targetValue = if (isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "expense_text"
            )
            val incomeBgColor by animateColorAsState(
                targetValue = if (!isExpense) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                label = "income_bg"
            )
            val incomeTextColor by animateColorAsState(
                targetValue = if (!isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "income_text"
            )

            // INCOME OR EXPENSE TOGGLE (Pill layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = CircleShape
                    )
                    .padding(4.dp)
            ) {
                // EXPENSE BUTTON
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(expenseBgColor)
                        .clickable {
                            isExpense = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = expenseTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "EXPENSE",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = expenseTextColor
                        )
                    }
                }

                // INCOME BUTTON
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(incomeBgColor)
                        .clickable {
                            isExpense = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = incomeTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "INCOME",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = incomeTextColor
                        )
                    }
                }
            }

            // PREVIEW ROW (Icon circle + Category name input)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val colorObj = try {
                    Color(android.graphics.Color.parseColor(selectedColor))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(colorObj, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val isCustom = selectedIcon.isNotEmpty() && !IconMapper.groupedIcons.values.flatten().contains(selectedIcon.lowercase())
                    if (isCustom) {
                        Text(
                            text = selectedIcon,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.Black
                        )
                    } else {
                        Icon(
                            imageVector = IconMapper.getIcon(selectedIcon),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Category name",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it
                            if (it.isNotBlank()) {
                                val initials = it.take(2).uppercase()
                                selectedIcon = initials
                            } else {
                                selectedIcon = ""
                            }
                        },
                        placeholder = { Text("e.g. Subscriptions, Gifts") },
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // CATEGORY COLOR SECTION
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Category color",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                val chunks = IconMapper.availableColors.chunked(9)
                chunks.forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowColors.forEach { hexColor ->
                            val isSelected = selectedColor.lowercase() == hexColor.lowercase()
                            val circleColor = try {
                                Color(android.graphics.Color.parseColor(hexColor))
                            } catch (e: Exception) {
                                Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(circleColor)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                    .clickable { selectedColor = hexColor },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            // CATEGORY ICON SECTION (Grouped as in screenshot)
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Category icon",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                IconMapper.groupedIcons.forEach { (groupName, iconsList) ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        val iconChunks = iconsList.chunked(5)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            iconChunks.forEach { rowIcons ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowIcons.forEach { iconName ->
                                        val isSelected = selectedIcon.lowercase() == iconName.lowercase()
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                .border(if (isSelected) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)), CircleShape)
                                                .clickable { selectedIcon = iconName },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = IconMapper.getIcon(iconName),
                                                contentDescription = iconName,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    repeat(5 - rowIcons.size) {
                                        Spacer(modifier = Modifier.size(56.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp)) // Extra space for FAB
            }
        }
    }
}

// --- BUDGET FORM ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetForm(
    viewModel: ExpensoViewModel,
    budgetToEdit: com.expenso.app.data.Budget? = null,
    onNavigateToAddCategory: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val categories by viewModel.allCategories.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val haptic = LocalHapticFeedback.current
    val localSnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    var amountStr by remember { mutableStateOf(budgetToEdit?.budgetAmount?.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() } ?: "") }
    var budgetNameStr by remember { mutableStateOf(budgetToEdit?.budgetName ?: "") }
    var isCategorySpecific by remember { mutableStateOf(budgetToEdit?.categoryId != null) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(budgetToEdit?.categoryId) }
    var showCategorySelectionDialog by remember { mutableStateOf(false) }
    
    var budgetType by remember {
        mutableStateOf(
            when (budgetToEdit?.budgetType) {
                "WEEKLY" -> "Monthly"
                "CUSTOM" -> "Custom"
                else -> "Monthly"
            }
        )
    }
    var startDate by remember { mutableStateOf<Long?>(budgetToEdit?.startDate) }
    var endDate by remember { mutableStateOf<Long?>(budgetToEdit?.endDate) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    
    val expenseCategories = categories.filter { it.type == "expense" }
    val currencySymbol = settings?.currency ?: "₹"

    // Synchronize selectedCategoryId based on isCategorySpecific selection
    LaunchedEffect(isCategorySpecific) {
        if (!isCategorySpecific) {
            selectedCategoryId = null
        }
    }

    Scaffold(
        snackbarHost = { com.expenso.app.ui.components.SwipeableSnackbarHost(localSnackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (replaces X button, matches TransactionForm style)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = CircleShape
                        )
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title
                Text(
                    text = if (budgetToEdit != null) "Edit Budget" else "Add Budget", 
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Tick (Save) Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (budgetNameStr.isBlank()) {
                                coroutineScope.launch { localSnackbarHostState.showSnackbar("Please enter a budget name") }
                                return@clickable
                            }

                            val amount = amountStr.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                coroutineScope.launch { localSnackbarHostState.showSnackbar("Please enter a valid amount > 0") }
                                return@clickable
                            }

                            if (isCategorySpecific && selectedCategoryId == null) {
                                coroutineScope.launch { localSnackbarHostState.showSnackbar("Please select a category") }
                                return@clickable
                            }
                            
                            val calendar = Calendar.getInstance()
                            if (budgetToEdit != null) {
                                calendar.set(Calendar.MONTH, budgetToEdit.month - 1)
                                calendar.set(Calendar.YEAR, budgetToEdit.year)
                            }
                            val currentMonth = calendar.get(Calendar.MONTH) + 1 // 1-12
                            val currentYear = calendar.get(Calendar.YEAR)
                            
                            var finalStartDate = startDate
                            var finalEndDate = endDate
                            
                            val budgetTypeEnum = budgetType.uppercase(java.util.Locale.ROOT)
                            if (budgetTypeEnum == "WEEKLY") {
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                finalStartDate = cal.timeInMillis
                                cal.add(java.util.Calendar.DAY_OF_WEEK, 6)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                                cal.set(java.util.Calendar.MINUTE, 59)
                                cal.set(java.util.Calendar.SECOND, 59)
                                finalEndDate = cal.timeInMillis
                            } else if (budgetTypeEnum == "MONTHLY") {
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                finalStartDate = cal.timeInMillis
                                cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                                cal.set(java.util.Calendar.MINUTE, 59)
                                cal.set(java.util.Calendar.SECOND, 59)
                                finalEndDate = cal.timeInMillis
                            }

                            viewModel.insertBudget(
                                categoryId = if (isCategorySpecific) selectedCategoryId else null,
                                amount = amount,
                                month = currentMonth,
                                year = currentYear,
                                budgetType = budgetTypeEnum,
                                startDate = finalStartDate,
                                endDate = finalEndDate,
                                budgetName = budgetNameStr.trim(),
                                id = budgetToEdit?.id ?: 0
                            )
                            coroutineScope.launch { localSnackbarHostState.showSnackbar(if (budgetToEdit != null) "Budget Updated" else "Budget Configured") }
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
        ) {
            // Input Fields
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                // Budget Name Input Field
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
                    Text(
                        text = "Budget Name",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedTextField(
                        value = budgetNameStr,
                        onValueChange = { budgetNameStr = it },
                        placeholder = { Text("e.g. Weekly Groceries, Tech Fund") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Budget Limit Input Field
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
                    Text(
                        text = "Budget Limit",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        prefix = { 
                            Text(
                                text = "$currencySymbol ", 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                            ) 
                        },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Scope Options (styled as month selector bar, decreased height)
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(
                    text = "Budget Scope",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    val isOverallSelected = !isCategorySpecific
                    // Overall Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isOverallSelected) 2.dp else 1.dp,
                            color = if (isOverallSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCategorySpecific = false
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                tint = if (isOverallSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Overall Limit",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isOverallSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    val isCategorySelected = isCategorySpecific
                    // Category Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isCategorySelected) 2.dp else 1.dp,
                            color = if (isCategorySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCategorySpecific = true
                                showCategorySelectionDialog = true
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val activeCategory = if (isCategorySpecific) expenseCategories.firstOrNull { it.id == selectedCategoryId } else null
                            if (activeCategory != null) {
                                val categoryColor = try { Color(android.graphics.Color.parseColor(activeCategory.color)) } catch(e: Exception) { MaterialTheme.colorScheme.primary }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconMapper.CategoryIcon(
                                        icon = activeCategory.icon,
                                        categoryName = activeCategory.name,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Category,
                                    contentDescription = null,
                                    tint = if (isCategorySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeCategory?.name ?: "By Category",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isCategorySelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Period Selector (styled as month selector bar, decreased height)
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(
                    text = "Budget Period",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Monthly", "Custom").forEach { period ->
                        val isSelected = budgetType == period
                        val borderModifier = if (isSelected) {
                            Modifier
                        } else {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(borderModifier)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable {
                                    budgetType = period
                                    if (period == "Custom") {
                                        showDateRangePicker = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                if (budgetType == "Custom" && startDate != null && endDate != null) {
                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                    Text(
                        text = "${sdf.format(java.util.Date(startDate!!))} - ${sdf.format(java.util.Date(endDate!!))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }


        }
    }
    
    if (showCategorySelectionDialog) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySelectionDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.25f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Category",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                val categoryGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = categoryGridState,
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .gridScrollbar(categoryGridState, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    gridItems(expenseCategories) { category: Category ->
                        val isSelected = selectedCategoryId == category.id
                        val categoryColor = try {
                            Color(android.graphics.Color.parseColor(category.color))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) categoryColor.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    selectedCategoryId = category.id
                                    showCategorySelectionDialog = false
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                IconMapper.CategoryIcon(
                                    icon = category.icon,
                                    categoryName = category.name,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FilledTonalButton(
                    onClick = {
                        showCategorySelectionDialog = false
                        onNavigateToAddCategory()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Category", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    
    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = dateRangePickerState.selectedStartDateMillis
                    endDate = dateRangePickerState.selectedEndDateMillis
                    showDateRangePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

// --- SAVINGS GOAL FORM ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalForm(
    viewModel: ExpensoViewModel,
    goalToEdit: SavingsGoal? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localSnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var title by remember(goalToEdit) { mutableStateOf(goalToEdit?.title ?: "") }
    var targetStr by remember(goalToEdit) { mutableStateOf(goalToEdit?.targetAmount?.toString() ?: "") }
    var currentStr by remember(goalToEdit) { mutableStateOf(goalToEdit?.currentAmount?.toString() ?: "0") }
    var targetDate by remember(goalToEdit) {
        mutableStateOf(
            goalToEdit?.targetDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }
    var selectedIcon by remember(goalToEdit) { mutableStateOf(goalToEdit?.icon ?: "savings") }
    var selectedColor by remember(goalToEdit) { mutableStateOf(goalToEdit?.color ?: "#A0C4FF") }

    Scaffold(
        snackbarHost = { com.expenso.app.ui.components.SwipeableSnackbarHost(localSnackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (goalToEdit == null) "New Savings Goal" else "Modify Goal", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (goalToEdit != null) {
                        IconButton(
                            onClick = {
                                viewModel.deleteSavingsGoal(goalToEdit)
                                coroutineScope.launch { localSnackbarHostState.showSnackbar("Goal Removed") }
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove Goal", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Savings Goal Title") },
                placeholder = { Text("e.g. Tesla Downpayment, New MacBook, Emergency Fund") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = targetStr,
                    onValueChange = { targetStr = it },
                    label = { Text("Target Amount") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = currentStr,
                    onValueChange = { currentStr = it },
                    label = { Text("Initial Saved Amount") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Target Date Picker
            OutlinedTextField(
                value = targetDate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Target Date") },
                trailingIcon = {
                    IconButton(onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> targetDate = String.format("%04d-%02d-%02d", y, m + 1, d) },
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = "Pick Date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Icon List
            Text("Goal Icon", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(IconMapper.availableIcons) { iconName ->
                    val selected = selectedIcon == iconName
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedIcon = iconName },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IconMapper.getIcon(iconName),
                            contentDescription = iconName,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Colors
            Text("Goal Color", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            val colorChunks = IconMapper.availableColors.chunked(9)
            colorChunks.forEach { rowColors ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowColors.forEach { hexColor ->
                        val selected = selectedColor == hexColor
                        val colorObj = Color(android.graphics.Color.parseColor(hexColor))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colorObj)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                .clickable { selectedColor = hexColor },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Please enter a title") }
                        return@Button
                    }
                    val target = targetStr.toDoubleOrNull()
                    val current = currentStr.toDoubleOrNull() ?: 0.0
                    if (target == null || target <= 0) {
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Please enter a valid target amount > 0") }
                        return@Button
                    }

                    if (goalToEdit == null) {
                        viewModel.insertSavingsGoal(title, target, current, targetDate, selectedIcon, selectedColor)
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Savings Goal Saved") }
                    } else {
                        viewModel.updateSavingsGoal(goalToEdit.id, title, target, current, targetDate, goalToEdit.status, selectedIcon, selectedColor)
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Savings Goal Updated") }
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (goalToEdit == null) "Create Savings Goal" else "Save Changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- RECURRING TRANSACTION FORM ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionForm(
    viewModel: ExpensoViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localSnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val accounts by viewModel.allAccounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()

    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var frequency by remember { mutableStateOf("monthly") } // daily, weekly, monthly, yearly
    var startDate by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }

    var selectedAccountId by remember { mutableStateOf(0) }
    var selectedCategoryId by remember { mutableStateOf(0) }

    val frequencies = listOf("daily", "weekly", "monthly", "yearly")
    val filteredCategories = categories.filter { it.type == (if (isExpense) "expense" else "income") }

    LaunchedEffect(isExpense) {
        selectedCategoryId = 0
    }

    Scaffold(
        snackbarHost = { com.expenso.app.ui.components.SwipeableSnackbarHost(localSnackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Schedule Repeating", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // INCOME / EXPENSE SEGMENTED SWITCH
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = CircleShape
                    )
                    .padding(4.dp)
            ) {
                // EXPENSE BUTTON
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (isExpense) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .clickable { isExpense = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = if (isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "EXPENSE",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // INCOME BUTTON
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (!isExpense) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .clickable { isExpense = false },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = if (!isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "INCOME",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (!isExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Frequency", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                frequencies.forEach { freq ->
                    val selected = frequency == freq
                    FilterChip(
                        selected = selected,
                        onClick = { frequency = freq },
                        label = { Text(freq.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            // SELECT ACCOUNT
            Text("Select Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(accounts) { account ->
                    val selected = selectedAccountId == account.id
                    FilterChip(
                        selected = selected,
                        onClick = { selectedAccountId = account.id },
                        label = { Text(account.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = IconMapper.getIcon(account.icon),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color(android.graphics.Color.parseColor(account.color))
                            )
                        }
                    )
                }
            }

            // SELECT CATEGORY
            Text("Select Category", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                filteredCategories.forEach { category ->
                    val selected = selectedCategoryId == category.id
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) },
                        leadingIcon = {
                            IconMapper.CategoryIcon(
                                icon = category.icon,
                                categoryName = category.name,
                                modifier = Modifier.size(24.dp),
                                tint = Color(android.graphics.Color.parseColor(category.color))
                            )
                        }
                    )
                }
            }

            // START DATE Picker
            OutlinedTextField(
                value = startDate,
                onValueChange = {},
                readOnly = true,
                label = { Text("First Execution Date") },
                trailingIcon = {
                    IconButton(onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> startDate = String.format("%04d-%02d-%02d", y, m + 1, d) },
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = "Pick Date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // NOTE
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Description / Label") },
                placeholder = { Text("e.g. Netflix Subscription, Gym Fee, Monthly Rent") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Please enter a valid amount > 0") }
                        return@Button
                    }
                    if (selectedAccountId == 0) {
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Please select an account") }
                        return@Button
                    }
                    if (selectedCategoryId == 0) {
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Please select a category") }
                        return@Button
                    }

                    viewModel.insertRecurringRule(
                        amount = amount,
                        type = if (isExpense) "expense" else "income",
                        categoryId = selectedCategoryId,
                        accountId = selectedAccountId,
                        note = note.takeIf { it.isNotBlank() } ?: "Repeating Entry",
                        frequency = frequency,
                        startDate = startDate
                    )
                    coroutineScope.launch { localSnackbarHostState.showSnackbar("Recurring rule scheduled!") }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Schedule Recurring Entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Simple FlowRow equivalent for Material 3 layouts
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

fun Modifier.gridScrollbar(
    state: LazyGridState,
    color: Color,
    width: Dp = 6.dp
): Modifier = this.drawWithContent {
    drawContent()
    
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    
    if (visibleItems.isNotEmpty() && totalItems > visibleItems.size) {
        val firstVisibleItem = visibleItems.first()
        val columns = 3
        
        // Approximate total height and current scroll offset
        val totalRows = (totalItems + columns - 1) / columns
        val firstRow = state.firstVisibleItemIndex / columns
        val scrollOffset = state.firstVisibleItemScrollOffset
        
        // Measure average row height
        val averageItemHeight = visibleItems.map { it.size.height }.average().toFloat()
        
        val totalHeightEst = averageItemHeight * totalRows
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        
        if (totalHeightEst > viewportHeight) {
            val currentOffset = firstRow * averageItemHeight + scrollOffset
            
            val scrollbarHeight = (viewportHeight.toFloat() / totalHeightEst) * size.height
            val scrollbarOffsetY = (currentOffset / totalHeightEst) * size.height
            
            val finalScrollbarHeight = scrollbarHeight.coerceIn(24.dp.toPx(), size.height)
            val finalScrollbarOffsetY = scrollbarOffsetY.coerceIn(0f, size.height - finalScrollbarHeight)
            
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - width.toPx() - 4.dp.toPx(), finalScrollbarOffsetY),
                size = Size(width.toPx(), finalScrollbarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}
