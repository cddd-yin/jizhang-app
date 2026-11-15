package com.yang.jizhang.engine.keepalive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** NotificationListenerService 由系统绑定。断开后只请求重绑，短时重试无需前台服务。 */
internal object ListenerRecovery {
    private val handler = Handler(Looper.getMainLooper())
    private val retryToken = Any()
    private val retryDelays = longArrayOf(1_000L, 5_000L, 15_000L)
    private const val ALARM_DELAY = 60_000L
    private const val REQUEST_CODE = 7102

    fun onDisconnected(context: Context) {
        val app = context.applicationContext
        cancel(app)
        if (!ListenerHealth.isEnabledInSettings(app)) return

        val now = SystemClock.uptimeMillis()
        for (delay in retryDelays) {
            handler.postAtTime({ ListenerHealth.ensureListening(app) }, retryToken, now + delay)
        }
        // 若进程在短时重试前被回收，系统还能在允许的时间唤醒一次。
        try {
            val alarm = app.getSystemService(AlarmManager::class.java) ?: return
            val intent = retryIntent(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            alarm.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ALARM_DELAY,
                intent,
            )
        } catch (e: RuntimeException) {
            Log.w("ListenerRecovery", "retry alarm deferred", e)
        }
    }

    fun cancel(context: Context) {
        handler.removeCallbacksAndMessages(retryToken)
        retryIntent(context, PendingIntent.FLAG_NO_CREATE)?.let { pending ->
            context.getSystemService(AlarmManager::class.java)?.cancel(pending)
            pending.cancel()
        }
    }

    private fun retryIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, ListenerRetryReceiver::class.java),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
}

class ListenerRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ListenerHealth.ensureListening(context)
    }
}
