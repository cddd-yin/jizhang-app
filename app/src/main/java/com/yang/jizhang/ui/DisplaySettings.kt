package com.yang.jizhang.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

internal enum class RefreshRate(val label: String, val limit: Int? = null) {
    SYSTEM("跟随系统"),
    HZ_60("60 Hz", 60),
    HZ_90("90 Hz", 90),
    HZ_120("120 Hz", 120),
    MAXIMUM("最高可用"),
}

internal data class DisplaySettings(
    val refreshRate: RefreshRate = RefreshRate.MAXIMUM,
    val powerSaving: Boolean = false,
) {
    // 省电优先，关掉后恢复用户选的档位。
    fun requestedRate(supported: List<Float>): Float? {
        if (!powerSaving && refreshRate == RefreshRate.SYSTEM) return null
        val limit = if (powerSaving) minOf(refreshRate.limit ?: 60, 60) else refreshRate.limit
        val valid = supported.filter { it.isFinite() && it > 0f }
        if (limit == null) return valid.maxOrNull()
        // 厂商可能把标称 60 Hz 上报为 59.94 或 60.01 Hz。
        return valid.filter { it <= limit + 0.5f }.maxOrNull() ?: valid.minOrNull()
    }
}

internal object DisplayPrefs {
    fun read(context: Context): DisplaySettings {
        val prefs = context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("display_refresh_rate", null)
        return DisplaySettings(
            refreshRate = RefreshRate.entries.firstOrNull { it.name == name } ?: RefreshRate.MAXIMUM,
            powerSaving = prefs.getBoolean("display_power_saving", false),
        )
    }

    fun save(context: Context, settings: DisplaySettings) {
        context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE).edit()
            .putString("display_refresh_rate", settings.refreshRate.name)
            .putBoolean("display_power_saving", settings.powerSaving)
            .apply()
    }
}

internal val LocalPowerSaving = staticCompositionLocalOf { false }
