package com.yang.jizhang.importer

import android.content.Context
import android.net.Uri
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 支付宝 / 微信账单 CSV 导入。
 *
 * 支付宝：App → 我的 → 账单 → 开具交易流水 → 邮箱收到 zip，解压得 .csv
 * 微信：  我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单 → 用于个人对账 → 邮箱收到 zip，解压得 .csv
 *
 * 两家 CSV 均为 GBK 编码、带引号的表格，本导入器按表头列名自适应解析。
 */
object CsvImporter {

    private val charsets = listOf(Charset.forName("UTF-8"), Charset.forName("GBK"))

    private fun readText(context: Context, uri: Uri): String? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        } catch (e: Exception) {
            return null
        }
        // BOM 处理
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charset.forName("UTF-8"))
        }
        for (cs in charsets) {
            val text = String(bytes, cs)
            if (text.contains("交易时间") || text.contains("收/支")) return text
        }
        return String(bytes, Charset.forName("GBK"))
    }

    /** 极简 CSV 行解析，支持引号内逗号 */
    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuote && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuote = !inQuote
                }
                (c == ',' || c == '\t') && !inQuote -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun parseTime(s: String): Long? {
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss")
        for (p in patterns) {
            try {
                return SimpleDateFormat(p, Locale.CHINA).parse(s.trim())?.time
            } catch (_: Exception) { }
        }
        return null
    }

    private fun parseAmount(s: String): Long? {
        val cleaned = s.replace("¥", "").replace("￥", "").replace(",", "").trim()
        val d = cleaned.toDoubleOrNull() ?: return null
        return Math.round(d * 100)
    }

    /**
     * 导入账单，返回成功入库条数。
     * source 标记为 [Source.IMPORT]，备注里带上 App 来源（支付宝/微信）。
     */
    suspend fun import(context: Context, uri: Uri, appLabel: String): Int {
        val text = readText(context, uri) ?: return 0
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val headerIdx = lines.indexOfFirst { it.contains("交易时间") }
        if (headerIdx < 0) return 0

        val header = splitCsvLine(lines[headerIdx]).map { it.trim() }
        fun col(vararg names: String): Int =
            header.indexOfFirst { h -> names.any { n -> h.contains(n) } }.takeIf { it >= 0 } ?: -1

        val cTime = col("交易时间")
        val cFlow = col("收/支", "收支")
        val cAmount = col("金额")
        val cParty = col("交易对方")
        val cGoods = col("商品说明", "商品", "备注")
        val cStatus = col("交易状态", "当前状态")
        if (cTime < 0 || cAmount < 0) return 0

        val db = (context.applicationContext as com.yang.jizhang.JizhangApp).db.txDao()
        var inserted = 0
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

        for (idx in (headerIdx + 1) until lines.size) {
            val cols = splitCsvLine(lines[idx])
            if (cols.size < 2) continue

            val status = if (cStatus >= 0) cols.getOrElse(cStatus) { "" } else ""
            if (listOf("退款", "还款", "失败", "关闭", "不计收支").any { it in status }) continue

            val time = parseTime(cols.getOrElse(cTime) { "" }) ?: continue
            val amountCents = parseAmount(cols.getOrElse(cAmount) { "" }) ?: continue
            if (amountCents <= 0) continue

            val flowStr = if (cFlow >= 0) cols.getOrElse(cFlow) { "" } else "支出"
            val isIncome = flowStr.contains("收") && !flowStr.contains("支")

            val party = if (cParty >= 0) cols.getOrElse(cParty) { "" } else ""
            val goods = if (cGoods >= 0) cols.getOrElse(cGoods) { "" } else ""
            val dedupeKey = "IMPORT|$appLabel|${df.format(time)}|$amountCents"

            val tx = TxEntity(
                amountCents = amountCents,
                isIncome = isIncome,
                category = CategoryRules.guess(party, goods),
                note = (party.ifBlank { goods }).ifBlank { appLabel },
                source = Source.IMPORT,
                time = time,
                dedupeKey = dedupeKey,
            )
            // 导入时同步来源标记（支付宝/微信），便于筛选
            val tagged = if (appLabel in listOf(Source.ALIPAY, Source.WECHAT, Source.QQ)) {
                tx.copy(source = appLabel)
            } else tx

            val ok = db.insertIgnore(tagged) != -1L
            if (ok) inserted++
        }
        return inserted
    }
}
