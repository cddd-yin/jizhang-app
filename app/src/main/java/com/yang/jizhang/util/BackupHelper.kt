package com.yang.jizhang.util

import android.content.Context
import android.net.Uri
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.AppDatabase
import com.yang.jizhang.data.BudgetEntity
import com.yang.jizhang.data.RecurringEntity
import com.yang.jizhang.data.TxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 完整备份 / 恢复（v3.8.0）：账单 + 预算 + 周期规则 → 单个 JSON 写入「下载/Jizhang」。
 * CSV 导出只含账单且不含预算/周期；换机或重装时用本备份可整库搬家。
 *
 * 恢复为幂等合并：账单按 dedupeKey 唯一索引兜底、预算按 分类+周期、
 * 周期规则按 名称+金额+周期+锚点 判重，重复恢复不产生双份。
 */
object BackupHelper {

    /** 导出全部数据，返回可分享 Uri；空库返回 null */
    suspend fun exportFull(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.get(context)
            val txs = db.txDao().getAllOnce()
            val budgets = db.budgetDao().getAllOnce()
            val allRecurrings = db.recurringDao().getAllOnce()
            if (txs.isEmpty() && budgets.isEmpty() && allRecurrings.isEmpty()) return@withContext null

            val root = JSONObject().apply {
                put("app", "jizhang")
                put("format", 1)
                put("exportedAt", System.currentTimeMillis())
                put("txCount", txs.size)
                put("transactions", JSONArray().apply {
                    txs.forEach { t ->
                        put(
                            JSONObject().apply {
                                put("amountCents", t.amountCents)
                                put("isIncome", t.isIncome)
                                put("category", t.category)
                                put("note", t.note)
                                put("source", t.source)
                                put("time", t.time)
                                put("dedupeKey", t.dedupeKey)
                            }
                        )
                    }
                })
                put("budgets", JSONArray().apply {
                    budgets.forEach { b ->
                        put(
                            JSONObject().apply {
                                put("category", b.category)
                                put("period", b.period)
                                put("amountCents", b.amountCents)
                                put("enabled", b.enabled)
                            }
                        )
                    }
                })
                put("recurrings", JSONArray().apply {
                    allRecurrings.forEach { r ->
                        put(
                            JSONObject().apply {
                                put("title", r.title)
                                put("amountCents", r.amountCents)
                                put("isIncome", r.isIncome)
                                put("category", r.category)
                                put("note", r.note)
                                put("period", r.period)
                                put("anchor", r.anchor)
                                put("enabled", r.enabled)
                                put("lastRunAt", r.lastRunAt)
                            }
                        )
                    }
                })
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            BackupStorage.writeText(context, "Jizhang_完整备份_$stamp.json", root.toString(), "application/json")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 恢复（幂等合并），返回 Triple(新增账单, 新增预算, 新增周期规则)；文件不合法返回 null */
    suspend fun restoreFull(context: Context, uri: Uri): Triple<Int, Int, Int>? =
        withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() } ?: return@withContext null
                val root = JSONObject(text)
                if (root.optString("app") != "jizhang") return@withContext null
                val db = AppDatabase.get(context)

                // 账单：dedupeKey 唯一索引兜底
                var txNew = 0
                val txArr = root.optJSONArray("transactions") ?: JSONArray()
                for (i in 0 until txArr.length()) {
                    val o = txArr.getJSONObject(i)
                    val row = db.txDao().insertIgnore(
                        TxEntity(
                            amountCents = o.getLong("amountCents"),
                            isIncome = o.optBoolean("isIncome"),
                            category = o.optString("category", "其他"),
                            note = o.optString("note", ""),
                            source = o.optString("source", "手动"),
                            time = o.optLong("time", System.currentTimeMillis()),
                            dedupeKey = o.optString("dedupeKey")
                                .ifBlank { "RESTORE|$i|${o.optLong("time")}|${o.getLong("amountCents")}" },
                        )
                    )
                    if (row != -1L) txNew++
                }

                // 预算：分类+周期 相同视为已有
                var budgetNew = 0
                val existingBudgets = db.budgetDao().getAllOnce()
                val budgetArr = root.optJSONArray("budgets") ?: JSONArray()
                for (i in 0 until budgetArr.length()) {
                    val o = budgetArr.getJSONObject(i)
                    val cat = o.optString("category", "")
                    val period = o.optString("period", "monthly")
                    if (existingBudgets.any { it.category == cat && it.period == period }) continue
                    db.budgetDao().insert(
                        BudgetEntity(
                            category = cat,
                            period = period,
                            amountCents = o.getLong("amountCents"),
                            enabled = o.optBoolean("enabled", true),
                        )
                    )
                    budgetNew++
                }

                // 周期规则：名称+金额+周期+锚点 相同视为已有
                var recurNew = 0
                val existingRecur = db.recurringDao().getAllOnce()
                val recurArr = root.optJSONArray("recurrings") ?: JSONArray()
                for (i in 0 until recurArr.length()) {
                    val o = recurArr.getJSONObject(i)
                    val title = o.optString("title", "")
                    val amount = o.getLong("amountCents")
                    val period = o.optString("period", "monthly")
                    val anchor = o.optInt("anchor", 1)
                    if (existingRecur.any {
                            it.title == title && it.amountCents == amount &&
                                it.period == period && it.anchor == anchor
                        }
                    ) continue
                    db.recurringDao().insert(
                        RecurringEntity(
                            title = title,
                            amountCents = amount,
                            isIncome = o.optBoolean("isIncome"),
                            category = o.optString("category", "其他"),
                            note = o.optString("note", ""),
                            period = period,
                            anchor = anchor,
                            enabled = o.optBoolean("enabled", true),
                            lastRunAt = o.optLong("lastRunAt", 0L),
                        )
                    )
                    recurNew++
                }

                Triple(txNew, budgetNew, recurNew)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
