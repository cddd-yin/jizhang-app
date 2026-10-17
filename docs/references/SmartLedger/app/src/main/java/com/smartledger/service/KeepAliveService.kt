package com.smartledger.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.smartledger.util.ListenerWatchdog
import com.smartledger.util.NotificationStyle

/**
 * 前台保活 + 监听重连巡检。
 * 未连上时积极 requestRebind，卡住时升级 forceReconnect；
 * 已连接时不再周期性软重绑（避免把正常连接弄断）。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAlive"
        private const val RECOVER_FAST_MS = 12_000L
        private const val RECOVER_SLOW_MS = 120_000L
        /** 卡住恢复时的强恢复冷却 */
        private const val STUCK_FORCE_COOLDOWN_MS = 12 * 60 * 1000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (_: Exception) {
            }
        }

        fun refreshNotification(context: Context) {
            try {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                    ?: return
                nm.notify(
                    NotificationStyle.ID_KEEP_ALIVE,
                    NotificationStyle.buildKeepAlive(context, ListenerStatus.displayState(context))
                )
            } catch (e: Exception) {
                Log.w(TAG, "refreshNotification failed", e)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var failStreak = 0
    private var alertedDown = false

    private val recoverRunnable = object : Runnable {
        override fun run() {
            var next = RECOVER_SLOW_MS
            try {
                next = tickRecover()
            } finally {
                handler.postDelayed(this, next)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationStyle.ensureChannels(this)
        Log.d(TAG, "KeepAlive service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteForeground()
        if (ListenerStatus.isEnabledInSettings(this) && !ListenerStatus.isBinderConnected()) {
            ListenerStatus.requestRebind(this, force = true)
        }
        // 确保定时狗在跑（进程被杀后重新拉起）
        ListenerWatchdog.schedule(this)
        handler.removeCallbacks(recoverRunnable)
        handler.postDelayed(recoverRunnable, 2_000L)
        Log.d(TAG, "KeepAlive foreground running")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(recoverRunnable)
        // 被杀前尽量预约下一次唤醒
        try {
            ListenerWatchdog.schedule(this)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteForeground() {
        val notification = NotificationStyle.buildKeepAlive(
            this,
            ListenerStatus.displayState(this)
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationStyle.ID_KEEP_ALIVE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationStyle.ID_KEEP_ALIVE, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            try {
                startForeground(NotificationStyle.ID_KEEP_ALIVE, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground fallback failed", e2)
                stopSelf()
            }
        }
    }

    /** @return 下次巡检间隔 */
    private fun tickRecover(): Long {
        refreshNotification(this)

        if (!ListenerStatus.isEnabledInSettings(this)) {
            failStreak = 0
            alertedDown = false
            Log.d(TAG, "NLS not enabled in settings")
            return RECOVER_SLOW_MS
        }

        if (ListenerStatus.isBinderConnected()) {
            failStreak = 0
            if (alertedDown) {
                alertedDown = false
                NotificationStyle.cancelListenerDown(this)
            }
            // 已连接：不要周期性 requestRebind，部分机型会把正常连接拆掉后无法自愈
            return RECOVER_SLOW_MS
        }

        failStreak++
        Log.w(
            TAG,
            "NLS binder not ready (wasConnected=${ListenerStatus.wasConnectedBefore(this)}), " +
                "attempt #$failStreak"
        )
        ListenerStatus.requestRebind(this, force = true)

        // 约 36s 后仍连不上 → 强恢复；之后约每 12 分钟可再试一次（勿在主线程 sleep）
        if (failStreak == 3 || (failStreak > 3 && failStreak % 10 == 0)) {
            val app = applicationContext
            val streak = failStreak
            Thread {
                val ok = ListenerStatus.forceReconnect(
                    app,
                    bypassCooldown = false,
                    cooldownMs = STUCK_FORCE_COOLDOWN_MS
                )
                Log.w(TAG, "escalate forceReconnect result=$ok streak=$streak")
                handler.post { refreshNotification(app) }
            }.start()
        }

        // 卡住约 2 分钟仍失败：发一条可点进 App 的提醒（只发一次，连上后取消）
        if (failStreak >= 10 && !alertedDown) {
            alertedDown = true
            NotificationStyle.notifyListenerDown(this)
        }

        return RECOVER_FAST_MS
    }
}
