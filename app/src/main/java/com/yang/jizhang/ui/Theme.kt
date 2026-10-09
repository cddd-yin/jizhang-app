package com.yang.jizhang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** iOS 系统配色：绿为主色，浅灰底 + 纯黑暗色模式 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF248A3D),
    secondary = Color(0xFFFF9F0A),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8E8E93),
    error = Color(0xFFFF3B30),
    errorContainer = Color(0xFFFFE5E0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF30D158),
    secondary = Color(0xFFFF9F0A),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    error = Color(0xFFFF453A),
    errorContainer = Color(0xFF3A1512),
)

@Composable
fun JizhangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
