package com.yang.jizhang.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.util.CategoryLearner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BillImporter {
    sealed interface Result {
        data class Imported(val added: Int, val duplicates: Int, val skipped: Int) : Result
        data class Invalid(val message: String) : Result
    }

    suspend fun import(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // 部分文档提供方只开放文件流。
        }

        val tables = try {
            resolver.openInputStream(uri)?.use { SheetReader.read(it, displayName ?: uri.lastPathSegment) }
                ?: return@withContext Result.Invalid("无法打开账单文件")
        } catch (e: CancellationException) {
            throw e
        } catch (e: SheetReader.Unsupported) {
            return@withContext Result.Invalid(e.message.orEmpty())
        } catch (e: IllegalArgumentException) {
            return@withContext Result.Invalid(e.message.orEmpty())
        } catch (_: Exception) {
            return@withContext Result.Invalid("无法读取账单。ZIP 若有密码，请先解压")
        }

        val zone = ZoneId.systemDefault()
        val parsed = tables.mapNotNull { BillTableParser.parse(it, zone) }
        if (parsed.isEmpty()) return@withContext Result.Invalid("找不到账单表头，请检查日期和金额列")
        if (parsed.all { it.bills.isEmpty() }) return@withContext Result.Invalid("账单没有有效的交易，请检查日期、金额和收支")

        val db = (context.applicationContext as JizhangApp).db
        val dao = db.txDao()
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(zone)
        val digest = MessageDigest.getInstance("SHA-256")
        db.withTransaction {
            var added = 0
            var duplicates = 0
            val occurrences = mutableMapOf<String, Int>()
            for (table in parsed) {
                for (bill in table.bills) {
                    val note = bill.party.ifBlank { bill.description }.ifBlank { Source.IMPORT }
                    val details = if (bill.transactionId.isNotBlank()) listOf(bill.transactionId)
                        else listOf(bill.party, bill.description)
                    val identity = listOf(bill.time.toString(), bill.cents.toString(), bill.income.toString()) + details
                    val hash = digest.digest(identity.joinToString("") { "${it.length}:$it" }.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    val count = if (bill.transactionId.isBlank()) {
                        occurrences.merge(hash, 1, Int::plus) ?: 1
                    } else 1

                    val legacyMatch = count == 1 && listOfNotNull(bill.time, bill.legacyExcelTime).distinct().any { time ->
                        val key = "IMPORT|${Source.IMPORT}|${stamp.format(Instant.ofEpochMilli(time))}|${bill.cents}"
                        dao.findByDedupeKey(key)?.let { it.isIncome == bill.income && it.note == note } == true
                    }
                    val near = dao.getByTimeRangeOnce(bill.time - 120_000, bill.time + 120_000)
                    val exists = legacyMatch || near.any {
                        it.amountCents == bill.cents && it.isIncome == bill.income && it.source != Source.IMPORT
                    }
                    if (exists) {
                        duplicates++
                        continue
                    }

                    val id = dao.insertIgnore(
                        TxEntity(
                            amountCents = bill.cents,
                            isIncome = bill.income,
                            category = bill.category.ifBlank {
                                CategoryLearner.hint(context, bill.party) ?: CategoryRules.guess(bill.party, bill.description)
                            },
                            note = note,
                            source = Source.IMPORT,
                            time = bill.time,
                            dedupeKey = "IMPORT2|$hash|$count",
                        )
                    )
                    if (id == -1L) duplicates++ else added++
                }
            }
            Result.Imported(added, duplicates, parsed.sumOf { it.skipped })
        }
    }
}
