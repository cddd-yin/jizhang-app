package com.yang.jizhang.engine

import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

internal object BillTableParser {
    data class Row(
        val time: Long,
        val cents: Long,
        val income: Boolean,
        val party: String,
        val description: String,
        val category: String,
        val transactionId: String,
        val legacyExcelTime: Long?,
    )

    data class Parsed(val bills: List<Row>, val skipped: Int)

    private class Columns(header: List<String>) {
        private val names = header.map { name ->
            name.trim().lowercase(Locale.ROOT).substringBefore('（').substringBefore('(')
                .replace(Regex("""[\s_\-]"""), "")
        }

        private fun find(vararg aliases: String): Int {
            for (alias in aliases) {
                val index = names.indexOf(alias)
                if (index >= 0) return index
            }
            return -1
        }

        val time = find("交易时间", "交易日期", "入账日期", "记账日期", "日期", "时间", "付款时间", "记账时间",
            "transactiondate", "transactiontime", "datetime", "date", "time")
            .takeIf { it >= 0 } ?: names.indexOfFirst { "日期" in it || "交易时间" in it }
        val debit = find("支出金额", "借方发生额", "借方金额", "支出", "借方", "debit", "debitamount")
        val credit = find("收入金额", "贷方发生额", "贷方金额", "收入", "贷方", "credit", "creditamount")
        val amount = find("交易金额", "金额", "发生额", "实付金额", "实付", "amount", "transactionamount")
            .takeIf { it >= 0 } ?: names.indices.firstOrNull {
                val name = names[it]
                it != debit && it != credit && "余额" !in name &&
                    ("金额" in name || "发生额" in name || "实付" in name)
            } ?: -1
        val flow = find("收/支", "收支", "收支类型", "收支方向", "资金流向", "方向", "借贷标志",
            "借贷方向", "借贷", "direction", "flow", "type", "类型")
        val party = find("交易对方", "对方户名", "对方", "商户名称", "商户", "merchant", "payee")
        val goods = find("商品说明", "商品名称", "商品", "备注", "摘要", "描述", "用途", "description", "note", "memo")
        val status = find("交易状态", "当前状态", "状态", "status")
        val category = find("分类", "category")
        val transactionId = find("交易单号", "交易订单号", "交易号", "流水号", "transactionid")
        val isHeader = time >= 0 && (amount >= 0 || debit >= 0 || credit >= 0)
    }

    fun isHeader(row: List<String>) = Columns(row).isHeader

    fun parse(table: SheetReader.Table, zone: ZoneId): Parsed? {
        val header = table.rows.indexOfFirst(::isHeader)
        if (header < 0) return null
        var columns = Columns(table.rows[header])
        val bills = mutableListOf<Row>()
        var skipped = 0
        for (row in table.rows.drop(header + 1)) {
            val repeatedHeader = Columns(row)
            if (repeatedHeader.isHeader) {
                columns = repeatedHeader
                continue
            }
            if (row.none(String::isNotBlank)) continue
            val bill = parseRow(row, columns, table.excelDateSystem, zone)
            if (bill == null) skipped++ else bills.add(bill)
        }
        return Parsed(bills, skipped)
    }

    private val ignoredStatuses = listOf("退款", "还款", "失败", "关闭", "取消", "不计收支", "待支付",
        "refunded", "failed", "cancelled", "canceled", "pending")

    private fun parseRow(
        row: List<String>, cols: Columns, dateSystem: SheetReader.ExcelDateSystem?, zone: ZoneId,
    ): Row? {
        fun at(index: Int) = row.getOrNull(index).orEmpty().trim()
        val status = at(cols.status).lowercase(Locale.ROOT)
        val flow = at(cols.flow).lowercase(Locale.ROOT)
        if (ignoredStatuses.any { it in status } || flow in setOf("/", "不计收支", "不计入收支", "neutral")) return null

        val rawTime = at(cols.time)
        val time = parseTime(rawTime, dateSystem, zone) ?: return null
        val legacyExcelTime = rawTime.toDoubleOrNull()?.takeIf { it in 40000.0..60000.0 }
            ?.let { Math.round((it - 25569.0) * 86_400_000) }
        val debit = parseAmount(at(cols.debit))
        val credit = parseAmount(at(cols.credit))
        val signedAmount = parseAmount(at(cols.amount))
        if (debit != null && credit != null) return null
        val cents = debit ?: credit ?: signedAmount ?: return null
        val income = when {
            credit != null -> true
            debit != null -> false
            flow in setOf("income", "credit", "in", "cr", "c") -> true
            flow in setOf("expense", "debit", "out", "dr", "d") -> false
            flow.contains("支") || flow.contains("借") -> false
            flow.contains("收") || flow.contains("贷") || flow.contains("入") -> true
            else -> cents < 0 // 沿用既有通用表格的约定。
        }
        return Row(time, kotlin.math.abs(cents), income, at(cols.party), at(cols.goods),
            at(cols.category), at(cols.transactionId), legacyExcelTime)
    }

    private val amountPattern = Regex("""[+-]?(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d+)?(?:[Ee][+-]?\d{1,3})?""")

    private fun parseAmount(text: String): Long? {
        val value = text.replace("￥", "").replace("¥", "").replace("−", "-")
            .removeSuffix("元").trim()
        if (!amountPattern.matches(value)) return null
        val decimal = value.replace(",", "").toBigDecimalOrNull() ?: return null
        return try {
            decimal.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
                .takeUnless { it == 0L || it == Long.MIN_VALUE }
        } catch (_: ArithmeticException) {
            null
        }
    }

    private val dateTimes = listOf("uuuu-M-d H:m:s", "uuuu-M-d H:m", "uuuu年M月d日 H:m:s", "uuuu年M月d日 H:m")
        .map(::dateFormatter)
    private val dates = listOf("uuuu-M-d", "uuuu年M月d日", "uuuuMMdd").map(::dateFormatter)

    private fun dateFormatter(pattern: String) =
        DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    private fun parseTime(raw: String, dateSystem: SheetReader.ExcelDateSystem?, zone: ZoneId): Long? {
        val normalized = raw.replace('/', '-')
        runCatching { OffsetDateTime.parse(normalized) }.getOrNull()?.let { return it.toInstant().toEpochMilli() }
        runCatching { LocalDateTime.parse(normalized) }.getOrNull()?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        val text = normalized.replace('T', ' ').replace('.', '-')
        for (formatter in dateTimes) {
            runCatching { LocalDateTime.parse(text, formatter) }.getOrNull()?.let {
                return it.atZone(zone).toInstant().toEpochMilli()
            }
        }
        for (formatter in dates) {
            runCatching { LocalDate.parse(text, formatter) }.getOrNull()?.let {
                return it.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
        val serial = raw.toDoubleOrNull() ?: return null
        if (dateSystem == null && serial !in 40000.0..60000.0) return null
        if (serial !in 1.0..<2_958_466.0) return null
        val days = serial.toLong()
        val epoch = when (dateSystem) {
            SheetReader.ExcelDateSystem.MAC -> LocalDate.of(1904, 1, 1)
            SheetReader.ExcelDateSystem.WINDOWS, null -> {
                if (days == 60L) return null // Excel 1900-02-29 并不存在。
                LocalDate.of(1899, 12, if (days < 60) 31 else 30)
            }
        }
        val millisInDay = Math.round((serial - days) * 86_400_000)
        return epoch.plusDays(days).atStartOfDay().plusNanos(millisInDay * 1_000_000)
            .atZone(zone).toInstant().toEpochMilli()
    }
}
