package com.yang.jizhang.engine

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yang.jizhang.engine.keepalive.GuardianService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * v4.0.0 引擎重写：支付通知监听服务（替代旧 PaymentListener，427 行精简为纯采集层）。
 * 职责收窄：只做采集与文本提纯（含聚合通知子项展开），业务全部交给 AutoBookEngine。
 */
class PaymentWatchService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        AutoBookEngine.onListenerConnected(this)
        GuardianService.start(this)
        Log.i(TAG, "connected")
    }

    override fun onListenerDisconnected() {
        AutoBookEngine.onListenerDisconnected(this)
        Log.w(TAG, "disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val extras = n.extras

        // 文本提纯：标题 + 正文 + 大文本（聚合通知的子项在大文本里）
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val merged = buildString {
            append(title)
            if (text.isNotBlank()) append(' ').append(text)
            if (bigText.isNotBlank() && bigText != text) append(' ').append(bigText)
        }
        if (merged.isBlank()) return

        val postTime = sbn.postTime
        scope.launch {
            try {
                AutoBookEngine.ingest(applicationContext, pkg, title, merged.removePrefix(title).trim().ifBlank { text }, postTime)
            } catch (e: Exception) {
                Log.w(TAG, "ingest failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "PaymentWatch"
    }
}
