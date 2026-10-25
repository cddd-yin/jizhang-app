package com.yang.jizhang.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.yang.jizhang.JizhangApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v4.0.0 引擎重写：CSV 导出 + 公共下载目录写文件（替代旧 CsvExporter + BackupStorage 写路径）。
 */
object BillExporter {
    private const val DIR = "Download/Jizhang"

    /** 导出 [start, end) 区间账单；无数据返回 null */
    suspend fun export(context: Context, start: Long, end: Long): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val txs = (context.applicationContext as JizhangApp).db.txDao().getByTimeRangeOnce(start, end)
            if (txs.isEmpty()) return@runCatching null
            val day = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
            val csv = buildCsv(txs)
            writeText(context, "Jizhang_${day.format(Date(start))}_${day.format(Date(end))}.csv", csv, "text/csv")
        }.getOrNull()
    }

    suspend fun exportAll(context: Context): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val txs = (context.applicationContext as JizhangApp).db.txDao().getAllOnce()
            if (txs.isEmpty()) return@runCatching null
            val day = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
            writeText(context, "Jizhang_全部_${day.format(Date())}.csv", buildCsv(txs), "text/csv")
        }.getOrNull()
    }

    private fun buildCsv(txs: List<com.yang.jizhang.data.TxEntity>): String = buildString {
        append('\uFEFF') // BOM：Excel 直开不乱码
        append("交易时间,收/支,金额,分类,备注,来源\n")
        val row = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        for (t in txs) {
            val amount = "%.2f".format(t.amountCents / 100.0)
            append(
                "${row.format(Date(t.time))},${if (t.isIncome) "收入" else "支出"},$amount," +
                    "${esc(t.category)},${esc(t.note)},${esc(t.source)}\n"
            )
        }
    }

    private fun esc(v: String) =
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) "\"${v.replace("\"", "\"\"")}\"" else v

    /** 写入公共「下载/Jizhang」，返回可分享 Uri（同名覆盖） */
    fun writeText(context: Context, fileName: String, content: String, mime: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(fileName),
                )
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, DIR)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use {
                    it.write(content.toByteArray(Charsets.UTF_8)); it.flush()
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (_: Exception) {
                // 落文件路径兜底
            }
        }
        return try {
            val dir = File(context.getExternalFilesDir(null), "Jizhang").apply { mkdirs() }
            val file = File(dir, fileName)
            FileWriter(file).use { it.write(content) }
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun share(context: Context, uri: Uri, mime: String = "text/csv") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出记账数据"))
    }
}
