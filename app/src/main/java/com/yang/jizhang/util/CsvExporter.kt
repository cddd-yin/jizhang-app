package com.yang.jizhang.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.TxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 导出：写公共「下载/Jizhang」并返回可分享 Uri。
 * 抄自 SmartLedger CsvExporter，适配 TxEntity（金额为分、收支为布尔）。
 *
 * 表头与 [com.yang.jizhang.importer.CsvImporter] 兼容，导出的文件可再被本 App 导入。
 */
object CsvExporter {

    private val stampFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    private val rowFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    /** 导出 [start, end) 区间账单，返回可分享 Uri；无数据返回 null */
    suspend fun export(context: Context, start: Long, end: Long): Uri? = withContext(Dispatchers.IO) {
        try {
            val dao = (context.applicationContext as JizhangApp).db.txDao()
            val txs = dao.getByTimeRangeOnce(start, end)
            if (txs.isEmpty()) return@withContext null
            val fileName = "Jizhang_${stampFormat.format(Date(start))}_${stampFormat.format(Date(end))}.csv"
            writeCsv(context, txs, fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 导出全部账单，返回可分享 Uri；无数据返回 null */
    suspend fun exportAll(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val dao = (context.applicationContext as JizhangApp).db.txDao()
            val txs = dao.getAllOnce()
            if (txs.isEmpty()) return@withContext null
            val fileName = "Jizhang_全部_${stampFormat.format(Date())}.csv"
            writeCsv(context, txs, fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun writeCsv(context: Context, txs: List<TxEntity>, fileName: String): Uri? {
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM：Excel 直接打开不乱码
        sb.append("交易时间,收/支,金额,分类,备注,来源\n")
        for (t in txs) {
            val type = if (t.isIncome) "收入" else "支出"
            val amount = "${t.amountCents / 100}.${(Math.abs(t.amountCents) % 100).toString().padStart(2, '0')}"
            sb.append(
                "${rowFormat.format(Date(t.time))},$type,$amount," +
                    "${escapeCsv(t.category)},${escapeCsv(t.note)},${escapeCsv(t.source)}\n"
            )
        }
        return BackupStorage.writeCsv(context, fileName, sb.toString())
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    fun shareFile(context: Context, uri: Uri, mimeType: String = "text/csv") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出记账数据"))
    }
}
