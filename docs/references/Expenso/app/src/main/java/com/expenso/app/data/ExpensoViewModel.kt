package com.expenso.app.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ExpensoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = Repository(db)

    // --- SHARED PREFERENCES FOR PIN ---
    private val prefs = application.getSharedPreferences("expenso_prefs", Context.MODE_PRIVATE)

    // --- CUSTOM ACCOUNT TYPES STATE ---
    private val _accountTypes = MutableStateFlow<List<String>>(emptyList())
    val accountTypes = _accountTypes.asStateFlow()

    // --- SEARCH HISTORY STATE ---
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        val trimmed = query.trim()
        current.remove(trimmed)
        current.add(0, trimmed)
        val limited = current.take(10)
        _searchHistory.value = limited
        prefs.edit().putString("search_history", limited.joinToString(",")).apply()
    }

    fun removeSearchQuery(query: String) {
        val current = _searchHistory.value.toMutableList()
        current.remove(query)
        _searchHistory.value = current
        prefs.edit().putString("search_history", current.joinToString(",")).apply()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs.edit().remove("search_history").apply()
    }

    fun addAccountType(newType: String) {
        val current = _accountTypes.value.toMutableSet()
        current.add(newType)
        prefs.edit().putStringSet("custom_account_types", current).apply()
        _accountTypes.value = current.toList().sorted()
    }

    fun editAccountType(oldType: String, newType: String) {
        val current = _accountTypes.value.toMutableSet()
        if (current.contains(oldType)) {
            current.remove(oldType)
            current.add(newType)
            prefs.edit().putStringSet("custom_account_types", current).apply()
            _accountTypes.value = current.toList().sorted()
            
            // Update existing accounts of oldType to newType in database
            viewModelScope.launch {
                val accounts = allAccounts.value
                accounts.forEach { acc ->
                    if (acc.type == oldType) {
                        repository.updateAccount(acc.copy(type = newType))
                    }
                }
            }
        }
    }

    // --- NAVIGATION STATE ---
    private val _currentTab = MutableStateFlow(0) // 0: Records, 1: Analysis, 2: Accounts, 3: More
    val currentTab = _currentTab.asStateFlow()

    fun selectTab(index: Int) {
        _currentTab.value = index
    }

    // --- SCROLL STATE ---
    private val _isScrolling = MutableStateFlow(false)
    val isScrolling = _isScrolling.asStateFlow()

    fun setScrolling(scrolling: Boolean) {
        _isScrolling.value = scrolling
    }
    
    // --- FILE PROCESSING STATE ---
    private val _isProcessingFile = MutableStateFlow(false)
    val isProcessingFile = _isProcessingFile.asStateFlow()

    private val _fileProcessingMessage = MutableStateFlow("")
    val fileProcessingMessage = _fileProcessingMessage.asStateFlow()

    fun setFileProcessing(processing: Boolean, message: String = "") {
        _isProcessingFile.value = processing
        _fileProcessingMessage.value = message
    }
    
    var isRecordsInitialLoadDone = false

    // --- PIN & LOCK STATE ---
    private val _isLocked = MutableStateFlow(false)
    val isLocked = _isLocked.asStateFlow()

    private val _isPinSet = MutableStateFlow(false)
    val isPinSet = _isPinSet.asStateFlow()

    private val saltKey = "expenso_salt_key_12345"

    init {
        val pinHash = prefs.getString("pin_hash", null)
        _isPinSet.value = !pinHash.isNullOrEmpty()
        _isLocked.value = !pinHash.isNullOrEmpty()

        // Load custom account types
        val storedTypes = prefs.getStringSet("custom_account_types", null)
        if (storedTypes == null) {
            val defaults = setOf("Cash", "Bank Account", "UPI", "Credit Card", "Debit Card", "Wallet", "Other")
            prefs.edit().putStringSet("custom_account_types", defaults).apply()
            _accountTypes.value = defaults.toList().sorted()
        } else {
            _accountTypes.value = storedTypes.toList().sorted()
        }
        
        // Load search history
        val historyStr = prefs.getString("search_history", "")
        if (!historyStr.isNullOrEmpty()) {
            _searchHistory.value = historyStr.split(",").filter { it.isNotBlank() }
        }

        // Process recurring transactions on view model initialization
        processRecurringTransactions()
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var cats = repository.allCategories.first()
                if (cats.any { it.name == "Food & Dining" } || cats.any { it.name == "Income" }) {
                    cats.forEach { repository.categoryDao.deleteCategory(it) }
                    cats = emptyList()
                }

                if (cats.isEmpty()) {
                    val defaultCats = listOf(
                        Category(name = "Bills", type = "expense", icon = "receipt_long", color = "#FFDFBA", isDefault = false, orderIndex = 1),
                        Category(name = "Car", type = "expense", icon = "directions_car", color = "#FFB3BA", isDefault = false, orderIndex = 2),
                        Category(name = "Clothing", type = "expense", icon = "checkroom", color = "#BDB2FF", isDefault = false, orderIndex = 3),
                        Category(name = "Education", type = "expense", icon = "school", color = "#D8B4FE", isDefault = false, orderIndex = 4),
                        Category(name = "Electronics", type = "expense", icon = "devices", color = "#FFCAD4", isDefault = false, orderIndex = 5),
                        Category(name = "Entertainment", type = "expense", icon = "sports_esports", color = "#A0C4FF", isDefault = false, orderIndex = 6),
                        Category(name = "Food", type = "expense", icon = "restaurant", color = "#D0F4DE", isDefault = false, orderIndex = 7),
                        Category(name = "Health", type = "expense", icon = "medical_services", color = "#FCE1E4", isDefault = false, orderIndex = 8),
                        Category(name = "Home", type = "expense", icon = "home", color = "#E2F0D9", isDefault = false, orderIndex = 9),
                        Category(name = "Insurance", type = "expense", icon = "shield", color = "#FCF6BD", isDefault = false, orderIndex = 10),
                        Category(name = "Shopping", type = "expense", icon = "shopping_bag", color = "#A9DEF9", isDefault = false, orderIndex = 11),
                        Category(name = "Social", type = "expense", icon = "groups", color = "#E4C1F9", isDefault = false, orderIndex = 12),
                        Category(name = "Tax", type = "expense", icon = "account_balance", color = "#D0F4EA", isDefault = false, orderIndex = 13),
                        Category(name = "Mobile", type = "expense", icon = "phone_android", color = "#FFD6A5", isDefault = false, orderIndex = 14),
                        Category(name = "Transportation", type = "expense", icon = "directions_bus", color = "#FFFACD", isDefault = false, orderIndex = 15),
                        Category(name = "Others", type = "expense", icon = "more_horiz", color = "#FAFAD2", isDefault = false, orderIndex = 16),
                        Category(name = "Wifi & Internet", type = "expense", icon = "wifi", color = "#FFEFD5", isDefault = false, orderIndex = 17),
                        Category(name = "Gym", type = "expense", icon = "fitness_center", color = "#FFE4B5", isDefault = false, orderIndex = 18),
                        
                        Category(name = "Others", type = "income", icon = "more_horiz", color = "#BFFCC6", isDefault = false, orderIndex = 19),
                        Category(name = "Awards", type = "income", icon = "emoji_events", color = "#CAFFBF", isDefault = false, orderIndex = 20),
                        Category(name = "Coupons", type = "income", icon = "local_offer", color = "#9BF6FF", isDefault = false, orderIndex = 21),
                        Category(name = "Grants", type = "income", icon = "school", color = "#FFFFBA", isDefault = false, orderIndex = 22),
                        Category(name = "Lottery", type = "income", icon = "casino", color = "#FFCAD4", isDefault = false, orderIndex = 23),
                        Category(name = "Refunds", type = "income", icon = "receipt_long", color = "#A0C4FF", isDefault = false, orderIndex = 24),
                        Category(name = "Rental", type = "income", icon = "home", color = "#D8B4FE", isDefault = false, orderIndex = 25),
                        Category(name = "Salary", type = "income", icon = "payments", color = "#B9FBC0", isDefault = false, orderIndex = 26),
                        Category(name = "Sold Items", type = "income", icon = "storefront", color = "#FBF8CC", isDefault = false, orderIndex = 27)
                    )
                    defaultCats.forEach { repository.categoryDao.insertCategory(it) }

                    repository.settingsDao.insertSettings(Settings(id = 1, themeMode = "system", currency = "₹", pinEnabled = false, biometricEnabled = false, notificationsEnabled = true, colorPalette = "Default"))
                }
                
                // Seed Default Accounts if there are no accounts
                val accounts = repository.allAccounts.first()
                if (accounts.isEmpty()) {
                    repository.accountDao.insertAccount(
                        Account(name = "Cash", type = "Cash", openingBalance = 0.0, currentBalance = 0.0, icon = "wallet", color = "#FFD6A5")
                    )
                    repository.accountDao.insertAccount(
                        Account(name = "Bank Account", type = "Bank Account", openingBalance = 0.0, currentBalance = 0.0, icon = "account_balance", color = "#A0C4FF")
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setPin(pin: String) {
        if (pin.length in 4..6) {
            val hash = sha256(pin, saltKey)
            prefs.edit().putString("pin_hash", hash).apply()
            _isPinSet.value = true
            _isLocked.value = false // unlocked upon setting
        }
    }

    fun disablePin() {
        prefs.edit().remove("pin_hash").apply()
        _isPinSet.value = false
        _isLocked.value = false
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString("pin_hash", null) ?: return true
        val checkHash = sha256(pin, saltKey)
        val success = (storedHash == checkHash)
        if (success) {
            _isLocked.value = false
        }
        return success
    }

    fun unlockApp() {
        _isLocked.value = false
    }

    fun lockApp() {
        if (_isPinSet.value || (settings.value?.biometricEnabled ?: false)) {
            _isLocked.value = true
        }
    }

    private fun sha256(input: String, salt: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest((input + salt).toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    // --- DATA STREAM DECLARATIONS ---
    val allAccounts = repository.allAccounts
        .onEach { list -> saveCachedAccounts(list) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = loadCachedAccounts()
        )

    val allCategories = repository.allCategories
        .onEach { list -> saveCachedCategories(list) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = loadCachedCategories()
        )

    val allTransactions = repository.allTransactions
        .onEach { list -> saveCachedTransactions(list) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = loadCachedTransactions()
        )

    val allBudgets = repository.allBudgets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allSavingsGoals = repository.allSavingsGoals.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allRecurringTransactions = repository.allRecurringTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun getInitialSettings(): Settings {
        val themeMode = prefs.getString("theme_mode", "system") ?: "system"
        val colorPalette = prefs.getString("color_palette", "Default") ?: "Default"
        return Settings(
            themeMode = themeMode,
            colorPalette = colorPalette
        )
    }

    val settings = repository.appSettings
        .onEach { settingsObj ->
            if (settingsObj != null) {
                prefs.edit()
                    .putString("theme_mode", settingsObj.themeMode)
                    .putString("color_palette", settingsObj.colorPalette)
                    .apply()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = getInitialSettings()
        )

    // --- SEARCH AND FILTER MUTABLES ---
    val searchQuery = MutableStateFlow("")
    val filterType = MutableStateFlow("All") // All, Income, Expense
    val filterCategoryIds = MutableStateFlow<Set<Int>>(emptySet())
    val filterAccountIds = MutableStateFlow<Set<Int>>(emptySet())
    val filterDateRange = MutableStateFlow<Pair<Long?, Long?>?>(null) // Epoch milliseconds
    val filterTags = MutableStateFlow<Set<String>>(emptySet())
    
    val selectedHomeDateFilter = MutableStateFlow(prefs.getString("home_date_filter_option", "Monthly") ?: "Monthly")

    fun setHomeDateFilterOption(option: String) {
        selectedHomeDateFilter.value = option
        prefs.edit().putString("home_date_filter_option", option).apply()
    }

    val allTags: StateFlow<Set<String>> = allTransactions.map { txList ->
        txList.flatMap { tx ->
            tx.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }.toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    fun toggleFilterTag(tag: String) {
        val current = filterTags.value
        filterTags.value = if (current.contains(tag)) current - tag else current + tag
    }

    fun clearFilterTags() {
        filterTags.value = emptySet()
    }

    // REACIVE FILTERED TRANSACTIONS
    val filteredTransactions: StateFlow<List<TransactionWithDetails>> = combine(
        allTransactions,
        searchQuery,
        filterType,
        filterCategoryIds,
        filterAccountIds,
        filterDateRange,
        filterTags
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val txs = flows[0] as List<TransactionWithDetails>
        val query = flows[1] as String
        val type = flows[2] as String
        @Suppress("UNCHECKED_CAST")
        val catIds = flows[3] as Set<Int>
        @Suppress("UNCHECKED_CAST")
        val accIds = flows[4] as Set<Int>
        @Suppress("UNCHECKED_CAST")
        val dateRange = flows[5] as Pair<Long?, Long?>?
        @Suppress("UNCHECKED_CAST")
        val selectedTags = flows[6] as Set<String>

        var result = txs

        // 1. Filter by Search Query
        if (query.isNotBlank()) {
            val lowercaseQuery = query.lowercase().trim()
            result = result.filter {
                it.note.lowercase().contains(lowercaseQuery) ||
                it.categoryName.lowercase().contains(lowercaseQuery) ||
                it.accountName.lowercase().contains(lowercaseQuery) ||
                it.tags.lowercase().contains(lowercaseQuery)
            }
        }

        // 2. Filter by Transaction Type
        if (type != "All") {
            val lowercaseType = type.lowercase()
            result = result.filter { it.type == lowercaseType }
        }

        // 3. Filter by Category IDs
        if (catIds.isNotEmpty()) {
            result = result.filter { catIds.contains(it.categoryId) }
        }

        // 4. Filter by Account IDs
        if (accIds.isNotEmpty()) {
            result = result.filter { accIds.contains(it.accountId) }
        }

        // 5. Filter by Date Range
        if (dateRange != null) {
            val startMs = dateRange.first
            val endMs = dateRange.second
            if (startMs != null && endMs != null) {
                result = result.filter {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        val txDate = sdf.parse(it.transactionDate)
                        val txMs = txDate?.time ?: 0L
                        txMs in startMs..endMs
                    } catch (e: Exception) {
                        true
                    }
                }
            }
        }

        // 6. Filter by Custom Tags
        if (selectedTags.isNotEmpty()) {
            result = result.filter { tx ->
                val txTags = tx.tags.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                selectedTags.any { txTags.contains(it.lowercase()) }
            }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- LOCAL NOTIFICATION FEEDBACK ---
    private val _notificationMessage = MutableSharedFlow<String>()
    val notificationMessage = _notificationMessage.asSharedFlow()

    data class SnackbarEvent(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    )

    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(SnackbarEvent(message = message))
        }
    }

    fun showSnackbar(message: String, actionLabel: String, onAction: () -> Unit) {
        viewModelScope.launch {
            _snackbarEvent.emit(SnackbarEvent(message = message, actionLabel = actionLabel, onAction = onAction))
        }
    }

    fun triggerLocalNotification(message: String) {
        viewModelScope.launch {
            if (settings.value?.notificationsEnabled == true) {
                _notificationMessage.emit(message)
            }
        }
    }

    // --- RECURRING TRANSACTIONS AUTO SCHEDULER ---
    fun processRecurringTransactions() {
        viewModelScope.launch {
            repository.processRecurringTransactions()
        }
    }

    // --- MUTATION METHODS ---

    // Transactions
    fun insertTransaction(
        amount: Double,
        type: String,
        categoryId: Int,
        accountId: Int,
        note: String,
        dateStr: String, // format "yyyy-MM-dd"
        attachmentPath: String? = null,
        tags: String = ""
    ) {
        viewModelScope.launch {
            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            val txDateTime = if (dateStr.contains("T")) dateStr else dateStr + "T09:00:00"
            val tx = Transaction(
                amount = amount,
                type = type,
                categoryId = categoryId,
                accountId = accountId,
                note = note,
                transactionDate = txDateTime,
                createdAt = nowStr,
                updatedAt = nowStr,
                attachmentPath = attachmentPath,
                tags = tags
            )
            repository.insertTransaction(tx)
            
            // Trigger check for budget limits
            checkBudgetLimits(categoryId, type, amount, dateStr)
        }
    }

    fun updateTransaction(
        id: Int,
        amount: Double,
        type: String,
        categoryId: Int,
        accountId: Int,
        note: String,
        dateStr: String,
        attachmentPath: String? = null,
        tags: String = ""
    ) {
        viewModelScope.launch {
            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            val txDateTime = if (dateStr.contains("T")) dateStr else dateStr + "T09:00:00"
            val tx = Transaction(
                id = id,
                amount = amount,
                type = type,
                categoryId = categoryId,
                accountId = accountId,
                note = note,
                transactionDate = txDateTime,
                createdAt = nowStr, // Keep simple
                updatedAt = nowStr,
                attachmentPath = attachmentPath,
                tags = tags
            )
            repository.updateTransaction(tx)
        }
    }

    fun deleteTransaction(transactionId: Int) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
        }
    }

    fun restoreTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
        }
    }

    // Accounts
    fun insertAccount(name: String, type: String, openingBalance: Double, color: String, icon: String) {
        viewModelScope.launch {
            val account = Account(
                name = name,
                type = type,
                openingBalance = openingBalance,
                currentBalance = openingBalance,
                color = color,
                icon = icon
            )
            repository.insertAccount(account)
        }
    }

    fun updateAccount(id: Int, name: String, type: String, openingBalance: Double, currentBalance: Double, color: String, icon: String) {
        viewModelScope.launch {
            val account = Account(
                id = id,
                name = name,
                type = type,
                openingBalance = openingBalance,
                currentBalance = currentBalance,
                color = color,
                icon = icon
            )
            repository.updateAccount(account)
        }
    }

    fun deleteAccount(account: Account, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteAccount(account)
            if (success) {
                onSuccess()
            } else {
                onFailure("Cannot delete account: transactions reference this account. Please reassign transactions first.")
            }
        }
    }

    // Categories
    fun insertCategory(name: String, type: String, color: String, icon: String) {
        viewModelScope.launch {
            val category = Category(
                name = name,
                type = type,
                color = color,
                icon = icon,
                isDefault = false
            )
            repository.insertCategory(category)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun updateCategoryOrders(categories: List<Category>) {
        viewModelScope.launch {
            val updatedCategories = categories.mapIndexed { index, category ->
                category.copy(orderIndex = index)
            }
            repository.updateCategories(updatedCategories)
        }
    }

    fun deleteCategory(category: Category, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteCategory(category)
            if (success) {
                onSuccess()
            } else {
                onFailure("Cannot delete category: transactions reference this category. Please reassign transactions first.")
            }
        }
    }

    // Budgets
    fun insertBudget(
        categoryId: Int?, 
        amount: Double, 
        month: Int, 
        year: Int, 
        budgetType: String = "MONTHLY",
        startDate: Long? = null,
        endDate: Long? = null,
        budgetName: String? = null,
        id: Int = 0
    ) {
        viewModelScope.launch {
            val budget = Budget(
                id = id,
                categoryId = categoryId,
                budgetAmount = amount,
                month = month,
                year = year,
                budgetType = budgetType,
                startDate = startDate,
                endDate = endDate,
                budgetName = budgetName
            )
            repository.insertBudget(budget)
            triggerLocalNotification("Budget configured successfully!")
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }

    // Savings Goals
    fun insertSavingsGoal(title: String, targetAmount: Double, currentAmount: Double, targetDate: String, icon: String, color: String) {
        viewModelScope.launch {
            val goal = SavingsGoal(
                title = title,
                targetAmount = targetAmount,
                currentAmount = currentAmount,
                targetDate = targetDate,
                status = if (currentAmount >= targetAmount) "completed" else "active",
                icon = icon,
                color = color
            )
            repository.insertSavingsGoal(goal)
            
            if (currentAmount >= targetAmount) {
                triggerLocalNotification("Savings Goal '${title}' completed! 🎉")
            }
        }
    }

    fun updateSavingsGoal(id: Int, title: String, targetAmount: Double, currentAmount: Double, targetDate: String, status: String, icon: String, color: String) {
        viewModelScope.launch {
            val newStatus = if (currentAmount >= targetAmount) "completed" else "active"
            val goal = SavingsGoal(
                id = id,
                title = title,
                targetAmount = targetAmount,
                currentAmount = currentAmount,
                targetDate = targetDate,
                status = newStatus,
                icon = icon,
                color = color
            )
            repository.updateSavingsGoal(goal)
            if (newStatus == "completed" && status != "completed") {
                triggerLocalNotification("Savings Goal '${title}' completed! 🎉")
            }
        }
    }

    fun addSavingsGoalFunds(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch {
            val newAmount = goal.currentAmount + amount
            val newStatus = if (newAmount >= goal.targetAmount) "completed" else "active"
            repository.updateSavingsGoal(
                goal.copy(
                    currentAmount = newAmount,
                    status = newStatus
                )
            )
            if (newStatus == "completed" && goal.status != "completed") {
                triggerLocalNotification("Savings Goal '${goal.title}' completed! 🎉")
            } else {
                triggerLocalNotification("Added funds to '${goal.title}'")
            }
        }
    }

    fun deleteSavingsGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(goal)
        }
    }

    // Recurring Rules
    fun insertRecurringRule(amount: Double, type: String, categoryId: Int, accountId: Int, note: String, frequency: String, startDate: String) {
        viewModelScope.launch {
            val rule = RecurringTransaction(
                amount = amount,
                type = type,
                categoryId = categoryId,
                accountId = accountId,
                note = note,
                frequency = frequency,
                nextExecutionDate = startDate,
                enabled = true
            )
            repository.insertRecurringTransaction(rule)
        }
    }

    fun updateRecurringRule(rule: RecurringTransaction) {
        viewModelScope.launch {
            repository.updateRecurringTransaction(rule)
        }
    }

    fun deleteRecurringRule(rule: RecurringTransaction) {
        viewModelScope.launch {
            repository.deleteRecurringTransaction(rule)
        }
    }

    // Settings
    fun updateSettings(themeMode: String, currency: String, notificationsEnabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettingsDirect() ?: Settings()
            repository.updateSettings(
                current.copy(
                    themeMode = themeMode,
                    currency = currency,
                    notificationsEnabled = notificationsEnabled
                )
            )
        }
    }

    fun updateColorPalette(colorPalette: String) {
        viewModelScope.launch {
            val current = repository.getSettingsDirect() ?: Settings()
            repository.updateSettings(
                current.copy(
                    colorPalette = colorPalette
                )
            )
        }
    }

    fun updatePinSettings(enabled: Boolean, pin: String? = null) {
        viewModelScope.launch {
            val current = repository.getSettingsDirect() ?: Settings()
            if (enabled && !pin.isNullOrEmpty()) {
                setPin(pin)
                repository.updateSettings(current.copy(pinEnabled = true))
            } else {
                disablePin()
                repository.updateSettings(current.copy(pinEnabled = false))
            }
        }
    }

    fun updateBiometricSettings(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettingsDirect() ?: Settings()
            repository.updateSettings(current.copy(biometricEnabled = enabled))
        }
    }

    // --- BUDGET TRIGGER EVALUATION ---
    private suspend fun checkBudgetLimits(categoryId: Int, type: String, amount: Double, dateStr: String) {
        if (type != "expense") return

        try {
            // Get Month and Year from dateStr (format "yyyy-MM-dd")
            val parts = dateStr.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()

            // 1. Check Category Budget
            val catBudget = repository.getBudgetForCategory(categoryId, month, year)
            if (catBudget != null) {
                // SUM all expenses for this category in this month/year
                val txs = allTransactions.value
                val categorySum = txs.filter {
                    it.categoryId == categoryId && 
                    it.type == "expense" && 
                    it.transactionDate.startsWith("$year-${String.format("%02d", month)}")
                }.sumOf { it.amount } + amount

                if (categorySum > catBudget.budgetAmount) {
                    triggerLocalNotification("Alert: Monthly budget for category exceeded! Limit: ${settings.value?.currency}${catBudget.budgetAmount}")
                }
            }

            // 2. Check Overall Budget (categoryId is null)
            val overallBudget = repository.getBudgetForCategory(null, month, year)
            if (overallBudget != null) {
                val txs = allTransactions.value
                val totalExpenses = txs.filter {
                    it.type == "expense" && 
                    it.transactionDate.startsWith("$year-${String.format("%02d", month)}")
                }.sumOf { it.amount } + amount

                if (totalExpenses > overallBudget.budgetAmount) {
                    triggerLocalNotification("Warning: Total monthly budget exceeded! Limit: ${settings.value?.currency}${overallBudget.budgetAmount}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- BACKUP & RESTORE IO ---
    fun exportTransactionsToCsv(outStream: java.io.OutputStream, onComplete: (Boolean) -> Unit) {
        setFileProcessing(true, "Exporting transactions to CSV...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var success = false
            try {
                val txList = allTransactions.value
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(outStream))
                writer.write("Transaction ID,Date,Type,Amount,Note,Category,Account\n")
                txList.forEach { tx ->
                    val cleanNote = tx.note.replace("\"", "\"\"")
                    val cleanCategory = tx.categoryName.replace("\"", "\"\"")
                    val cleanAccount = tx.accountName.replace("\"", "\"\"")
                    writer.write("${tx.id},${tx.transactionDate},${tx.type},${tx.amount},\"$cleanNote\",\"$cleanCategory\",\"$cleanAccount\"\n")
                }
                writer.flush()
                writer.close()
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    outStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                setFileProcessing(false)
                onComplete(success)
            }
        }
    }

    fun exportTransactionsToPdf(outStream: java.io.OutputStream, onComplete: (Boolean) -> Unit) {
        setFileProcessing(true, "Exporting statement to PDF...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var success = false
            var logoBitmap: android.graphics.Bitmap? = null
            try {
                val txList = allTransactions.value.sortedBy { it.transactionDate }
                val pdfDocument = android.graphics.pdf.PdfDocument()
                
                // Define page parameters
                val pageWidth = 595 // A4 width in points (72 points/inch)
                val pageHeight = 842 // A4 height in points
                
                // Define layout constraints
                val margin = 40f
                val rowHeight = 24f
                val headerHeight = 35f
                val yStart = 140f
                val rowsPerPage = ((pageHeight - yStart - margin) / rowHeight).toInt()
                
                // Decode logo resource at a very small size to minimize PDF file size
                val context = getApplication<Application>()
                logoBitmap = try {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeResource(context.resources, com.expenso.app.R.drawable.logo, options)
                    
                    val reqWidth = 64
                    val reqHeight = 64
                    var inSampleSize = 1
                    if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                        val halfHeight = options.outHeight / 2
                        val halfWidth = options.outWidth / 2
                        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                            inSampleSize *= 2
                        }
                    }
                    
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    }
                    val rawBitmap = android.graphics.BitmapFactory.decodeResource(context.resources, com.expenso.app.R.drawable.logo, decodeOptions)
                    if (rawBitmap != null) {
                        val scaled = android.graphics.Bitmap.createScaledBitmap(rawBitmap, 64, 64, true)
                        if (rawBitmap != scaled) {
                            rawBitmap.recycle()
                        }
                        scaled
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
                
                // Split transactions into pages
                val chunks = if (txList.isEmpty()) listOf(emptyList()) else txList.chunked(rowsPerPage)
                
                chunks.forEachIndexed { pageIndex, pageTxList ->
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    
                    // Set up paints
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        isFilterBitmap = true
                    }
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 10f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                    }
                    val boldTextPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 10f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    }
                    val titlePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#121212")
                        textSize = 24f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    }
                    val subtitlePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 10f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                    }
                    
                    // Header (Only on page 1)
                    if (pageIndex == 0) {
                        var titleStartX = margin
                        if (logoBitmap != null) {
                            try {
                                val dstRect = android.graphics.RectF(margin, 28f, margin + 32f, 28f + 32f)
                                canvas.drawBitmap(logoBitmap, null, dstRect, paint)
                                titleStartX = margin + 42f
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        canvas.drawText("EXPENSO", titleStartX, 54f, titlePaint)
                        val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        canvas.drawText("Generated on: $currentDate", margin, 78f, subtitlePaint)
                        canvas.drawText("Total Transactions: ${txList.size}", margin, 92f, subtitlePaint)
                        
                        // Draw decorative bar
                        paint.color = android.graphics.Color.parseColor("#006B5D") // Brand Teal
                        canvas.drawRect(margin, 116f, pageWidth - margin, 120f, paint)
                    } else {
                        var titlePageStartX = margin
                        if (logoBitmap != null) {
                            try {
                                val dstRect = android.graphics.RectF(margin, 30f, margin + 18f, 30f + 18f)
                                canvas.drawBitmap(logoBitmap, null, dstRect, paint)
                                titlePageStartX = margin + 24f
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        canvas.drawText("EXPENSO - Transactions Statement (Page ${pageIndex + 1})", titlePageStartX, 44f, boldTextPaint)
                        paint.color = android.graphics.Color.parseColor("#006B5D") // Brand Teal
                        canvas.drawRect(margin, 52f, pageWidth - margin, 54f, paint)
                    }
                    
                    // Draw Table Header
                    val headerY = if (pageIndex == 0) yStart else 70f
                    paint.color = android.graphics.Color.parseColor("#EEEEEE")
                    canvas.drawRect(margin, headerY, pageWidth - margin, headerY + headerHeight, paint)
                    
                    val colDateX = margin + 5f
                    val colTimeX = margin + 75f
                    val colAccountX = margin + 135f
                    val colCategoryX = margin + 235f
                    val colTypeX = margin + 345f
                    val colAmountX = margin + 425f
                    
                    val headerTextY = headerY + 22f
                    canvas.drawText("Date", colDateX, headerTextY, boldTextPaint)
                    canvas.drawText("Time", colTimeX, headerTextY, boldTextPaint)
                    canvas.drawText("Account", colAccountX, headerTextY, boldTextPaint)
                    canvas.drawText("Category", colCategoryX, headerTextY, boldTextPaint)
                    canvas.drawText("Type", colTypeX, headerTextY, boldTextPaint)
                    canvas.drawText("Amount (₹)", colAmountX, headerTextY, boldTextPaint)
                    
                    // Draw rows
                    var currentY = headerY + headerHeight
                    pageTxList.forEachIndexed { index, tx ->
                        val isEven = index % 2 == 0
                        if (isEven) {
                            paint.color = android.graphics.Color.parseColor("#F9F9F9")
                            canvas.drawRect(margin, currentY, pageWidth - margin, currentY + rowHeight, paint)
                        }
                        
                        val rowTextY = currentY + 16f
                        
                        // Date & Time Split
                        val parts = tx.transactionDate.split("T")
                        val datePart = parts.getOrNull(0) ?: ""
                        val timePart = parts.getOrNull(1)?.take(5) ?: ""
                        
                        // Date
                        canvas.drawText(datePart, colDateX, rowTextY, textPaint)
                        
                        // Time
                        canvas.drawText(timePart, colTimeX, rowTextY, textPaint)
                        
                        // Account
                        val accText = if (tx.accountName.length > 12) tx.accountName.take(10) + ".." else tx.accountName
                        canvas.drawText(accText, colAccountX, rowTextY, textPaint)
                        
                        // Category
                        val catText = if (tx.categoryName.length > 15) tx.categoryName.take(13) + ".." else tx.categoryName
                        canvas.drawText(catText, colCategoryX, rowTextY, textPaint)
                        
                        // Type
                        val typeUpper = tx.type.uppercase()
                        val typePaint = android.graphics.Paint(textPaint).apply {
                            color = if (tx.type.lowercase() == "income") {
                                android.graphics.Color.parseColor("#00796B") // Teal/Green
                            } else {
                                android.graphics.Color.parseColor("#D32F2F") // Red
                            }
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }
                        canvas.drawText(typeUpper, colTypeX, rowTextY, typePaint)
                        
                        // Amount
                        val formattedAmount = String.format("₹%.2f", tx.amount)
                        canvas.drawText(formattedAmount, colAmountX, rowTextY, textPaint)
                        
                        currentY += rowHeight
                    }
                    
                    // Draw Page number at bottom right
                    val footerY = pageHeight - 20f
                    canvas.drawText("Page ${pageIndex + 1} of ${chunks.size}", pageWidth - margin - 60f, footerY, subtitlePaint)
                    
                    pdfDocument.finishPage(page)
                }
                
                pdfDocument.writeTo(outStream)
                pdfDocument.close()
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    logoBitmap?.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    outStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                setFileProcessing(false)
                onComplete(success)
            }
        }
    }

    fun importTransactionsFromCsv(inStream: java.io.InputStream, onComplete: (Boolean, String) -> Unit) {
        setFileProcessing(true, "Importing transactions from CSV...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var success = false
            var message = ""
            try {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inStream))
                val lines = reader.readLines()
                if (lines.isEmpty()) {
                    onComplete(false, "CSV file is empty")
                    return@launch
                }

                // Check header to identify columns or map them
                val header = lines.first().lowercase()
                val hasHeader = header.contains("amount") || header.contains("type") || header.contains("date")
                val dataLines = if (hasHeader) lines.drop(1) else lines

                var successCount = 0
                var errorCount = 0

                // Parse each line
                dataLines.forEach { line ->
                    if (line.trim().isEmpty()) return@forEach
                    try {
                        val tokens = parseCsvLine(line)
                        if (tokens.size >= 5) {
                            val dateStrRaw: String
                            val typeRaw: String
                            val amountRaw: String
                            val noteRaw: String
                            val categoryRaw: String
                            val accountRaw: String

                            if (tokens.size >= 7 && hasHeader && (header.startsWith("transaction id") || header.contains("id"))) {
                                dateStrRaw = tokens[1].trim()
                                typeRaw = tokens[2].trim()
                                amountRaw = tokens[3].trim()
                                noteRaw = tokens[4].trim()
                                categoryRaw = tokens[5].trim()
                                accountRaw = tokens[6].trim()
                            } else {
                                dateStrRaw = tokens.getOrNull(0)?.trim() ?: ""
                                typeRaw = tokens.getOrNull(1)?.trim() ?: "expense"
                                amountRaw = tokens.getOrNull(2)?.trim() ?: "0.0"
                                noteRaw = tokens.getOrNull(3)?.trim() ?: ""
                                categoryRaw = tokens.getOrNull(4)?.trim() ?: "Other"
                                accountRaw = tokens.getOrNull(5)?.trim() ?: "Cash"
                            }

                            val amount = amountRaw.toDoubleOrNull() ?: 0.0
                            val type = if (typeRaw.lowercase().contains("income")) "income" else "expense"
                            val note = noteRaw.removeSurrounding("\"")
                            val catName = categoryRaw.removeSurrounding("\"").ifBlank { "Other" }
                            val accName = accountRaw.removeSurrounding("\"").ifBlank { "Cash" }

                            // Format Date
                            var finalDate = dateStrRaw.removeSurrounding("\"")
                            if (finalDate.isBlank()) {
                                finalDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                            } else if (!finalDate.contains("T")) {
                                finalDate = "${finalDate}T09:00:00"
                            }

                            // 1. Find or create Account
                            var account = repository.getAccountByNameDirect(accName)
                            if (account == null) {
                                val newAcc = Account(
                                    name = accName,
                                    type = "Other",
                                    openingBalance = 0.0,
                                    currentBalance = 0.0,
                                    color = "#4CAF50",
                                    icon = "wallet"
                                )
                                val accId = repository.insertAccountDirect(newAcc)
                                account = newAcc.copy(id = accId.toInt())
                            }

                            // 2. Find or create Category
                            var category = repository.getCategoryByNameDirect(catName, type)
                            if (category == null) {
                                val newCat = Category(
                                    name = catName,
                                    type = type,
                                    color = if (type == "income") "#4CAF50" else "#F44336",
                                    icon = if (type == "income") "trending_up" else "trending_down"
                                )
                                val catId = repository.insertCategoryDirect(newCat)
                                category = newCat.copy(id = catId.toInt())
                            }

                            // 3. Insert transaction
                            val tx = Transaction(
                                amount = amount,
                                type = type,
                                categoryId = category.id,
                                accountId = account.id,
                                note = note,
                                transactionDate = finalDate,
                                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                                updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                            )
                            repository.insertTransaction(tx)
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorCount++
                    }
                }
                success = true
                message = "Successfully imported $successCount transactions. Failed: $errorCount"
            } catch (e: Exception) {
                e.printStackTrace()
                success = false
                message = "Import failed: ${e.localizedMessage}"
            } finally {
                try {
                    inStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                setFileProcessing(false)
                onComplete(success, message)
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < line.length && line[i + 1] == '\"') {
                        curVal.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString())
                    curVal = StringBuilder()
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }

    private fun saveCachedAccounts(accounts: List<Account>) {
        val array = org.json.JSONArray()
        accounts.forEach { array.put(it.toJson()) }
        prefs.edit().putString("cached_accounts", array.toString()).apply()
    }

    private fun loadCachedAccounts(): List<Account> {
        val jsonStr = prefs.getString("cached_accounts", null) ?: return emptyList()
        val list = mutableListOf<Account>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(jsonToAccount(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveCachedCategories(categories: List<Category>) {
        val array = org.json.JSONArray()
        categories.forEach { array.put(it.toJson()) }
        prefs.edit().putString("cached_categories", array.toString()).apply()
    }

    private fun loadCachedCategories(): List<Category> {
        val jsonStr = prefs.getString("cached_categories", null) ?: return emptyList()
        val list = mutableListOf<Category>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(jsonToCategory(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveCachedTransactions(transactions: List<TransactionWithDetails>) {
        val array = org.json.JSONArray()
        transactions.forEach { array.put(it.toJson()) }
        prefs.edit().putString("cached_transactions", array.toString()).apply()
    }

    private fun loadCachedTransactions(): List<TransactionWithDetails> {
        val jsonStr = prefs.getString("cached_transactions", null) ?: return emptyList()
        val list = mutableListOf<TransactionWithDetails>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(jsonToTransactionWithDetails(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

private fun Account.toJson(): org.json.JSONObject {
    return org.json.JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("openingBalance", openingBalance)
        put("currentBalance", currentBalance)
        put("icon", icon)
        put("color", color)
    }
}

private fun jsonToAccount(json: org.json.JSONObject): Account {
    return Account(
        id = json.getInt("id"),
        name = json.getString("name"),
        type = json.getString("type"),
        openingBalance = json.getDouble("openingBalance"),
        currentBalance = json.getDouble("currentBalance"),
        icon = json.getString("icon"),
        color = json.getString("color")
    )
}

private fun Category.toJson(): org.json.JSONObject {
    return org.json.JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("icon", icon)
        put("color", color)
        put("isDefault", isDefault)
        put("orderIndex", orderIndex)
    }
}

private fun jsonToCategory(json: org.json.JSONObject): Category {
    return Category(
        id = json.getInt("id"),
        name = json.getString("name"),
        type = json.getString("type"),
        icon = json.getString("icon"),
        color = json.getString("color"),
        isDefault = json.optBoolean("isDefault", false),
        orderIndex = json.optInt("orderIndex", 0)
    )
}

private fun TransactionWithDetails.toJson(): org.json.JSONObject {
    return org.json.JSONObject().apply {
        put("id", id)
        put("amount", amount)
        put("type", type)
        put("categoryId", categoryId)
        put("accountId", accountId)
        put("note", note)
        put("transactionDate", transactionDate)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("attachmentPath", attachmentPath ?: "")
        put("tags", tags)
        put("categoryName", categoryName)
        put("categoryIcon", categoryIcon)
        put("categoryColor", categoryColor)
        put("accountName", accountName)
        put("accountIcon", accountIcon)
        put("accountColor", accountColor)
    }
}

private fun jsonToTransactionWithDetails(json: org.json.JSONObject): TransactionWithDetails {
    return TransactionWithDetails(
        id = json.getInt("id"),
        amount = json.getDouble("amount"),
        type = json.getString("type"),
        categoryId = json.getInt("categoryId"),
        accountId = json.getInt("accountId"),
        note = json.getString("note"),
        transactionDate = json.getString("transactionDate"),
        createdAt = json.getString("createdAt"),
        updatedAt = json.getString("updatedAt"),
        attachmentPath = json.optString("attachmentPath").let { if (it.isEmpty()) null else it },
        tags = json.optString("tags", ""),
        categoryName = json.getString("categoryName"),
        categoryIcon = json.getString("categoryIcon"),
        categoryColor = json.getString("categoryColor"),
        accountName = json.getString("accountName"),
        accountIcon = json.getString("accountIcon"),
        accountColor = json.getString("accountColor")
    )
}
