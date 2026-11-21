package com.yang.jizhang.engine.payment

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object ScreenPaymentMonitor {
    private const val ENABLED = "screen_payment_enabled"
    private val connection = MutableStateFlow(false)
    val connected = connection.asStateFlow()

    fun setConnected(connected: Boolean) {
        connection.value = connected
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun hasPermission(context: Context): Boolean {
        val target = ComponentName(context, ScreenPaymentService::class.java)
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            val service = info.resolveInfo.serviceInfo
            service != null && ComponentName(service.packageName, service.name) == target
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
}
