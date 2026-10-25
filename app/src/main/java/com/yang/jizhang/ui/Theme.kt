package com.yang.jizhang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.R

/**
 * v3.0.0「深海」设计系统（全部推翻 v2.x 旧 token，从零重写）：
 * - 灵感来自 Expenso（docs/references）：深色高级感、藤紫主色、彩色分类圆徽、
 *   半圆预算仪表、计算器键盘记账。
 * - 表面：近黑深海底 + 双层卡面，无毛玻璃、无描边堆砌。
 * - 语义：收入绿 / 支出红 / 警告琥珀，随深浅色切换。
 * - 金额字体 Lato + 正文 Sans（OFL 许可，沿用既有字体资源）。
 */

// ── 主色：藤紫（Periwinkle） ──
val AccentDark = Color(0xFF8B8CF8)
val AccentLight = Color(0xFF5A5BD8)

// ── 语义色 ──
val IncomeDark = Color(0xFF42D392)
val IncomeLight = Color(0xFF0FA968)
val ExpenseDark = Color(0xFFF97066)
val ExpenseLight = Color(0xFFE5484D)
val WarnDark = Color(0xFFF5B544)
val WarnLight = Color(0xFFC77414)

// ── 图表色板（分类/饼图/柱状通用，深浅色通用） ──
val JzChartColors = listOf(
    Color(0xFF8B8CF8), // 藤紫
    Color(0xFF42D392), // 绿
    Color(0xFFF5A623), // 橙
    Color(0xFFEC6AA8), // 粉
    Color(0xFF38BDF8), // 天青
    Color(0xFFA78BFA), // 亮紫
)

// ── 表面（深色：深海） ──
private val DarkPage = Color(0xFF0B0C11)
private val DarkCard = Color(0xFF15171F)
private val DarkCardHi = Color(0xFF1D2029)
private val DarkOn = Color(0xFFF4F5F9)
private val DarkSub = Color(0xFF9297A3)
private val DarkStroke = Color(0xFF262A35)

// ── 表面（浅色） ──
private val LightPage = Color(0xFFF2F3F7)
private val LightCard = Color(0xFFFFFFFF)
private val LightCardHi = Color(0xFFF3F4F9)
private val LightOn = Color(0xFF191B23)
private val LightSub = Color(0xFF7C8090)
private val LightStroke = Color(0xFFE8EAF1)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = DarkCardHi,
    onPrimaryContainer = DarkOn,
    secondary = DarkCard,
    onSecondary = DarkOn,
    secondaryContainer = DarkCardHi,
    onSecondaryContainer = DarkOn,
    tertiary = WarnDark,
    onTertiary = Color(0xFF22180A),
    background = DarkPage,
    onBackground = DarkOn,
    surface = DarkCard,
    onSurface = DarkOn,
    surfaceVariant = DarkCardHi,
    onSurfaceVariant = DarkSub,
    outline = DarkStroke,
    outlineVariant = DarkStroke,
    error = ExpenseDark,
    onError = Color.White,
    errorContainer = Color(0xFF3B1A1C),
    onErrorContainer = Color(0xFFFFD9D9),
)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = LightCardHi,
    onPrimaryContainer = LightOn,
    secondary = LightCard,
    onSecondary = LightOn,
    secondaryContainer = LightCardHi,
    onSecondaryContainer = LightOn,
    tertiary = WarnLight,
    onTertiary = Color.White,
    background = LightPage,
    onBackground = LightOn,
    surface = LightCard,
    onSurface = LightOn,
    surfaceVariant = LightCardHi,
    onSurfaceVariant = LightSub,
    outline = LightStroke,
    outlineVariant = LightStroke,
    error = ExpenseLight,
    onError = Color.White,
    errorContainer = Color(0xFFFDE9E9),
    onErrorContainer = Color(0xFF7F1D1D),
)

// ── 字体 ──
private val Sans = FontFamily(
    Font(R.font.sans_light, FontWeight.Light),
    Font(R.font.sans_regular, FontWeight.Normal),
    Font(R.font.sans_medium, FontWeight.Medium),
    Font(R.font.sans_bold, FontWeight.SemiBold),
)

private val Lato = FontFamily(Font(R.font.lato_regular, FontWeight.Normal))

val JzTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Normal, fontSize = 40.sp),
    headlineMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Normal, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 10.sp),
)

val JzShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun incomeColor(): Color = if (isSystemInDarkTheme()) IncomeDark else IncomeLight

@Composable
fun expenseColor(): Color = if (isSystemInDarkTheme()) ExpenseDark else ExpenseLight

@Composable
fun warnColor(): Color = if (isSystemInDarkTheme()) WarnDark else WarnLight

@Composable
fun accentColor(): Color = if (isSystemInDarkTheme()) AccentDark else AccentLight

@Composable
fun JizhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JzTypography,
        shapes = JzShapes,
        content = content,
    )
}

// ── 金额格式化 ──

/** 金额（分）→ 12,345.67（带千分位，保留两位小数） */
fun fmtMoney(cents: Long): String {
    val abs = Math.abs(cents)
    val fen = (abs % 100).toString().padStart(2, '0')
    return "%,d.$fen".format(abs / 100)
}

/** 金额（分）→ ¥12,345.67 */
fun fmtCents(cents: Long): String = "¥" + fmtMoney(cents)

/** 金额（分）→ 带符号展示：+12,345.67 / -12,345.67 */
fun fmtSigned(cents: Long, isIncome: Boolean): String =
    (if (isIncome) "+" else "-") + fmtMoney(cents)
