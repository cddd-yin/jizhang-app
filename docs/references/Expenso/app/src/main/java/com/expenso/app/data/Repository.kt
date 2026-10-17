package com.expenso.app.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Repository(private val db: AppDatabase) {

    val accountDao = db.accountDao()
    val categoryDao = db.categoryDao()
    val transactionDao = db.transactionDao()
    val budgetDao = db.budgetDao()
    val savingsGoalDao = db.savingsGoalDao()
    val recurringTransactionDao = db.recurringTransactionDao()
    val settingsDao = db.settingsDao()

    // --- ACCOUNTS ---
    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()

    suspend fun getAccountById(id: Int): Account? = accountDao.getAccountById(id)

    suspend fun getAccountByNameDirect(name: String): Account? = accountDao.getAccountByName(name)

    suspend fun insertAccount(account: Account): Long = accountDao.insertAccount(account)

    suspend fun insertAccountDirect(account: Account): Long = accountDao.insertAccount(account)

    suspend fun updateAccount(account: Account) = accountDao.updateAccount(account)

    suspend fun deleteAccount(account: Account): Boolean {
        // Guarded deletion: check if any transaction uses this account
        val count = transactionDao.getTransactionCountForAccount(account.id)
        if (count > 0) {
            return false // Deletion blocked
        }
        accountDao.deleteAccount(account)
        return true
    }

    // --- CATEGORIES ---
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    fun getCategoriesByType(type: String): Flow<List<Category>> = categoryDao.getCategoriesByType(type)

    suspend fun getCategoryById(id: Int): Category? = categoryDao.getCategoryById(id)

    suspend fun getCategoryByNameDirect(name: String, type: String): Category? = categoryDao.getCategoryByName(name, type)

    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)

    suspend fun insertCategoryDirect(category: Category): Long = categoryDao.insertCategory(category)

    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)

    suspend fun updateCategories(categories: List<Category>) = categoryDao.updateCategories(categories)

    suspend fun deleteCategory(category: Category): Boolean {
        // Guarded deletion
        val count = transactionDao.getTransactionCountForCategory(category.id)
        if (count > 0) {
            return false // Deletion blocked
        }
        categoryDao.deleteCategory(category)
        return true
    }

    // --- TRANSACTIONS ---
    val allTransactions: Flow<List<TransactionWithDetails>> = transactionDao.getAllTransactions()

    fun getTransactionsForAccount(accountId: Int): Flow<List<TransactionWithDetails>> =
        transactionDao.getTransactionsForAccount(accountId)

    suspend fun getTransactionById(id: Int): TransactionWithDetails? = transactionDao.getTransactionById(id)

    // TRANSACTION SAFE INSERT
    suspend fun insertTransaction(transaction: Transaction): Long {
        return db.withTransaction {
            val account = accountDao.getAccountById(transaction.accountId)
            if (account != null) {
                val newBalance = if (transaction.type == "income") {
                    account.currentBalance + transaction.amount
                } else {
                    account.currentBalance - transaction.amount
                }
                accountDao.updateAccount(account.copy(currentBalance = newBalance))
            }
            transactionDao.insertTransaction(transaction)
        }
    }

    // TRANSACTION SAFE UPDATE
    suspend fun updateTransaction(newTransaction: Transaction) {
        db.withTransaction {
            val oldTransaction = transactionDao.getRawTransactionById(newTransaction.id) ?: return@withTransaction
            
            // 1. Revert old transaction effect
            val oldAccount = accountDao.getAccountById(oldTransaction.accountId)
            if (oldAccount != null) {
                val revertedBalance = if (oldTransaction.type == "income") {
                    oldAccount.currentBalance - oldTransaction.amount
                } else {
                    oldAccount.currentBalance + oldTransaction.amount
                }
                accountDao.updateAccount(oldAccount.copy(currentBalance = revertedBalance))
            }

            // 2. Apply new transaction effect
            val newAccount = accountDao.getAccountById(newTransaction.accountId)
            if (newAccount != null) {
                val appliedBalance = if (newTransaction.type == "income") {
                    newAccount.currentBalance + newTransaction.amount
                } else {
                    newAccount.currentBalance - newTransaction.amount
                }
                accountDao.updateAccount(newAccount.copy(currentBalance = appliedBalance))
            }

            // 3. Update the transaction
            transactionDao.updateTransaction(newTransaction)
        }
    }

    // TRANSACTION SAFE DELETE
    suspend fun deleteTransaction(transactionId: Int) {
        db.withTransaction {
            val transaction = transactionDao.getRawTransactionById(transactionId) ?: return@withTransaction
            
            // Revert balance effect
            val account = accountDao.getAccountById(transaction.accountId)
            if (account != null) {
                val revertedBalance = if (transaction.type == "income") {
                    account.currentBalance - transaction.amount
                } else {
                    account.currentBalance + transaction.amount
                }
                accountDao.updateAccount(account.copy(currentBalance = revertedBalance))
            }

            // Delete transaction
            transactionDao.deleteTransaction(transaction)
        }
    }

    // --- BUDGETS ---
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()

    fun getBudgetsForPeriod(month: Int, year: Int): Flow<List<Budget>> = budgetDao.getBudgetsForPeriod(month, year)

    suspend fun getBudgetForCategory(categoryId: Int?, month: Int, year: Int): Budget? =
        budgetDao.getBudgetForCategory(categoryId, month, year)

    suspend fun insertBudget(budget: Budget): Long = budgetDao.insertBudget(budget)

    suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)

    // --- SAVINGS GOALS ---
    val allSavingsGoals: Flow<List<SavingsGoal>> = savingsGoalDao.getAllSavingsGoals()

    suspend fun getSavingsGoalById(id: Int): SavingsGoal? = savingsGoalDao.getSavingsGoalById(id)

    suspend fun insertSavingsGoal(savingsGoal: SavingsGoal): Long = savingsGoalDao.insertSavingsGoal(savingsGoal)

    suspend fun updateSavingsGoal(savingsGoal: SavingsGoal) = savingsGoalDao.updateSavingsGoal(savingsGoal)

    suspend fun deleteSavingsGoal(savingsGoal: SavingsGoal) = savingsGoalDao.deleteSavingsGoal(savingsGoal)

    // --- RECURRING TRANSACTIONS ---
    val allRecurringTransactions: Flow<List<RecurringTransaction>> = recurringTransactionDao.getAllRecurringTransactions()

    suspend fun insertRecurringTransaction(rule: RecurringTransaction): Long =
        recurringTransactionDao.insertRecurringTransaction(rule)

    suspend fun updateRecurringTransaction(rule: RecurringTransaction) =
        recurringTransactionDao.updateRecurringTransaction(rule)

    suspend fun deleteRecurringTransaction(rule: RecurringTransaction) =
        recurringTransactionDao.deleteRecurringTransaction(rule)

    // RUN RECURRING TRANSACTIONS SCHEDULER
    suspend fun processRecurringTransactions() {
        db.withTransaction {
            val nowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dueRules = recurringTransactionDao.getEnabledRecurringTransactions()
            for (rule in dueRules) {
                if (rule.nextExecutionDate <= nowStr) {
                    var execDateStr = rule.nextExecutionDate
                    
                    // While the execution date is in the past/today, generate transactions and advance
                    while (execDateStr <= nowStr) {
                        val transaction = Transaction(
                            amount = rule.amount,
                            type = rule.type,
                            categoryId = rule.categoryId,
                            accountId = rule.accountId,
                            note = "[Recurring] " + rule.note,
                            transactionDate = execDateStr + "T09:00:00",
                            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                            updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                        )
                        
                        // Insert and update account balance
                        val account = accountDao.getAccountById(rule.accountId)
                        if (account != null) {
                            val newBalance = if (rule.type == "income") {
                                account.currentBalance + rule.amount
                            } else {
                                account.currentBalance - rule.amount
                            }
                            accountDao.updateAccount(account.copy(currentBalance = newBalance))
                        }
                        transactionDao.insertTransaction(transaction)

                        // Advance execution date
                        execDateStr = advanceDate(execDateStr, rule.frequency)
                    }

                    // Update rule in database with new execution date
                    recurringTransactionDao.updateRecurringTransaction(
                        rule.copy(nextExecutionDate = execDateStr)
                    )
                }
            }
        }
    }

    private fun advanceDate(dateStr: String, frequency: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr
        val calendar = java.util.Calendar.getInstance()
        calendar.time = date
        when (frequency.lowercase()) {
            "daily" -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            "weekly" -> calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            "monthly" -> calendar.add(java.util.Calendar.MONTH, 1)
            "yearly" -> calendar.add(java.util.Calendar.YEAR, 1)
            else -> calendar.add(java.util.Calendar.MONTH, 1)
        }
        return sdf.format(calendar.time)
    }

    // --- SETTINGS ---
    val appSettings: Flow<Settings?> = settingsDao.getSettings()

    suspend fun getSettingsDirect(): Settings? = settingsDao.getSettingsDirect()

    suspend fun updateSettings(settings: Settings) = settingsDao.updateSettings(settings)
}
