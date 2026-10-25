package com.yang.jizhang.engine.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.yang.jizhang.engine.PaymentWatchService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v4.0.0 引擎重写：通知监听健康状态（替代旧 ListenerStatus）。
 * 持久化沿用原 jizhang_prefs 的 nls_* 键（升级不丢历史状态），
 * 实现重建为三态显示模型 + 组件级强恢复（带冷却）。
 */
object ListenerHealth {
    private const val TAG = "ListenerHealth"
    private const val PREFS = "jizhang"
    private const val K_CONNECTED = "nls_connected"
    private const val K_LAST_VERSION = "last_version_code"
    private const val K_SHOW_AFTER_UPDATE = "show_nls_after_update"
    private const val K_EVER_ENABLED = "nls_ever_enabled"
    private const val K_LAST_FORCE = "nls_last_force_reconnect"
    private const val K_LAST_ALIVE = "nls_last_alive_at"

    private val binderConnected = AtomicBoolean(false)

    enum class DisplayState { CONNECTED, RECOVERING, NEED_PERMISSION }

    fun componentOf(context: Context): ComponentName =
        ComponentName(context, PaymentWatchService::class.java)

    fun isEnabledInSettings(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val mine = componentOf(context).flattenToString()
        return flat.split(':').any { it.equals(mine, ignoreCase = true) }
    }

    fun isBinderConnected(): Boolean = binderConnected.get()

    fun setConnected(connected: Boolean) {
        binderConnected.set(connected)
    }

    fun markAlive(context: Context) {
        prefs(context).edit().putLong(K_LAST_ALIVE, System.currentTimeMillis()).apply()
    }

    fun lastAliveAt(context: Context): Long = prefs(context).getLong(K_LAST_ALIVE, 0L)

    fun markEverEnabled(context: Context) {
        prefs(context).edit().putBoolean(K_EVER_ENABLED, true).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 系统设置里已开但 binder 未连 → 显示"恢复中" */
    fun displayState(context: Context): DisplayState = when {
        !isEnabledInSettings(context) -> DisplayState.NEED_PERMISSION
        isBinderConnected() -> DisplayState.CONNECTED
        else -> DisplayState.RECOVERING
    }

    /** 软重绑（连接异常时的第一手段） */
    fun requestRebind(context: Context, force: Boolean = false): Boolean {
        if (!isEnabledInSettings(context)) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerHelper.rebind(context)
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "rebind failed", e)
            false
        }
    }

    /** 确保组件未被禁用后拉起监听（App 打开时调用） */
    fun ensureListening(context: Context) {
        if (!isEnabledInSettings(context)) return
        ensureComponentEnabled(context)
        requestRebind(context)
    }

    /** 强恢复：禁用→启用组件踢系统重绑（带 30s 冷却防抖） */
    fun forceReconnect(context: Context, bypassCooldown: Boolean = false, cooldownMs: Long = 30_000): Boolean {
        val now = System.currentTimeMillis()
        val last = prefs(context).getLong(K_LAST_FORCE, 0L)
        if (!bypassCooldown && now - last < cooldownMs) return false
        prefs(context).edit().putLong(K_LAST_FORCE, now).apply()
        return try {
            val cn = componentOf(context)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            NotificationListenerHelper.rebind(context)
            true
        } catch (e: Exception) {
            Log.w(TAG, "forceReconnect failed", e)
            false
        }
    }

    /** 版本升级后系统会撤销权限：标记 App 内提示重新开启 */
    fun checkAppUpdated(context: Context): Boolean {
        val pm = context.packageManager
        val code = try {
            pm.getPackageInfo(context.packageName, 0).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode
                else @Suppress("DEPRECATION") it.versionCode.toLong()
            }
        } catch (_: Exception) {
            return false
        }
        val prefs = prefs(context)
        val saved = prefs.getLong(K_LAST_VERSION, -1L)
        if (saved == -1L || saved == code) {
            prefs.edit().putLong(K_LAST_VERSION, code).apply()
            return false
        }
        prefs.edit()
            .putLong(K_LAST_VERSION, code)
            .putBoolean(K_SHOW_AFTER_UPDATE, true)
            .putBoolean(K_CONNECTED, false)
            .apply()
        return true
    }

    private fun ensureComponentEnabled(context: Context) {
        try {
            context.packageManager.setComponentEnabledSetting(
                componentOf(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        } catch (_: Exception) {
        }
    }
}

/** NotificationListenerService.rebind 的隔离层（API 24+） */
private object NotificationListenerHelper {
    fun rebind(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.service.notification.NotificationListenerService.requestRebind(
                ListenerHealth.componentOf(context)
            )
            true
        } else false
    }
}
