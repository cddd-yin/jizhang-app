package com.yang.jizhang.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * v3.0.0：MainActivity 只做壳，全部界面逻辑在 AppRoot 及各 Tab（全新重写）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { JizhangTheme { AppRoot() } }
    }
}
