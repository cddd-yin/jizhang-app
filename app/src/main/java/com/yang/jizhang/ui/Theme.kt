package com.yang.jizhang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.R

/**
 * ZCode 风格主题 v2.4.0（设计 Token 提取自 ZCode 桌面应用 app.asar 的 Tailwind CSS）：
 * - 品牌色：天蓝 sky（浅色 sky-400 #00BCFF / 深色 sky-500 #00A6F4）
 * - 表面：中性灰阶分层（页面 background → 面板 panel → 卡片 card，细描边 outline）
 * - 语义：收入绿 green-600 / 支出红 red-600（深色升一档）
 * - 金额字体沿用 wallee 的 Lato+Sans（OFL 许可），保持展示级大字质量
 */

// ── 品牌天蓝（ZCode --color-brand） ──
val SkyBrand = Color(0xFF00BCFF)       // sky-400（浅色主色）
val SkyBrandDark = Color(0xFF00A6F4)   // sky-500（深色主色）
val SkyStrong = Color(0xFF0084D1)      // sky-600（渐变深端 / 深色渐变浅端）
val SkyContainer = Color(0xFF052F4A)   // sky-950（深色容器）
val SkyOnContainer = Color(0xFF74D4FF) // sky-300（深色容器文字）

// 主视觉渐变（结余大卡）：浅色亮蓝 / 深色深蓝
val SkyGradientLight = listOf(Color(0xFF00A6F4), Color(0xFF00BCFF))
val SkyGradientDark = listOf(Color(0xFF0084D1), Color(0xFF00A6F4))

// ── 语义色（ZCode success/destructive/warning） ──
val IncomeGreen = Color(0xFF00A63E)      // green-600（浅色收入）
val IncomeGreenDark = Color(0xFF00C950)  // green-500（深色收入）
val ExpenseRed = Color(0xFFE7000B)       // red-600（浅色支出）
val ExpenseRedDark = Color(0xFFFB2C36)   // red-500（深色支出）
val WarnAmber = Color(0xFFD08700)        // yellow-600（浅色警告）
val WarnAmberDark = Color(0xFFF0B100)    // yellow-500（深色警告）

// ── 中性表面（ZCode neutral 灰阶） ──
private val LightPage = Color(0xFFFAFAFA)     // neutral-50 页面
private val LightPanel = Color(0xFFF5F5F5)    // neutral-100 面板/填充
private val LightCard = Color(0xFFFFFFFF)     // 卡片/弹层
private val LightOn = Color(0xFF404040)       // neutral-700 正文
private val LightOnStrong = Color(0xFF0A0A0A) // neutral-950 主文本/反相按钮
private val LightSubtle = Color(0xFF737373)   // neutral-500 次要文字
private val LightBorder = Color(0xFFE6E6E6)   // 描边（neutral-950@10% 落在白底）
private val LightBorderSoft = Color(0xFFF2F2F2)

private val DarkPage = Color(0xFF171717)      // neutral-900 页面
private val DarkPanel = Color(0xFF262626)     // neutral-800 面板/填充/卡片
private val DarkDeep = Color(0xFF0A0A0A)      // neutral-950 最深（底栏/搜索）
private val DarkCard = Color(0xFF262626)      // neutral-800 卡片
private val DarkOn = Color(0xFFE5E5E5)        // neutral-200 正文
private val DarkOnStrong = Color(0xFFFAFAFA)  // neutral-50 主文本/反相按钮
private val DarkSubtle = Color(0xFFA1A1A1)    // 次要文字
private val DarkBorder = Color(0xFF2E2E2E)    // 描边（neutral-50@10% 落在 #171717）
private val DarkBorderSoft = Color(0xFF262626)

private val LightColors = lightColorScheme(
    primary = SkyBrand,
    onPrimary = Color.White,
    primaryContainer = SkyBrand,
    onPrimaryContainer = Color.White,
    secondary = LightCard,
    onSecondary = LightOn,
    secondaryContainer = LightCard,
    onSecondaryContainer = LightOn,
    tertiary = WarnAmber,
    onTertiary = Color.White,
    background = LightPage,
    onBackground = LightOn,
    surface = LightCard,
    onSurface = LightOn,
    surfaceVariant = LightPanel,
    onSurfaceVariant = LightSubtle,
    outline = LightBorder,
    outlineVariant = LightBorderSoft,
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFECEC),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = SkyBrandDark,
    onPrimary = Color.White,
    primaryContainer = SkyContainer,
    onPrimaryContainer = SkyOnContainer,
    secondary = DarkCard,
    onSecondary = DarkOn,
    secondaryContainer = DarkCard,
    onSecondaryContainer = DarkOn,
    tertiary = WarnAmberDark,
    onTertiary = Color(0xFF3A2A00),
    background = DarkPage,
    onBackground = DarkOn,
    surface = DarkCard,
    onSurface = DarkOn,
    surfaceVariant = DarkPanel,
    onSurfaceVariant = DarkSubtle,
    outline = DarkBorder,
    outlineVariant = DarkBorderSoft,
    error = ExpenseRedDark,
    onError = Color.White,
    errorContainer = Color(0xFF4A1418),
    onErrorContainer = Color(0xFFFFD9D9),
)

// ── 字体（Lato 展示数字 + Sans 正文，与 wallee 同款；Lato 为 OFL 许可） ──
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
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** 收入语义色（随深浅色切换） */
@Composable
fun incomeColor(): Color = if (isSystemInDarkTheme()) IncomeGreenDark else IncomeGreen

/** 支出语义色（随深浅色切换） */
@Composable
fun expenseColor(): Color = if (isSystemInDarkTheme()) ExpenseRedDark else ExpenseRed

/** 主视觉渐变（结余大卡背景，随深浅色切换） */
@Composable
fun skyGradient(): Brush =
    if (isSystemInDarkTheme()) Brush.linearGradient(SkyGradientDark) else Brush.linearGradient(SkyGradientLight)

/** 卡片描边色（随深浅色切换） */
@Composable
fun borderColor(): Color = MaterialTheme.colorScheme.outline

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
