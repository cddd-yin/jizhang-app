package com.yang.jizhang.ui

import android.hardware.display.DisplayManager
import android.view.Display
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.yang.jizhang.engine.keepalive.ChannelIds

/**
 * v5.1.1：壳 + 入口意图落地 + 高刷/状态栏适配。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (activityDisplay()?.displayId == displayId) applyDisplaySettings(DisplayPrefs.read(this@MainActivity))
        }
    }

    override fun onStart() {
        super.onStart()
        displayManager?.registerDisplayListener(displayListener, android.os.Handler(mainLooper))
        applyDisplaySettings(DisplayPrefs.read(this))
    }

    override fun onStop() {
        displayManager?.unregisterDisplayListener(displayListener)
        super.onStop()
    }

    private fun activityDisplay(): Display? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

    /** 只请求当前分辨率下的刷新率，实际刷新率由系统决定。 */
    internal fun applyDisplaySettings(settings: DisplaySettings) {
        val display = activityDisplay() ?: return
        val current = display.mode
        val modes = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val rate = settings.requestedRate(modes.map { it.refreshRate })
        val modeId = when {
            rate == null -> 0
            current.refreshRate == rate -> current.modeId
            else -> modes.firstOrNull { it.refreshRate == rate }?.modeId ?: 0
        }
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != modeId) {
            attrs.preferredDisplayModeId = modeId
            window.attributes = attrs
        }
    }

    /** 供 AppRoot 在主题切换时重设状态栏图标色（强制深/浅色时跟 App 而非系统） */
    fun applyStatusBarStyle(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val systemBarStyle = if (dark) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent)
        // 浅色三键导航在旧系统仍需要可见的按钮和后备底色。
        val navigationStyle = if (dark) systemBarStyle
            else SystemBarStyle.light(transparent, 0xFF1B1B1B.toInt())
        enableEdgeToEdge(statusBarStyle = systemBarStyle, navigationBarStyle = navigationStyle)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent ?: return
        runCatching {
            if (intent.action == ChannelIds.ACTION_REVIEW_BILL) {
                val id = intent.getLongExtra(ChannelIds.EXTRA_PENDING_ID, 0L)
                if (id > 0) UiBus.pendingBillRequest = id
                intent.removeExtra(ChannelIds.EXTRA_PENDING_ID)
                return
            }
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
