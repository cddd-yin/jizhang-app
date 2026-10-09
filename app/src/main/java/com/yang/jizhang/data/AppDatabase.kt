package com.yang.jizhang.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** 记账来源 */
object Source {
    const val MANUAL = "手动"
    const val ALIPAY = "支付宝"
    const val WECHAT = "微信"
    const val QQ = "QQ"
    const val IMPORT = "导入"
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

    @Query("SELECT * FROM transactions ORDER BY time DESC LIMIT 1")
    suspend fun latest(): TxEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}

@Database(entities = [TxEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txDao(): TxDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "jizhang.db"
            ).build().also { instance = it }
        }
    }
}
