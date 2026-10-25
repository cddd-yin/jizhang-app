package com.yang.jizhang.engine.keepalive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * v4.0.0 引擎重写：定时唤醒（替代旧 ListenerWatchdog + ListenerWakeReceiver）。
 * AlarmManager inexact 周期兜底：不开 App 也能拉起守卫与补账。
 */
object WatchdogScheduler {
    private const val TAG = "Watchdog"
    const val ACTION_TICK = "com.yang.jizhang.action.NLS_WATCHDOG"
    private const val intervalMs = 25 * 60_000L

    private fun pi(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7101,
        Intent(context, BootRouser::class.java).apply { action = ACTION_TICK },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        val am = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(pi(context))
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + intervalMs,
                pi(context),
            )
        } catch (e: Exception) {
            Log.w(TAG, "schedule failed", e)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pi(context))
    }

    fun onTick(context: Context) {
        val app = context.applicationContext
        ChannelIds.ensure(app)
        if (ListenerHealth.isEnabledInSettings(app)) {
            if (!ListenerHealth.isBinderConnected()) ListenerHealth.requestRebind(app)
            GuardianService.start(app)
        }
        // 兜底工作（保活心跳之外的第二道网）
        com.yang.jizhang.util.RecurringHelper.processDueAsync(app)
        com.yang.jizhang.util.BudgetAlertHelper.checkAsync(app)
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    com.yang.jizhang.util.DailyDigest.maybeNotify(app)
                }
            } catch (_: Exception) {
            }
        }.start()
        schedule(app)
    }
}

/** 开机 / 解锁 / 定时唤醒统一接收器（替代旧 ListenerWakeReceiver） */
class BootRouser : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_PRESENT,
            WatchdogScheduler.ACTION_TICK,
            -> WatchdogScheduler.onTick(context)
        }
    }
}

/** 覆盖安装后标记需重新授权（替代旧 PackageReplacedReceiver） */
class UpgradeMarker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ListenerHealth.checkAppUpdated(context)
        }
    }
}
