package com.yang.jizhang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * iOS 系统配色：
 * - 主色 systemBlue (#007AFF / #0A84FF)
 * - 背景 groupedBackground (#F2F2F7 / #000000)
 * - 卡片 secondaryGroupedBackground (#FFFFFF / #1C1C1E)
 * - 辅助文字 secondaryLabel (#8E8E93)
 * - 收入绿 systemGreen (#34C759 / #30D158)
 */
private val IosBlue = Color(0xFF007AFF)
private val IosBlueDark = Color(0xFF0A84FF)
private val IosGreen = Color(0xFF34C759)
private val IosGreenDark = Color(0xFF30D158)
private val IosOrange = Color(0xFFFF9500)
private val IosOrangeDark = Color(0xFFFF9F0A)
private val IosRed = Color(0xFFFF3B30)
private val IosRedDark = Color(0xFFFF453A)

private val LightColors = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    secondary = IosGreen,
    onSecondary = Color.White,
    tertiary = IosOrange,
    onTertiary = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFC6C6C8),
    error = IosRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF8A1C16),
    tertiaryContainer = Color(0xFFFFF3E0),
    onTertiaryContainer = Color(0xFF8A5300),
)

private val DarkColors = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    secondary = IosGreenDark,
    onSecondary = Color.Black,
    tertiary = IosOrangeDark,
    onTertiary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF3A3A3C),
    error = IosRedDark,
    onError = Color.White,
    errorContainer = Color(0xFF3A1518),
    onErrorContainer = Color(0xFFFFB4AB),
    tertiaryContainer = Color(0xFF3A2A10),
    onTertiaryContainer = Color(0xFFFFD8A8),
)

@Composable
fun JizhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
