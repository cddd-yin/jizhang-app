package com.yang.jizhang.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle

/**
 * v5.1.1：壳 + 入口意图落地 + 高刷/状态栏适配。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyMaxRefreshRate()
        handleIntent(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * 高刷支持（60–120Hz 自适应）：把窗口首选显示模式设为屏幕支持的最高刷新率。
     * 高刷屏上部分厂商对未声明的 App 锁 60Hz，导致动画掉半帧；60Hz 屏取到的最高值仍是 60，
     * 不产生额外功耗。
     */
    private fun applyMaxRefreshRate() {
        try {
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return
            val best = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = best.modeId
            }
        } catch (_: Exception) {
        }
    }

    /** 供 AppRoot 在主题切换时重设状态栏图标色（强制深/浅色时跟 App 而非系统） */
    fun applyStatusBarStyle(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
        )
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent ?: return
        runCatching {
            // 快捷磁贴 / 桌面小组件按钮：直接打开记一笔（组件经 Glance parameters 传字符串值）
            if (intent.getBooleanExtra("open_editor", false) ||
                intent.getStringExtra("open_editor") == "true"
            ) {
                UiBus.openEditorRequest = true
            }
            // 分享文本 / 划词记账
            val text = when (intent.action) {
                android.content.Intent.ACTION_SEND -> intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                android.content.Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT)?.toString()
                else -> null
            }
            if (!text.isNullOrBlank()) {
                parseShareText(this, text)?.let { UiBus.sharePending = it }
            }
        }
    }
}
