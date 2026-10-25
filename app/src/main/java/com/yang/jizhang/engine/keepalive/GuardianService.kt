package com.yang.jizhang.engine.keepalive

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * v4.0.0 引擎重写：前台守卫服务（替代旧 KeepAliveService，198 行散装巡检重建为三态状态机）。
 *
 * 状态机：CONNECTED（静默，慢巡检）→ RECOVERING（软重绑，3 连败升级 forceReconnect）
 *       → DOWN（告警通知 + 慢速重试）。回连即回落。
 */
class GuardianService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var failStreak = 0
    private var alerted = false

    private enum class Phase { CONNECTED, RECOVERING, DOWN }

    private val patrol = object : Runnable {
        override fun run() {
            val next = patrolOnce()
            handler.postDelayed(this, next)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ChannelIds.ensure(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteForeground(ListenerHealth.displayState(this))
        if (ListenerHealth.isEnabledInSettings(this) && !ListenerHealth.isBinderConnected()) {
            ListenerHealth.requestRebind(this)
        }
        WatchdogScheduler.schedule(this)
        handler.removeCallbacks(patrol)
        handler.postDelayed(patrol, 2_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(patrol)
        WatchdogScheduler.schedule(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** @return 下次巡检间隔 */
    private fun patrolOnce(): Long {
        val phase = when {
            !ListenerHealth.isEnabledInSettings(this) -> Phase.DOWN
            ListenerHealth.isBinderConnected() -> Phase.CONNECTED
            else -> Phase.RECOVERING
        }
        when (phase) {
            Phase.CONNECTED -> {
                failStreak = 0
                if (alerted) {
                    alerted = false
                    ChannelIds.cancelWatchDown(this)
                }
                promoteForeground(ListenerHealth.DisplayState.CONNECTED)
                // 顺带兜底任务（节流在各自内部）
                com.yang.jizhang.util.RecurringHelper.processDueAsync(this)
                com.yang.jizhang.util.BudgetAlertHelper.checkAsync(this)
                return SLOW_MS
            }
            Phase.RECOVERING -> {
                failStreak++
                promoteForeground(ListenerHealth.DisplayState.RECOVERING)
                ListenerHealth.requestRebind(this, force = true)
                if (failStreak == 3 || (failStreak > 3 && failStreak % 10 == 0)) {
                    ListenerHealth.forceReconnect(applicationContext)
                }
                return FAST_MS
            }
            Phase.DOWN -> {
                failStreak = 0
                alerted = false
                promoteForeground(ListenerHealth.DisplayState.NEED_PERMISSION)
                return SLOW_MS
            }
        }
    }

    private fun promoteForeground(state: ListenerHealth.DisplayState) {
        val text = when (state) {
            ListenerHealth.DisplayState.CONNECTED -> "正在自动记录支付通知"
            ListenerHealth.DisplayState.RECOVERING -> "正在恢复支付监听…"
            ListenerHealth.DisplayState.NEED_PERMISSION -> "自动记账已暂停：需重新开启通知使用权"
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    ChannelIds.ID_GUARDIAN_FG,
                    ChannelIds.guardian(this, text),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(ChannelIds.ID_GUARDIAN_FG, ChannelIds.guardian(this, text))
            }
        } catch (e: Exception) {
            Log.w("Guardian", "startForeground failed", e)
            stopSelf()
        }
    }

    companion object {
        private const val FAST_MS = 12_000L
        private const val SLOW_MS = 5 * 60_000L

        fun start(context: Context) {
            context.applicationContext.startForegroundService(
                Intent(context, GuardianService::class.java)
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context, GuardianService::class.java))
        }
    }
}
