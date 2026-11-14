package com.yang.jizhang.engine

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

internal object SheetReader {
    data class Table(val rows: List<List<String>>, val excelDateSystem: ExcelDateSystem? = null)
    enum class ExcelDateSystem { WINDOWS, MAC }

    class Unsupported(message: String) : IllegalArgumentException(message)

    private const val MAX_FILE_SIZE = 24 * 1024 * 1024
    private const val MAX_ARCHIVE_SIZE = 48 * 1024 * 1024
    private const val MAX_ENTRIES = 512
    private const val MAX_CELLS = 1_000_000
    private val worksheet = Regex("""xl/worksheets/[^/]+\.xml""")
    private val textExtensions = setOf("csv", "tsv", "txt")
    private val workbookExtensions = setOf("xlsx", "xlsm")

    fun read(input: InputStream, displayName: String?): List<Table> {
        val bytes = readLimited(input, MAX_FILE_SIZE)
        val extension = displayName.orEmpty().substringAfterLast('.', "").lowercase()
        val budget = Budget()
        return when {
            isZip(bytes) -> readZip(bytes, budget, allowBillArchive = true)
            bytes.startsWithBytes(0xD0, 0xCF, 0x11, 0xE0) || extension == "xls" ->
                throw Unsupported("旧版 Excel .xls 请另存为 .xlsx 后再导入")
            bytes.startsWithBytes(0x25, 0x50, 0x44, 0x46) || extension == "pdf" ->
                throw Unsupported("请从账单来源导出表格文件")
            extension in workbookExtensions || extension == "zip" ->
                throw IllegalArgumentException("文件已损坏，或压缩包需要先解压")
            else -> listOf(readText(bytes, budget))
        }
    }

    private fun ByteArray.startsWithBytes(vararg signature: Int): Boolean =
        size >= signature.size && signature.indices.all { this[it] == signature[it].toByte() }

