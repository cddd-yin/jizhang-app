package com.yang.jizhang.engine

import android.content.Context
import android.net.Uri
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.util.CategoryLearner
import java.text.SimpleDateFormat
import java.util.Locale
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.CategoryRules

/**
 * 账单导入（v5.3.0 起支持多种形式）：支付宝/微信 CSV、任意来源通用 CSV、Excel(.xlsx)。
 * 统一路径：SheetReader 读行 → 表头自动映射（宽别名）→ 近邻去重入库。
 */
object BillImporter {

    /** 表头列映射（宽别名：支付宝/微信官方表 + 银行/自建表格常见列名） */
    private data class Columns(val time: Int, val flow: Int, val amount: Int, val party: Int, val goods: Int, val status: Int) {
        companion object {
            fun List<String>.find(vararg keys: String): Int =
                indexOfFirst { h -> keys.any { h.contains(it) } }
            fun of(header: List<String>): Columns {
                val h = header
                return Columns(
                    time = h.find("交易时间", "交易日期", "日期", "时间"),
                    flow = h.find("收/支", "收支", "方向", "类型", "借贷"),
                    amount = h.find("金额", "发生额", "实付"),
                    party = h.find("交易对方", "对方", "商户", "对方户名"),
                    goods = h.find("商品说明", "商品", "备注", "摘要", "描述", "用途"),
                    status = h.find("交易状态", "当前状态", "状态"),
                )
            }
        }
    }

    suspend fun import(context: Context, uri: Uri, appLabel: String): Int {
        val rows = when (val r = SheetReader.read(context, uri, uri.lastPathSegment)) {
            is SheetReader.Result.Ok -> r.rows
            else -> return 0
        }
        val headerIdx = rows.indexOfFirst { row ->
            row.any { it.contains("交易时间") || it.contains("日期") } &&
                row.any { it.contains("金额") || it.contains("发生额") || it.contains("实付") }
        }
        if (headerIdx < 0) return 0

        val cols = Columns.of(rows[headerIdx].map { it.trim() })
        if (cols.time < 0 || cols.amount < 0) return 0

        val dao = (context.applicationContext as JizhangApp).db.txDao()
        var inserted = 0
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

        for (i in headerIdx + 1 until rows.size) {
            val cell = rows[i]
            if (cell.size < 2) continue
            fun cellAt(idx: Int): String = if (idx in cell.indices) cell[idx].trim() else ""

            val status = cellAt(cols.status)
            if (listOf("退款", "还款", "失败", "关闭", "不计收支").any { it in status }) continue

            val time = parseTimeFlexible(cellAt(cols.time)) ?: continue
            val rawAmount = cellAt(cols.amount)
            val negative = rawAmount.trim().startsWith("-")
            val amountCents = parseCents(rawAmount) ?: continue
            if (amountCents <= 0) continue

            val flow = cellAt(cols.flow)
            val income = when {
                flow.contains("收") && !flow.contains("支") -> true
                flow.contains("支") || flow.contains("借") -> false
                flow.contains("贷") || flow.contains("入") -> true
                negative -> true // 通用表格：负数金额视为收入
                else -> false
            }
            val party = cellAt(cols.party)
            val goods = cellAt(cols.goods)

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

    private val timePatterns = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
        "yyyy.MM.dd HH:mm:ss", "yyyy.MM.dd HH:mm", "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd",
    )

    /** 文本日期 + Excel 日期序列号（40000~60000 ≈ 2009-2064）双支持 */
    private fun parseTimeFlexible(v: String): Long? {
        val s = v.trim()
        if (s.isEmpty()) return null
        s.toDoubleOrNull()?.let { serial ->
            if (serial in 40000.0..60000.0) return SheetReader.excelSerialToMillis(serial)
        }
        for (p in timePatterns) {
            try {
                return SimpleDateFormat(p, Locale.CHINA).parse(s)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseCents(s: String): Long? {
        val v = s.replace("¥", "").replace("￥", "").replace(",", "").replace("+", "").trim()
        val d = v.toDoubleOrNull() ?: return null
        return Math.round(Math.abs(d) * 100)
    }
}
