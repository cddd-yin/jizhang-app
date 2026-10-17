package com.smartledger.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.smartledger.service.KeepAliveService
import com.smartledger.service.ListenerStatus

/**
 * 定时唤醒：不依赖用户打开 App，周期性拉起保活并尝试重绑通知监听。
 * WorkManager 在国产机 Doze / 省电下经常被推迟，AlarmManager 更适合做兜底。
 */
object ListenerWatchdog {

    private const val TAG = "ListenerWatchdog"
    const val ACTION = "com.smartledger.action.NLS_WATCHDOG"
    private const val REQ_CODE = 7101
    /** 约 25 分钟一次（inexact / allow-while-idle） */
    private const val INTERVAL_MS = 25 * 60 * 1000L

    fun schedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(app)
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        try {
            // 先取消再设，避免重复
            am.cancel(pi)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pi
                )
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "next watchdog in ${INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            Log.w(TAG, "schedule failed", e)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(pendingIntent(app))
        } catch (_: Exception) {
        }
    }

    fun tick(context: Context) {
        val app = context.applicationContext
        NotificationStyle.ensureChannels(app)
        if (ListenerStatus.isEnabledInSettings(app)) {
            KeepAliveService.start(app)
            ListenerStatus.ensureListening(app)
        }
        KeepAliveService.refreshNotification(app)
        // 链式安排下一次（比 setInexactRepeating 更抗 Doze）
        schedule(app)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ListenerWakeReceiver::class.java).setAction(ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQ_CODE, intent, flags)
    }
}

/**
 * 开机 / 解锁亮屏 / 定时狗：拉起监听，避免隔夜卡在「正在重新连接」。
 */
class ListenerWakeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ListenerWake"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "onReceive action=$action")
        val app = context.applicationContext
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_PRESENT,
            ListenerWatchdog.ACTION -> {
                ListenerWatchdog.tick(app)
                ListenerRebindScheduler.schedule(app)
            }
        }
    }
}