    private fun isZip(bytes: ByteArray) =
        bytes.startsWithBytes(0x50, 0x4B, 0x03, 0x04) || bytes.startsWithBytes(0x50, 0x4B, 0x05, 0x06)

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return output.toByteArray()
            require(count <= limit - output.size()) { "账单文件或解压内容过大" }
            output.write(buffer, 0, count)
        }
    }

    private class Budget(
        var remaining: Int = MAX_ARCHIVE_SIZE,
        var entries: Int = MAX_ENTRIES,
        var cells: Int = MAX_CELLS,
    ) {
        fun consumeCells(count: Int) {
            require(count <= cells) { "账单行列过多，请拆成多个文件导入" }
            cells -= count
        }
    }

    private fun readZip(bytes: ByteArray, budget: Budget, allowBillArchive: Boolean): List<Table> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(budget.entries-- > 0) { "压缩包内文件过多" }
                val data = readLimited(zip, minOf(MAX_FILE_SIZE, budget.remaining))
                budget.remaining -= data.size
                if (!entry.isDirectory) files[entry.name] = data
                zip.closeEntry()
            }
        }
        val workbook = files["xl/workbook.xml"]
        if (workbook != null) {
            val dateSystem = if (readXml(workbook, "workbookPr") {
                    getAttributeValue(null, "date1904") in setOf("1", "true")
                }.any { it }) ExcelDateSystem.MAC else ExcelDateSystem.WINDOWS
            val shared = files["xl/sharedStrings.xml"]?.let {
                readXml(it, "si") {
                    budget.consumeCells(1)
                    readStringItem()
                }
            }.orEmpty()
            val relationships = files["xl/_rels/workbook.xml.rels"]?.let { rels ->
                readXml(rels, "Relationship") {
                    val target = getAttributeValue(null, "Target")
                    val id = getAttributeValue(null, "Id")
                    val type = getAttributeValue(null, "Type").orEmpty()
                    if (id == null || target == null || !type.endsWith("/worksheet") ||
                        getAttributeValue(null, "TargetMode") == "External") null
                    else id to URI.create("/xl/workbook.xml").resolve(target).normalize().path.removePrefix("/")
                }.filterNotNull().toMap()
            }.orEmpty()
            val sheetIds = readXml(workbook, "sheet") {
                (0 until attributeCount).firstOrNull { getAttributeName(it) == "id" }
                    ?.let { getAttributeValue(it) }
            }
            val sheets = if (sheetIds.isEmpty() && relationships.isEmpty()) {
                files.keys.filter(worksheet::matches)
            } else {
                sheetIds.mapNotNull(relationships::get).distinct()
            }
            return sheets.mapNotNull(files::get).map { sheet ->
                Table(readXml(sheet, "row") { readRow(shared, budget) }, dateSystem)
            }
        }
        require(allowBillArchive) { "无法读取 Excel 工作簿" }
        return files.flatMap { (name, data) ->
            val parts = name.split('/', '\\')
            if (parts.any { it.startsWith('.') || it == "__MACOSX" }) return@flatMap emptyList()
            val extension = name.substringAfterLast('.', "").lowercase()
            when {
                extension in textExtensions -> listOf(readText(data, budget))
                extension in workbookExtensions -> readZip(data, budget, allowBillArchive = false)
                else -> emptyList()
            }
        }.also {
            if (it.isEmpty()) throw Unsupported("ZIP 中没有可导入的 CSV、TSV、TXT 或 Excel 账单")
        }
    }

    private fun readText(bytes: ByteArray, budget: Budget): Table {
        val text = when {
            bytes.startsWithBytes(0xFF, 0xFE) -> String(bytes, Charsets.UTF_16)
            bytes.startsWithBytes(0xFE, 0xFF) -> String(bytes, Charsets.UTF_16)
            else -> try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                String(bytes, charset("GB18030"))
            }
        }.removePrefix("\uFEFF")
        require('\u0000' !in text) { "无法识别账单文本编码" }
        // 先找有账单列的表头，再按该分隔符读取整份文件（包括带换行的备注）。
        val delimiter = text.lineSequence().firstNotNullOfOrNull { line ->
            listOf(',', '\t', ';').firstOrNull { delimiter ->
                delimiter in line && runCatching { readDelimited(line, delimiter) }
                    .getOrNull()?.any(BillTableParser::isHeader) == true
            }
        } ?: return Table(emptyList())
        return Table(readDelimited(text, delimiter, budget))
    }

    private fun readDelimited(text: String, delimiter: Char, budget: Budget? = null): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        fun endField() {
            budget?.consumeCells(1)
            row.add(field.toString())
            field.clear()
        }
        fun endRow() {
            endField()
            if (row.any(String::isNotBlank)) rows.add(row.toList())
            row.clear()
        }
        while (index < text.length) {
            val ch = text[index++]
            when {
                ch == '"' && quoted -> {
                    if (text.getOrNull(index) == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                    }
                }
                ch == '"' && field.isEmpty() -> quoted = true
                quoted -> field.append(ch)
                ch == delimiter -> endField()
                ch == '\r' || ch == '\n' -> {
                    if (ch == '\r' && text.getOrNull(index) == '\n') index++
                    endRow()
                }
                else -> field.append(ch)
            }
        }
        require(!quoted) { "账单中有未闭合的引号" }
        if (row.isNotEmpty() || field.isNotEmpty()) endRow()
        return rows
    }

    private fun <T> readXml(bytes: ByteArray, element: String, read: XmlPullParser.() -> T): List<T> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(bytes.inputStream(), null)
        return buildList {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == element) add(parser.read())
            }
        }
    }

    private inline fun XmlPullParser.readChildren(onTag: XmlPullParser.() -> Unit) {
        val parentDepth = depth
        while (true) {
            when (next()) {
                XmlPullParser.START_TAG -> onTag()
                XmlPullParser.END_TAG -> if (depth == parentDepth) return
                XmlPullParser.END_DOCUMENT -> error("Excel 工作表内容不完整")
            }
        }
    }

    private fun XmlPullParser.readStringItem(): String = buildString {
        readChildren {
            when (name) {
                "t" -> append(nextText())
                "rPh" -> readChildren { } // 跳过拼音注释
            }
        }
    }

    private fun XmlPullParser.readRow(shared: List<String>, budget: Budget): List<String> {
        budget.consumeCells(1)
        val row = mutableListOf<String>()
        readChildren {
            if (name != "c") return@readChildren
            val col = getAttributeValue(null, "r")?.takeWhile { it in 'A'..'Z' }
                ?.takeIf { it.length in 1..3 }?.fold(0) { value, ch -> value * 26 + (ch - 'A' + 1) }
                ?.minus(1) ?: row.size
            require(col in 0..16383) { "Excel 列号无效" }
            val type = getAttributeValue(null, "t")
            var value = ""
            readChildren {
                when (name) {
                    "v" -> value = nextText()
                    "is" -> value = readStringItem()
                }
            }
            if (type == "s") value = value.toIntOrNull()?.let(shared::getOrNull).orEmpty()
            val missing = col + 1 - row.size
            if (missing > 0) {
                budget.consumeCells(missing)
                repeat(missing) { row.add("") }
            }
            row[col] = value
        }
        return row
    }
}
