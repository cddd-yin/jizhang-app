package com.yang.jizhang.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** 记账来源 */
object Source {
    const val MANUAL = "手动"
    const val ALIPAY = "支付宝"
    const val WECHAT = "微信"
    const val QQ = "QQ"
    const val IMPORT = "导入"
    const val RECUR = "周期"
}

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["dedupeKey"], unique = true)]
)
data class TxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 金额，单位：分（避免浮点误差） */
    val amountCents: Long,
    /** true = 收入 */
    val isIncome: Boolean,
    val category: String,
    val note: String,
    /** 来源：Source 中常量 */
    val source: String,
    /** 交易时间（毫秒） */
    val time: Long,
    /** 去重键：来源+金额+时间(分)+内容摘要 */
    val dedupeKey: String,
)

/** 预算：category 为空串表示总预算；period = "monthly" / "weekly" */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 预算所属分类（空串 = 总预算） */
    val category: String,
    /** 周期：monthly / weekly */
    val period: String,
    val amountCents: Long,
    val enabled: Boolean = true,
)

/** 周期记账规则：period = "monthly"（anchor=1..28 日）/ "weekly"（anchor=1..7，周一=1） */
@Entity(tableName = "recurrings")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 规则名（如「房租」「视频会员」） */
    val title: String,
    val amountCents: Long,
    val isIncome: Boolean,
    val category: String,
    val note: String,
    /** 周期：monthly / weekly */
    val period: String,
    /** 锚点：monthly=每月几号（1..28），weekly=周几（1=周一..7=周日） */
    val anchor: Int,
    val enabled: Boolean = true,
    /** 上次补账检查时间（毫秒），0 = 从未执行 */
    val lastRunAt: Long = 0,
)

@Dao
interface TxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tx: TxEntity): Long

    @Update
    suspend fun update(tx: TxEntity)

    @Delete
    suspend fun delete(tx: TxEntity)

    @Query("SELECT * FROM transactions WHERE time BETWEEN :start AND :end ORDER BY time DESC")
    fun watchRange(start: Long, end: Long): Flow<List<TxEntity>>

    @Query("SELECT * FROM transactions WHERE dedupeKey = :key LIMIT 1")
    suspend fun findByDedupeKey(key: String): TxEntity?

    @Query("SELECT * FROM transactions WHERE time BETWEEN :start AND :end")
    suspend fun getByTimeRangeOnce(start: Long, end: Long): List<TxEntity>

    @Query("SELECT * FROM transactions WHERE time >= :start ORDER BY time")
    fun watchSince(start: Long): Flow<List<TxEntity>>

    @Query("SELECT * FROM transactions ORDER BY time DESC LIMIT :limit")
    fun watchRecent(limit: Int): Flow<List<TxEntity>>

    @Query("SELECT * FROM transactions ORDER BY time DESC")
    suspend fun getAllOnce(): List<TxEntity>

    @Query("SELECT * FROM transactions ORDER BY time DESC LIMIT 1")
    suspend fun latest(): TxEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets ORDER BY id")
    fun watchAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE enabled = 1 ORDER BY id")
    fun watchEnabled(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY id")
    suspend fun getAllOnce(): List<BudgetEntity>
}

@Dao
interface RecurringDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringEntity): Long

    @Update
    suspend fun update(rule: RecurringEntity)

    @Delete
    suspend fun delete(rule: RecurringEntity)

    @Query("SELECT * FROM recurrings ORDER BY id")
    fun watchAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurrings WHERE enabled = 1")
    suspend fun getEnabledOnce(): List<RecurringEntity>
}

@Database(
    entities = [TxEntity::class, BudgetEntity::class, RecurringEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txDao(): TxDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2：新增 budgets 与 recurrings 两表（不动 transactions，老数据无损） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`period` TEXT NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurrings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, " +
                        "`isIncome` INTEGER NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`period` TEXT NOT NULL, " +
                        "`anchor` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`lastRunAt` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "jizhang.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
