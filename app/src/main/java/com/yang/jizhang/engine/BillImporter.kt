package com.yang.jizhang.engine

import android.content.Context
import android.net.Uri
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.util.CategoryLearner
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.CategoryRules

/**
 * v4.0.0 引擎重写：支付宝/微信账单 CSV 导入（替代旧 CsvImporter）。
 * 表头列映射对象化 + ±2 分钟近邻去重（v3.6 引入的策略保留）。
 */
object BillImporter {

    private val charsets = listOf(Charset.forName("UTF-8"), Charset.forName("GBK"))

    suspend fun import(context: Context, uri: Uri, appLabel: String): Int {
        val text = readText(context, uri) ?: return 0
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val headerIdx = lines.indexOfFirst { it.contains("交易时间") }
        if (headerIdx < 0) return 0

        val cols = Columns.of(splitCsv(lines[headerIdx]))
        if (cols.time < 0 || cols.amount < 0) return 0

        val dao = (context.applicationContext as JizhangApp).db.txDao()
        var inserted = 0
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

        for (i in headerIdx + 1 until lines.size) {
            val cell = splitCsv(lines[i])
            if (cell.size < 2) continue
            val status = cell.getOrNull(cols.status).orEmpty()
            if (listOf("退款", "还款", "失败", "关闭", "不计收支").any { it in status }) continue

            val time = parseTime(cell.getOrNull(cols.time)) ?: continue
            val amountCents = parseCents(cell.getOrNull(cols.amount)) ?: continue
            if (amountCents <= 0) continue
            val flow = cell.getOrNull(cols.flow).orEmpty().ifBlank { "支出" }
            val income = flow.contains("收") && !flow.contains("支")
            val party = cell.getOrNull(cols.party).orEmpty()
            val goods = cell.getOrNull(cols.goods).orEmpty()

            // 近邻去重：±2 分钟内同额同向的非导入记录视为已被实时记过
            val near = dao.getByTimeRangeOnce(time - 120_000, time + 120_000)
            if (near.any { it.amountCents == amountCents && it.isIncome == income && it.source != Source.IMPORT }) {
                continue
            }

            val source = if (appLabel in listOf(Source.ALIPAY, Source.WECHAT, Source.QQ)) appLabel else Source.IMPORT
            val id = dao.insertIgnore(
                TxEntity(
                    amountCents = amountCents,
                    isIncome = income,
                    category = CategoryLearner.hint(context, party) ?: CategoryRules.guess(party, goods),
                    note = party.ifBlank { goods }.ifBlank { appLabel },
                    source = source,
                    time = time,
                    dedupeKey = "IMPORT|$appLabel|${stamp.format(time)}|$amountCents",
                )
            )
            if (id != -1L) inserted++
        }
        return inserted
    }

    /** 表头列映射 */
    private data class Columns(val time: Int, val flow: Int, val amount: Int, val party: Int, val goods: Int, val status: Int) {
        companion object {
            fun of(header: List<String>): Columns {
                fun find(vararg keys: String) = header.indexOfFirst { h -> keys.any { h.contains(it) } }
                return Columns(
                    time = find("交易时间"),
                    flow = find("收/支", "收支"),
                    amount = find("金额"),
                    party = find("交易对方"),
                    goods = find("商品说明", "商品", "备注"),
                    status = find("交易状态", "当前状态"),
                )
            }
        }
    }

    private fun readText(context: Context, uri: Uri): String? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        } else {
            charsets.firstOrNull { cs -> String(bytes, cs).let { it.contains("交易时间") || it.contains("收/支") } }
                ?.let { String(bytes, it) } ?: String(bytes, charsets.last())
        }
    } catch (_: Exception) {
        null
    }

    /** 引号内逗号兼容的行切分 */
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (quote && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ }
                    else quote = !quote
                }
                (c == ',' || c == '\t') && !quote -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private val timePatterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss")

    private fun parseTime(s: String?): Long? {
        val v = s?.trim() ?: return null
        for (p in timePatterns) {
            try {
                return SimpleDateFormat(p, Locale.CHINA).parse(v)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseCents(s: String?): Long? {
        val v = s?.replace("¥", "")?.replace("￥", "")?.replace(",", "")?.trim() ?: return null
        return v.toDoubleOrNull()?.let { Math.round(it * 100) }
    }
}
