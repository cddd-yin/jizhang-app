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
 * 高级感主题：双层背景（页面 L2 / 卡片 L1）+ 靛蓝主色 + Lato/Sans 字体。
 * 配色与排版借鉴 wallee（wisnukurniawan/Compose-Expense，Apache-2.0）；
 * 金额语义色沿用其 Expense #FD3C4A / Income #00A86B。
 */

// ── 主色板 ──
val IndigoPrimary = Color(0xFF5B5FEF)      // 靛蓝紫：保留蓝系身份、更显高级
val IndigoPrimaryDark = Color(0xFFA6A9FF)
val IncomeGreen = Color(0xFF00A86B)
val IncomeGreenDark = Color(0xFF30D158)
val ExpenseRed = Color(0xFFFD3C4A)
val ExpenseRedDark = Color(0xFFFF453A)

// 双层背景（wallee 分层思想：页面 L2 / 卡片 L1 / 填充 L2）
private val LightPage = Color(0xFFFFFFFF)
private val LightCard = Color(0xFFF7F7F7)
private val LightFill = Color(0xFFF1F1F1)
private val LightOn = Color(0xFF000000)
private val LightSecondaryText = Color(0xFF6E6E73)
private val LightError = Color(0xFFEA6D7E)

private val DarkPage = Color(0xFF20232F)
private val DarkCard = Color(0xFF373945)
private val DarkFill = Color(0xFF373945)
private val DarkOn = Color(0xFFFFFFFF)
private val DarkSecondaryText = Color(0xFFB8B9C0)
private val DarkError = Color(0xFFFF8A99)

private val LightColors = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoPrimary,
    onPrimaryContainer = Color.White,
    secondary = LightCard,
    onSecondary = LightOn,
    secondaryContainer = LightCard,
    onSecondaryContainer = LightOn,
    tertiary = Color(0xFFFF9F0A),
    onTertiary = Color.White,
    background = LightPage,
    onBackground = LightOn,
    surface = LightPage,
    onSurface = LightOn,
    surfaceVariant = LightFill,
    onSurfaceVariant = LightSecondaryText,
    outline = Color(0xFFE4E4E7),
    outlineVariant = Color(0xFFEDEDF0),
    error = LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E8),
    onErrorContainer = Color(0xFF7A1E2A),
)

private val DarkColors = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = Color(0xFF23236B),
    primaryContainer = IndigoPrimaryDark,
    onPrimaryContainer = Color(0xFF23236B),
    secondary = DarkCard,
    onSecondary = DarkOn,
    secondaryContainer = DarkCard,
    onSecondaryContainer = DarkOn,
    tertiary = Color(0xFFFFB258),
    onTertiary = Color(0xFF4A2C00),
    background = DarkPage,
    onBackground = DarkOn,
    surface = DarkPage,
    onSurface = DarkOn,
    surfaceVariant = DarkFill,
    onSurfaceVariant = DarkSecondaryText,
    outline = Color(0xFF454854),
    outlineVariant = Color(0xFF3A3D48),
    error = DarkError,
    onError = Color(0xFF4A0D14),
    errorContainer = Color(0xFF5C2530),
    onErrorContainer = Color(0xFFFFD9DE),
)

// ── 字体（与 wallee 同款：Lato 展示数字 + Sans 正文；Lato 为 OFL 许可） ──
private val Sans = FontFamily(
    Font(R.font.sans_light, FontWeight.Light),
    Font(R.font.sans_regular, FontWeight.Normal),
    Font(R.font.sans_medium, FontWeight.Medium),
    Font(R.font.sans_bold, FontWeight.SemiBold),
)

private val Lato = FontFamily(Font(R.font.lato_regular, FontWeight.Normal))

val JzTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Normal, fontSize = 34.sp),
    headlineMedium = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = Lato, fontWeight = FontWeight.Normal, fontSize = 20.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 10.sp),
)

val JzShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** 收入语义色（随深浅色切换） */
@Composable
fun incomeColor(): Color = if (isSystemInDarkTheme()) IncomeGreenDark else IncomeGreen

/** 支出语义色（随深浅色切换） */
@Composable
fun expenseColor(): Color = if (isSystemInDarkTheme()) ExpenseRedDark else ExpenseRed

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
