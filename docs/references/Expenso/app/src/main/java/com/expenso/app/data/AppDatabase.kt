package com.expenso.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Account::class,
        Category::class,
        Transaction::class,
        Budget::class,
        SavingsGoal::class,
        RecurringTransaction::class,
        Settings::class
    ],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add the colorPalette column with a default value of 'Default'
                db.execSQL("ALTER TABLE settings ADD COLUMN colorPalette TEXT NOT NULL DEFAULT 'Default'")
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE budgets ADD COLUMN budgetType TEXT NOT NULL DEFAULT 'MONTHLY'")
                db.execSQL("ALTER TABLE budgets ADD COLUMN startDate INTEGER")
                db.execSQL("ALTER TABLE budgets ADD COLUMN endDate INTEGER")
                db.execSQL("DROP INDEX IF EXISTS index_budgets_categoryId_month_year")
                
                // Migrate existing budgets
                db.execSQL("""
                    UPDATE budgets 
                    SET budgetType = 'MONTHLY',
                        startDate = CAST(strftime('%s', printf('%04d-%02d-01 00:00:00', year, month)) AS INTEGER) * 1000,
                        endDate = CAST(strftime('%s', datetime(printf('%04d-%02d-01 00:00:00', year, month), '+1 month', '-1 second')) AS INTEGER) * 1000
                """.trimIndent())
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE budgets RENAME COLUMN periodType TO budgetType")
                } catch (e: Exception) {
                    try {
                        db.execSQL("ALTER TABLE budgets ADD COLUMN budgetType TEXT NOT NULL DEFAULT 'MONTHLY'")
                    } catch (e2: Exception) {
                        // Already exists
                    }
                }
                try {
                    db.execSQL("ALTER TABLE budgets ADD COLUMN startDate INTEGER")
                } catch (e: Exception) {}
                try {
                    db.execSQL("ALTER TABLE budgets ADD COLUMN endDate INTEGER")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE budgets ADD COLUMN budgetName TEXT")
                } catch (e: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expenso_database"
                )
                .addCallback(DatabaseCallback())
                .addMigrations(MIGRATION_4_5, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedDatabase(db)
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                seedDatabase(db)
            }

            private fun seedDatabase(db: SupportSQLiteDatabase) {
                try {
                    // Seed Categories (Income) - Editable/Deletable by setting isDefault to 0
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Salary', 'income', 'payments', '#BFFCC6', 0, 1)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Business', 'income', 'storefront', '#CAFFBF', 0, 2)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Investments', 'income', 'trending_up', '#9BF6FF', 0, 3)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Gifts', 'income', 'card_giftcard', '#FFFFBA', 0, 4)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Freelance', 'income', 'computer', '#FFCAD4', 0, 5)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Rental', 'income', 'home', '#A0C4FF', 0, 6)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Refunds', 'income', 'receipt_long', '#D8B4FE', 0, 7)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Dividends', 'income', 'account_balance', '#B9FBC0', 0, 8)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Bonus', 'income', 'star', '#FBF8CC', 0, 9)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Other', 'income', 'more_horiz', '#E8AEB2', 0, 10)")

                    // Seed Categories (Expense) - Editable/Deletable by setting isDefault to 0
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Food & Dining', 'expense', 'restaurant', '#FFDFBA', 0, 1)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Shopping', 'expense', 'shopping_bag', '#FFB3BA', 0, 2)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Transport', 'expense', 'directions_car', '#BDB2FF', 0, 3)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Bills & Utilities', 'expense', 'receipt_long', '#D8B4FE', 0, 4)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Health & Fitness', 'expense', 'medical_services', '#FFCAD4', 0, 5)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Entertainment', 'expense', 'sports_esports', '#A0C4FF', 0, 6)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Groceries', 'expense', 'shopping_cart', '#D0F4DE', 0, 7)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Rent & Housing', 'expense', 'home', '#FCE1E4', 0, 8)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Education', 'expense', 'school', '#E2F0D9', 0, 9)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Insurance & Taxes', 'expense', 'shield', '#FCF6BD', 0, 10)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Electronics', 'expense', 'devices', '#A9DEF9', 0, 11)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Subscriptions', 'expense', 'subscriptions', '#E4C1F9', 0, 12)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Travel', 'expense', 'flight', '#D0F4EA', 0, 13)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Personal Care', 'expense', 'spa', '#FFD6A5', 0, 14)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Alcohol', 'expense', 'local_bar', '#FFFACD', 0, 15)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Apparel', 'expense', 'checkroom', '#FAFAD2', 0, 16)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Appliances', 'expense', 'kitchen', '#FFEFD5', 0, 17)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Art', 'expense', 'palette', '#FFE4B5', 0, 18)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Auto Maintenance', 'expense', 'build', '#FFDAB9', 0, 19)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Babysitting', 'expense', 'child_care', '#EEE8AA', 0, 20)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Bakery', 'expense', 'bakery_dining', '#F5DEB3', 0, 21)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Barber', 'expense', 'content_cut', '#F5F5DC', 0, 22)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Beauty', 'expense', 'face', '#FFDEAD', 0, 23)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Bicycle', 'expense', 'pedal_bike', '#F5F5F5', 0, 24)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Books', 'expense', 'book', '#E6E6FA', 0, 25)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Cable', 'expense', 'tv', '#D8BFD8', 0, 26)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Car Payment', 'expense', 'directions_car', '#DDA0DD', 0, 27)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Car Wash', 'expense', 'local_car_wash', '#EE82EE', 0, 28)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Charity', 'expense', 'favorite', '#DA70D6', 0, 29)")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, icon, color, isDefault, orderIndex) VALUES ('Other', 'expense', 'more_horiz', '#E8AEB2', 0, 30)")

                    // Seed Default Accounts
                    db.execSQL("INSERT INTO accounts (id, name, type, openingBalance, currentBalance, icon, color) VALUES (1, 'Cash', 'Cash', 0.0, 0.0, 'wallet', '#FFD6A5')")
                    db.execSQL("INSERT INTO accounts (id, name, type, openingBalance, currentBalance, icon, color) VALUES (2, 'Bank Account', 'Bank Account', 0.0, 0.0, 'account_balance', '#A0C4FF')")

                    // Seed Settings
                    db.execSQL("INSERT OR IGNORE INTO settings (id, themeMode, currency, pinEnabled, biometricEnabled, notificationsEnabled, colorPalette) VALUES (1, 'system', '₹', 0, 0, 1, 'Default')")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
