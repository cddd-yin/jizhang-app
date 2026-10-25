package com.yang.jizhang.engine

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * v5.3.0 表格读取器：统一把 CSV / Excel(.xlsx) 读成行列表，供 BillImporter 列映射导入。
 * xlsx 自写轻量解析（zip + XmlPullParser，零第三方依赖）：支持共享字符串/内联字符串/数字，
 * 老格式 .xls 不支持（调用方提示）。
 */
object SheetReader {

    sealed class Result {
        data class Ok(val rows: List<List<String>>) : Result()
        data class Unsupported(val name: String) : Result()
        object Failed : Result()
    }

    fun read(context: Context, uri: Uri, displayName: String?): Result {
        val name = (displayName ?: uri.lastPathSegment ?: "").lowercase()
        return try {
            when {
                name.endsWith(".xlsx") || name.endsWith(".xlsm") -> readXlsx(context, uri)
                name.endsWith(".xls") -> Result.Unsupported("xls")
                else -> readCsv(context, uri) // csv / txt / 无扩展名按文本尝试
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Failed
        }
    }

    // ═══ CSV ═══

    private val charsets = listOf(Charset.forName("UTF-8"), Charset.forName("GBK"))

    private fun readCsv(context: Context, uri: Uri): Result {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return Result.Failed
        val text = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        } else {
            charsets.firstOrNull { cs -> String(bytes, cs).let { it.contains("交易时间") || it.contains("日期") || it.contains("收/支") } }
                ?.let { String(bytes, it) } ?: String(bytes, Charsets.UTF_8)
        }
        val rows = text.lineSequence()
            .filter { it.isNotBlank() }
            .map { splitCsvLine(it) }
            .toList()
        return if (rows.isEmpty()) Result.Failed else Result.Ok(rows)
    }

    /** 引号内逗号兼容的行切分（制表符也当分隔符） */
    internal fun splitCsvLine(line: String): List<String> {
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

    // ═══ XLSX ═══

    private fun readXlsx(context: Context, uri: Uri): Result {
        // 1) 解包取目标条目
        var shared: List<String> = emptyList()
        var sheetXml: ByteArray? = null
        ZipInputStream(context.contentResolver.openInputStream(uri)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> shared = parseShared(zip)
                    entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") && sheetXml == null ->
                        sheetXml = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val sheet = sheetXml ?: return Result.Failed

        // 2) 解析 sheet 行单元格
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(sheet.inputStream().bufferedReader(Charsets.UTF_8))
        var curRow = mutableListOf<String>()
        var curCell = StringBuilder()
        var cellType = ""
        var inValue = false
        var inInlineText = false
        var colIndex = -1
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> curRow = mutableListOf()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        colIndex = colOf(parser.getAttributeValue(null, "r"))
                        curCell = StringBuilder()
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInlineText = true
                }
                XmlPullParser.TEXT -> if (inValue || inInlineText) curCell.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        // t="s" 共享字符串：v 为索引
                        val value = if (cellType == "s") {
                            curCell.toString().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        } else {
                            curCell.toString()
                        }
                        putCell(curRow, colIndex, value)
                    }
                    "row" -> rows.add(curRow.toList())
                }
            }
            event = parser.next()
        }
        return if (rows.isEmpty()) Result.Failed else Result.Ok(rows)
    }

    /** r 属性 "B7" → 列索引 1；缺列补空对齐 */
    private fun colOf(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') idx = idx * 26 + (ch - 'A' + 1) else break
        }
        return idx - 1
    }

    private fun putCell(row: MutableList<String>, col: Int, value: String) {
        val c = if (col >= 0) col else row.size
        while (row.size < c) row.add("")
        if (c == row.size) row.add(value) else row[c] = value
    }

    /** sharedStrings 的 <si> 拼接（含富文本 <r><t>） */
    private fun parseShared(zip: ZipInputStream): List<String> {
        val out = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(BufferedReader(InputStreamReader(zip, Charsets.UTF_8)))
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; sb.clear() }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inSi && inT) sb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> { out.add(sb.toString()); inSi = false }
                }
            }
            event = parser.next()
        }
        return out
    }

    /** Excel 日期序列号（1900 epoch）→ 毫秒；仅接受合理区间，含时区偏移不处理（本地表格基本同区） */
    fun excelSerialToMillis(serial: Double): Long {
        // Excel epoch: 1899-12-30 + serial 天；序列以天为单位（含小数时分秒）
        return Math.round((serial - 25569.0) * 86_400_000.0)
    }
}
