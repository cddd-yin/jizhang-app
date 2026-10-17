package com.expenso.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.absoluteValue
import com.expenso.app.data.*
import com.expenso.app.ui.screens.*
import com.expenso.app.ui.theme.MyApplicationTheme

class MainActivity : androidx.fragment.app.FragmentActivity() {

    private val viewModel: ExpensoViewModel by viewModels()

    // Export CSV Contract
    private val csvExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val outStream = contentResolver.openOutputStream(uri)
                if (outStream != null) {
                    viewModel.exportTransactionsToCsv(outStream) { success ->
                        runOnUiThread {
                            if (success) {
                                viewModel.showSnackbar("Transactions exported to CSV! 📊")
                            } else {
                                viewModel.showSnackbar("Failed to export CSV.")
                            }
                        }
                    }
                } else {
                    viewModel.showSnackbar("Failed to open CSV destination file.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.showSnackbar("CSV Export Error: ${e.message}")
            }
        }
    }

    // Export PDF Contract
    private val pdfExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                val outStream = contentResolver.openOutputStream(uri)
                if (outStream != null) {
                    viewModel.exportTransactionsToPdf(outStream) { success ->
                        runOnUiThread {
                            if (success) {
                                viewModel.showSnackbar("Transactions exported to PDF! 📄")
                            } else {
                                viewModel.showSnackbar("Failed to export PDF.")
                            }
                        }
                    }
                } else {
                    viewModel.showSnackbar("Failed to open PDF destination file.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.showSnackbar("PDF Export Error: ${e.message}")
            }
        }
    }

    // Import CSV Contract
    private val csvImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inStream = contentResolver.openInputStream(uri)
                if (inStream != null) {
                    viewModel.importTransactionsFromCsv(inStream) { success, message ->
                        runOnUiThread {
                            if (success) {
                                viewModel.showSnackbar(message)
                            } else {
                                viewModel.showSnackbar("CSV Import Failed: $message")
                            }
                        }
                    }
                } else {
                    viewModel.showSnackbar("Failed to open CSV source file.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.showSnackbar("CSV Import Error: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsState()
            
            // Map settings ThemeMode into Compose isDarkTheme logic
            val isDark = when (settings?.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark, colorPalette = settings?.colorPalette ?: "Default") {
                val isLocked by viewModel.isLocked.collectAsState()

                if (isLocked) {
                    SplashAndLockScreen(
                        viewModel = viewModel,
                        onTriggerBiometric = { onSuccess ->
                            triggerBiometricPrompt {
                                onSuccess()
                            }
                        },
                        onUnlockSuccess = {
                            viewModel.unlockApp()
                        }
                    )
                } else {
                    MainAppContent(
                        viewModel = viewModel,
                        onExportCsv = { csvExportLauncher.launch("expenso_transactions.csv") },
                        onImportCsv = { csvImportLauncher.launch("text/comma-separated-values") },
                        onExportPdf = { pdfExportLauncher.launch("expenso_transactions.pdf") }
                    )
                }
            }
        }
    }

    private fun triggerBiometricPrompt(onSuccess: () -> Unit) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Expenso Secure Unlock")
            .setSubtitle("Authenticate using biometrics to access financial vault")
            .setNegativeButtonText("Use PIN / Cancel")
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            // Biometrics not set up or not supported
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-lock when leaving application to background if App Lock is configured
        viewModel.lockApp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: ExpensoViewModel,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onExportPdf: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsState()
    
    // Form navigation overlays
    val activeFormStack = remember { mutableStateListOf<String>() }
    var selectedTransactionToEdit by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var lastNonNullTxToEdit by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var selectedAccountToEdit by remember { mutableStateOf<Account?>(null) }
    var lastNonNullAccToEdit by remember { mutableStateOf<Account?>(null) }
    var selectedGoalToEdit by remember { mutableStateOf<SavingsGoal?>(null) }
    var lastNonNullGoalToEdit by remember { mutableStateOf<SavingsGoal?>(null) }
    var selectedCategoryToEdit by remember { mutableStateOf<com.expenso.app.data.Category?>(null) }
    var lastNonNullCategoryToEdit by remember { mutableStateOf<com.expenso.app.data.Category?>(null) }
    var selectedBudgetToEdit by remember { mutableStateOf<com.expenso.app.data.Budget?>(null) }
    var lastNonNullBudgetToEdit by remember { mutableStateOf<com.expenso.app.data.Budget?>(null) }

    val activeFormState = remember {
        object : MutableState<String?> {
            override var value: String?
                get() = activeFormStack.lastOrNull()
                set(newValue) {
                    if (newValue == null) {
                        activeFormStack.clear()
                        selectedTransactionToEdit = null
                        selectedAccountToEdit = null
                        selectedGoalToEdit = null
                        selectedCategoryToEdit = null
                        selectedBudgetToEdit = null
                    } else {
                        val index = activeFormStack.indexOf(newValue)
                        if (index >= 0) {
                            // Clear states of popped screens
                            for (i in (index + 1) until activeFormStack.size) {
                                when (activeFormStack[i]) {
                                    "edit_transaction" -> selectedTransactionToEdit = null
                                    "edit_account" -> selectedAccountToEdit = null
                                    "edit_category" -> selectedCategoryToEdit = null
                                    "edit_goal" -> selectedGoalToEdit = null
                                    "edit_budget" -> selectedBudgetToEdit = null
                                }
                            }
                            while (activeFormStack.size > index + 1) {
                                activeFormStack.removeAt(activeFormStack.lastIndex)
                            }
                        } else {
                            activeFormStack.add(newValue)
                        }
                    }
                }

            override fun component1(): String? = value
            override fun component2(): (String?) -> Unit = { value = it }
        }
    }
    var activeForm by activeFormState
    var previousForm by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = activeForm != null || currentTab != 0) {
        if (activeForm != null) {
            if (activeFormStack.size > 1) {
                val popped = activeFormStack.removeAt(activeFormStack.lastIndex)
                // Clear state of the popped screen
                when (popped) {
                    "edit_transaction" -> selectedTransactionToEdit = null
                    "edit_account" -> selectedAccountToEdit = null
                    "edit_category" -> selectedCategoryToEdit = null
                    "edit_goal" -> selectedGoalToEdit = null
                    "edit_budget" -> selectedBudgetToEdit = null
                }
            } else {
                activeFormStack.clear()
                selectedTransactionToEdit = null
                selectedAccountToEdit = null
                selectedGoalToEdit = null
                selectedCategoryToEdit = null
                selectedBudgetToEdit = null
            }
        } else if (currentTab != 0) {
            viewModel.selectTab(0)
        }
    }

    // Listen to localized alerts / budget warnings from SharedFlow
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.notificationMessage.collect { msg ->
            viewModel.showSnackbar(msg)
        }
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = if (event.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { 
                com.expenso.app.ui.components.SwipeableSnackbarHost(
                    hostState = snackbarHostState
                )
            },
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                val isScrolling by viewModel.isScrolling.collectAsState()
                AnimatedVisibility(
                    visible = currentTab == 0 && !isScrolling,
                    enter = slideInVertically(
                        initialOffsetY = { it * 2 },
                        animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                    ) + fadeIn(
                        animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween()
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it * 2 },
                        animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                    ) + fadeOut(
                        animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween()
                    ),
                    modifier = Modifier.zIndex(0f)
                ) {
                    FloatingActionButton(
                        onClick = { activeForm = "add_transaction" },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Transaction")
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        label = { Text("Home", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                        modifier = Modifier.testTag("tab_records")
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
                        label = { Text("Analytics", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                        modifier = Modifier.testTag("tab_analysis")
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.Rounded.DonutLarge, contentDescription = null) },
                        label = { Text("Budgets", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                        modifier = Modifier.testTag("tab_budgets")
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        icon = { Icon(Icons.Rounded.AccountBalance, contentDescription = null) },
                        label = { Text("Accounts", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                        modifier = Modifier.testTag("tab_accounts")
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { viewModel.selectTab(4) },
                        icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = null) },
                        label = { Text("More", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                        modifier = Modifier.testTag("tab_more")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(
                        bottom = innerPadding.calculateBottomPadding()
                    )
                    .fillMaxSize()
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val slideSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween<androidx.compose.ui.unit.IntOffset>()
                        val scaleSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween<Float>()
                        val fadeSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween<Float>()
                        
                        if (targetState > initialState) {
                            (slideInHorizontally(animationSpec = slideSpec) { width -> width / 12 } +
                             scaleIn(animationSpec = scaleSpec, initialScale = 0.98f) +
                             fadeIn(animationSpec = fadeSpec))
                                .togetherWith(
                                    slideOutHorizontally(animationSpec = slideSpec) { width -> -width / 12 } +
                                    scaleOut(animationSpec = scaleSpec, targetScale = 0.98f) +
                                    fadeOut(animationSpec = fadeSpec)
                                )
                        } else if (targetState < initialState) {
                            (slideInHorizontally(animationSpec = slideSpec) { width -> -width / 12 } +
                             scaleIn(animationSpec = scaleSpec, initialScale = 0.98f) +
                             fadeIn(animationSpec = fadeSpec))
                                .togetherWith(
                                    slideOutHorizontally(animationSpec = slideSpec) { width -> width / 12 } +
                                    scaleOut(animationSpec = scaleSpec, targetScale = 0.98f) +
                                    fadeOut(animationSpec = fadeSpec)
                                )
                        } else {
                            fadeIn(animationSpec = fadeSpec).togetherWith(fadeOut(animationSpec = fadeSpec))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "tab_animated_content"
                ) { page ->
                    when (page) {
                        0 -> {
                            RecordsScreen(
                                viewModel = viewModel,
                                onEditTransaction = { tx ->
                                    previousForm = null
                                    selectedTransactionToEdit = tx
                                    lastNonNullTxToEdit = tx
                                    activeForm = "edit_transaction"
                                },
                                onAddTransactionClick = { activeForm = "add_transaction" },
                                onSearchClick = { activeForm = "search_transactions" }
                            )
                        }
                        1 -> {
                            AnalysisScreen(
                                viewModel = viewModel,
                                onSearchClick = { activeForm = "search_transactions" }
                            )
                        }
                        2 -> {
                            BudgetsScreen(
                                viewModel = viewModel,
                                onNavigateToAddBudget = { activeForm = "add_budget" },
                                onEditBudget = { budget ->
                                    selectedBudgetToEdit = budget
                                    lastNonNullBudgetToEdit = budget
                                    activeForm = "edit_budget"
                                }
                            )
                        }
                        3 -> {
                            AccountsScreen(
                                viewModel = viewModel,
                                onAddAccountClick = { activeForm = "add_account" },
                                onEditAccount = { account ->
                                    selectedAccountToEdit = account
                                    lastNonNullAccToEdit = account
                                    activeForm = "edit_account"
                                },
                                onAddTransactionClick = { _ ->
                                    activeForm = "add_transaction"
                                }
                            )
                        }
                        4 -> {
                            MoreScreen(
                                viewModel = viewModel,
                                onExportCsv = onExportCsv,
                                onImportCsv = onImportCsv,
                                onExportPdf = onExportPdf,
                                onNavigateToCategories = { activeForm = "categories" },
                                onNavigateToAddCategory = { activeForm = "add_category" },
                                onEditCategory = { category ->
                                    selectedCategoryToEdit = category
                                    lastNonNullCategoryToEdit = category
                                    activeForm = "edit_category"
                                },
                                onNavigateToAddBudget = { activeForm = "add_budget" },
                                onNavigateToAddGoal = { activeForm = "add_goal" },
                                onNavigateToAddRecurring = { activeForm = "add_recurring" },
                                onEditSavingsGoal = { goal ->
                                    selectedGoalToEdit = goal
                                    lastNonNullGoalToEdit = goal
                                    activeForm = "edit_goal"
                                }
                            )
                        }
                    }
                }
            }
        }

        // FULL SCREEN OVERLAY TRANSITIONAL MODAL DIALOGS FOR FORMS

        AnimatedContent(
            targetState = activeForm,
            transitionSpec = {
                val isHorizontalState = { state: String? ->
                    state != null && state != "add_transaction" && state != "search_transactions"
                }
                
                if (initialState == null && targetState != null) {
                    val enterAnim = if (targetState in listOf("add_transaction", "edit_transaction", "add_category", "edit_category")) {
                        scaleIn(
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.9f),
                            initialScale = 0.82f,
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                        ) + fadeIn(
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween()
                        )
                    } else if (isHorizontalState(targetState)) {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                        ) + fadeIn(
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween()
                        )
                    } else {
                        slideInVertically(
                            initialOffsetY = { (it * 0.3f).toInt() },
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                        ) + fadeIn(
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween()
                        )
                    }
                    enterAnim togetherWith fadeOut(animationSpec = tween(0))
                } else if (initialState != null && targetState == null) {
                    val exitAnim = if (initialState in listOf("add_transaction", "edit_transaction", "add_category", "edit_category")) {
                        scaleOut(
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.9f),
                            targetScale = 0.82f,
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                        ) + fadeOut(
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween()
                        )
                    } else if (isHorizontalState(initialState)) {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                        ) + fadeOut(
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween()
                        )
                    } else {
                        slideOutVertically(
                            targetOffsetY = { (it * 0.3f).toInt() },
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                        ) + fadeOut(
                            animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween()
                        )
                    }
                    fadeIn(animationSpec = tween(0)) togetherWith exitAnim
                } else {
                    val getDepth = { state: String? ->
                        when (state) {
                            null -> 0
                            "search_transactions", "categories", "add_transaction", "add_budget", "edit_budget", "add_goal", "edit_goal", "add_recurring", "edit_recurring" -> 1
                            "edit_transaction" -> 2
                            "add_category", "edit_category", "add_account", "edit_account" -> 3
                            else -> 1
                        }
                    }
                    val isBack = getDepth(initialState) > getDepth(targetState)
                    val slideSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring<androidx.compose.ui.unit.IntOffset>()
                    val fadeSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultTween<Float>()
                    val isScaleState = { state: String? ->
                        state in listOf("add_transaction", "edit_transaction", "add_category", "edit_category")
                    }
                    
                    if (isBack) {
                        if (isScaleState(initialState)) {
                            val exitAnim = scaleOut(
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.9f),
                                targetScale = 0.82f,
                                animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                            ) + fadeOut(animationSpec = fadeSpec)
                            fadeIn(animationSpec = fadeSpec) togetherWith exitAnim
                        } else {
                            (slideInHorizontally(animationSpec = slideSpec) { width -> -width / 4 } + fadeIn(animationSpec = fadeSpec)) togetherWith
                            (slideOutHorizontally(animationSpec = slideSpec) { width -> width } + fadeOut(animationSpec = fadeSpec))
                        }
                    } else {
                        if (isScaleState(targetState)) {
                            val enterAnim = scaleIn(
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.9f),
                                initialScale = 0.82f,
                                animationSpec = com.expenso.app.ui.components.ExpensoAnimations.defaultSpring()
                            ) + fadeIn(animationSpec = fadeSpec)
                            enterAnim togetherWith fadeOut(animationSpec = fadeSpec)
                        } else {
                            (slideInHorizontally(animationSpec = slideSpec) { width -> width } + fadeIn(animationSpec = fadeSpec)) togetherWith
                            (slideOutHorizontally(animationSpec = slideSpec) { width -> -width / 4 } + fadeOut(animationSpec = fadeSpec))
                        }
                    }
                }
            },
            label = "form_transition",
            modifier = Modifier.fillMaxSize()
        ) { targetForm ->
            if (targetForm != null) {
                when (targetForm) {
                    "search_transactions" -> {
                        SearchScreen(
                            viewModel = viewModel,
                            onEditTransaction = { tx ->
                                previousForm = "search_transactions"
                                selectedTransactionToEdit = tx
                                lastNonNullTxToEdit = tx
                                activeForm = "edit_transaction"
                            },
                            onDismiss = { activeForm = null }
                        )
                    }
                    "add_transaction" -> {
                        TransactionForm(
                            viewModel = viewModel,
                            onNavigateToAddCategory = { activeForm = "add_category" },
                            onNavigateToAddAccount = { activeForm = "add_account" },
                            onDismiss = { activeForm = null }
                        )
                    }
                    "edit_transaction" -> {
                        TransactionForm(
                            viewModel = viewModel,
                            transactionToEdit = lastNonNullTxToEdit,
                            onNavigateToAddCategory = { activeForm = "add_category" },
                            onNavigateToAddAccount = { activeForm = "add_account" },
                            onDismiss = { 
                                activeForm = previousForm
                                previousForm = null
                                selectedTransactionToEdit = null
                            }
                        )
                    }
                    "add_account" -> {
                        AccountForm(
                            viewModel = viewModel,
                            onDismiss = { activeForm = null }
                        )
                    }
                    "edit_account" -> {
                        AccountForm(
                            viewModel = viewModel,
                            accountToEdit = lastNonNullAccToEdit,
                            onDismiss = {
                                activeForm = null
                                selectedAccountToEdit = null
                            }
                        )
                    }
                    "categories" -> {
                        CategoriesScreen(
                            viewModel = viewModel,
                            onNavigateToAddCategory = {
                                previousForm = "categories"
                                activeForm = "add_category"
                            },
                            onEditCategory = { category ->
                                previousForm = "categories"
                                selectedCategoryToEdit = category
                                lastNonNullCategoryToEdit = category
                                activeForm = "edit_category"
                            },
                            onDismiss = { activeForm = null }
                        )
                    }
                    "add_category" -> {
                        CategoryForm(
                            viewModel = viewModel,
                            onDismiss = { 
                                activeForm = previousForm
                                previousForm = null
                            }
                        )
                    }
                    "edit_category" -> {
                        CategoryForm(
                            viewModel = viewModel,
                            categoryToEdit = lastNonNullCategoryToEdit,
                            onDismiss = {
                                activeForm = previousForm
                                previousForm = null
                                selectedCategoryToEdit = null
                            }
                        )
                    }
                    "add_budget" -> {
                        BudgetForm(
                            viewModel = viewModel,
                            onNavigateToAddCategory = {
                                previousForm = "add_budget"
                                activeForm = "add_category"
                            },
                            onDismiss = { activeForm = null }
                        )
                    }
                    "edit_budget" -> {
                        BudgetForm(
                            viewModel = viewModel,
                            budgetToEdit = lastNonNullBudgetToEdit,
                            onNavigateToAddCategory = {
                                previousForm = "edit_budget"
                                activeForm = "add_category"
                            },
                            onDismiss = {
                                activeForm = null
                                selectedBudgetToEdit = null
                            }
                        )
                    }
                    "add_goal" -> {
                        SavingsGoalForm(
                            viewModel = viewModel,
                            onDismiss = { activeForm = null }
                        )
                    }
                    "edit_goal" -> {
                        SavingsGoalForm(
                            viewModel = viewModel,
                            goalToEdit = lastNonNullGoalToEdit,
                            onDismiss = {
                                activeForm = null
                                selectedGoalToEdit = null
                            }
                        )
                    }
                    "add_recurring" -> {
                        RecurringTransactionForm(
                            viewModel = viewModel,
                            onDismiss = { activeForm = null }
                        )
                    }
                }
            }
        }

        val isProcessingFile by viewModel.isProcessingFile.collectAsState()
        val fileProcessingMessage by viewModel.fileProcessingMessage.collectAsState()

        if (isProcessingFile) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {}, // Consume all touch events to block user interaction
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp).testTag("file_processing_indicator")
                        )
                        Text(
                            text = fileProcessingMessage,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
