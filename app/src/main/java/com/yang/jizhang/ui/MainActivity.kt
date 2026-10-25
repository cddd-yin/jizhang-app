package com.yang.jizhang.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * v3.1.0：MainActivity 是壳 + 三个入口的落地页——
 * ① 正常启动；② 分享/划词记账（ACTION_SEND / ACTION_PROCESS_TEXT）；
 * ③ 快捷磁贴「记一笔」（extra open_editor）。
 * 解析结果经 UiBus 交给 AppRoot 打开预填的记账页。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 仅冷启动处理入口意图；旋转/重建走 savedInstanceState 分支避免重复触发
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        runCatching {
            // 快捷磁贴：直接打开记一笔
            if (intent.getBooleanExtra("open_editor", false)) {
                UiBus.openEditorRequest = true
            }
            // 分享文本 / 划词记账
            val text = when (intent.action) {
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                else -> null
            }
            if (!text.isNullOrBlank()) {
                parseShareText(text)?.let { UiBus.sharePending = it }
            }
        }
    }
}
