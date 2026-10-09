package com.yang.jizhang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 蓝白配色：海蓝主色 + 云白底色，暗色模式为深海军蓝 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6BE6),
    secondary = Color(0xFF5AC8FA),
    background = Color(0xFFF5F8FF),
    onBackground = Color(0xFF17263F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17263F),
    surfaceVariant = Color(0xFFE6EDF9),
    onSurfaceVariant = Color(0xFF7C8AA3),
    error = Color(0xFFE5484D),
    errorContainer = Color(0xFFFFE5E5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6AA6FF),
    secondary = Color(0xFF5AC8FA),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFE8EEF9),
    surface = Color(0xFF131A28),
    onSurface = Color(0xFFE8EEF9),
    surfaceVariant = Color(0xFF1D2637),
    onSurfaceVariant = Color(0xFF8B97AD),
    error = Color(0xFFFF6369),
    errorContainer = Color(0xFF3A1518),
)

@Composable
fun JizhangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
