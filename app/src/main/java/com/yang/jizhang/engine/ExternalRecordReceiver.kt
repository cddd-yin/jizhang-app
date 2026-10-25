package com.yang.jizhang.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.engine.keepalive.ChannelIds
import com.yang.jizhang.util.BudgetAlertHelper
import com.yang.jizhang.util.CategoryLearner
import com.yang.jizhang.util.CategoryStore
import kotlinx.coroutines.runBlocking

/**
 * v4.0.0：外部记账广播入口（Tasker / AutoJS / AutoAccounting 等喂单）。
 * Action: com.yang.jizhang.action.RECORD
 * Extras: amount（元，必填）· note · category · type(expense/income)
 * 30 秒幂等桶防重复广播。
 */
class ExternalRecordReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.yang.jizhang.action.RECORD"
        private const val TAG = "ExternalRecord"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext

        val amountStr = intent.getStringExtra("amount") ?: intent.extras?.get("amount")?.toString()
        val cents = amountStr?.trim()?.replace(",", "")?.toDoubleOrNull()
            ?.let { Math.round(it * 100) } ?: 0L
        if (cents <= 0) {
            Log.w(TAG, "invalid amount: $amountStr")
            return
        }
        val note = intent.getStringExtra("note")?.takeIf { it.isNotBlank() } ?: "外部记账"
        val isIncome = (intent.getStringExtra("type") ?: "expense").equals("income", true)
        val category = intent.getStringExtra("category")?.takeIf { it in CategoryStore.all(app) }
            ?: CategoryLearner.hint(app, note)
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

        val pending = goAsync()
        Thread {
            try {
                val dao = (app as JizhangApp).db.txDao()
                val id = runBlocking { dao.insertIgnore(tx) }
                if (id != -1L) {
                    runBlocking { BudgetAlertHelper.check(app) }
                    runCatching { ChannelIds.autoRecorded(app, cents, note, Source.EXTERNAL, isIncome, id) }
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
