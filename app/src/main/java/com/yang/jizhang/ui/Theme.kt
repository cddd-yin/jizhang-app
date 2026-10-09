package com.yang.jizhang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1B6E53)
private val GreenDark = Color(0xFF63DBA9)
private val Amber = Color(0xFFFFC94D)

@Composable
fun JizhangTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = GreenDark,
            secondary = Amber,
        ) else lightColorScheme(
            primary = Green,
            secondary = Amber,
        ),
        content = content,
    )
}
