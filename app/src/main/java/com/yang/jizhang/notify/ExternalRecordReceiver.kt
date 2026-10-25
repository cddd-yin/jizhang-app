package com.yang.jizhang.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.notify.keepalive.NotificationChannels
import kotlinx.coroutines.runBlocking

/**
 * 外部记账广播入口（v3.10.0，借鉴 AutoAccounting 生态惯例）：
 * Tasker / AutoJS / AutoAccounting / 快捷指令类工具发广播即可喂单，
 * 无需打开 App，零新权限（exported receiver 仅收本动作）。
 *
 * Action: com.yang.jizhang.action.RECORD
 * Extras:
 *   - amount  (必填) 金额，字符串或数字，单位元，如 "12.5"
 *   - note    (可选) 备注，默认 "外部记账"
 *   - category( 可选) 分类名（须为内置 11 类之一），缺省按备注猜
 *   - type    (可选) "expense"(默认) / "income"
 *
 * 幂等：dedupeKey = EXTERNAL|30秒桶|金额|备注，重复广播不产生双份。
 */
class ExternalRecordReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.yang.jizhang.action.RECORD"
        private const val TAG = "ExternalRecord"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext

        val amountStr = intent.getStringExtra("amount")
            ?: (intent.extras?.get("amount")?.toString())
        val cents = amountStr?.trim()?.replace(",", "")?.toDoubleOrNull()
            ?.let { Math.round(it * 100) } ?: 0L
        if (cents <= 0) {
            Log.w(TAG, "invalid amount: $amountStr")
            return
        }
        val note = intent.getStringExtra("note")?.takeIf { it.isNotBlank() } ?: "外部记账"
        val type = intent.getStringExtra("type") ?: "expense"
        val isIncome = type.equals("income", true)
        var category = intent.getStringExtra("category")
            ?.takeIf { it in com.yang.jizhang.util.CategoryStore.all(app) }
            ?: com.yang.jizhang.util.CategoryLearner.hint(app, note)
            ?: CategoryRules.guess(note).takeIf { !isIncome || it != "餐饮美食" }
            ?: "收入进账"

        val now = System.currentTimeMillis()
        val tx = TxEntity(
            amountCents = cents,
            isIncome = isIncome,
            category = category,
            note = note,
            source = Source.EXTERNAL,
            time = now,
            dedupeKey = "EXTERNAL|${now / 30_000}|$cents|$isIncome|$note",
        )

        // 广播在主线程，goAsync 交协程落库
        val pending = goAsync()
        Thread {
            try {
                val dao = (app as JizhangApp).db.txDao()
                val id = runBlocking { dao.insertIgnore(tx) }
                if (id != -1L) {
                    runBlocking {
                        com.yang.jizhang.util.BudgetAlertHelper.check(app)
                    }
                    try {
                        NotificationChannels.notifyPaymentDetected(
                            app, cents, note, Source.EXTERNAL, isIncome, id,
                        )
                    } catch (_: Exception) {
                    }
                    com.yang.jizhang.widget.WidgetSync.refreshAsync(app)
                }
                Log.d(TAG, "recorded id=$id $cents ${if (isIncome) "income" else "expense"} $note")
            } catch (e: Exception) {
                Log.w(TAG, "record failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
