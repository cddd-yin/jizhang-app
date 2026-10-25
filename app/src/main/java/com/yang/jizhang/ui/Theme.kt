package com.yang.jizhang.ui

import android.content.Context
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
 * v3.7.0 多风格预设：在「深海」设计系统上提供 5 套配色皮肤 × 3 档深浅模式。
 * 全部组件均取 colorScheme，预设只换主色与容器色，表面保持中性分层。
 */

// ── 主题预设 ──

enum class JzPreset(
    val label: String,
    /** 深色模式主色 */
    val accentDark: Color,
    /** 浅色模式主色 */
    val accentLight: Color,
) {
    DEEP_SEA("深海", Color(0xFF8B8CF8), Color(0xFF5A5BD8)),   // 默认 · 藤紫
    SKY("晴空", Color(0xFF4CC2FF), Color(0xFF0084D1)),        // 天蓝（v2.x 记忆色）
    MINT("薄荷", Color(0xFF3DDCB4), Color(0xFF0FA968)),       // 薄荷绿
    SAKURA("樱花", Color(0xFFF78FB3), Color(0xFFE24C7B)),     // 樱花粉
    AMBER("琥珀", Color(0xFFF5B544), Color(0xFFB25E09)),      // 琥珀橙
}

/** 深浅模式 */
enum class JzMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"),
}

/** 主题偏好持久化（SharedPreferences 轻量存取） */
object ThemePrefs {
    private const val KEY_PRESET = "theme_preset"
    private const val KEY_MODE = "theme_mode"

    fun preset(context: Context): JzPreset =
        runCatching {
            JzPreset.valueOf(
                context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
                    .getString(KEY_PRESET, null) ?: JzPreset.DEEP_SEA.name
            )
        }.getOrDefault(JzPreset.DEEP_SEA)

    fun mode(context: Context): JzMode =
        runCatching {
            JzMode.valueOf(
                context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
                    .getString(KEY_MODE, null) ?: JzMode.SYSTEM.name
            )
        }.getOrDefault(JzMode.SYSTEM)

    fun save(context: Context, preset: JzPreset, mode: JzMode) {
        context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESET, preset.name)
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}

// ── 语义色（各预设通用，不随皮肤换） ──
val IncomeDark = Color(0xFF42D392)
val IncomeLight = Color(0xFF0FA968)
val ExpenseDark = Color(0xFFF97066)
val ExpenseLight = Color(0xFFE5484D)
val WarnDark = Color(0xFFF5B544)
val WarnLight = Color(0xFFC77414)

// ── 图表色板（固定多色，饼图/图例一致性优先） ──
val JzChartColors = listOf(
    Color(0xFF8B8CF8), // 藤紫
    Color(0xFF42D392), // 绿
    Color(0xFFF5A623), // 橙
    Color(0xFFEC6AA8), // 粉
    Color(0xFF38BDF8), // 天青
    Color(0xFFA78BFA), // 亮紫
)

// ── 表面（深色：深海；浅色：云白） ──
private val DarkPage = Color(0xFF0B0C11)
private val DarkCard = Color(0xFF15171F)
private val DarkCardHi = Color(0xFF1D2029)
private val DarkOn = Color(0xFFF4F5F9)
private val DarkSub = Color(0xFF9297A3)
private val DarkStroke = Color(0xFF262A35)

private val LightPage = Color(0xFFF2F3F7)
private val LightCard = Color(0xFFFFFFFF)
private val LightCardHi = Color(0xFFF3F4F9)
private val LightOn = Color(0xFF191B23)
private val LightSub = Color(0xFF7C8090)
private val LightStroke = Color(0xFFE8EAF1)

/** 主色容器：主色降饱和落深色表面（选中态底色等） */
private fun containerOf(accent: Color, dark: Boolean): Color =
    if (dark) accent.copy(alpha = 0.16f).compositeOverDark() else accent.copy(alpha = 0.10f)

private fun Color.compositeOverDark(): Color =
    Color(
        red = red * 0.16f + DarkCardHi.red * 0.84f,
        green = green * 0.16f + DarkCardHi.green * 0.84f,
        blue = blue * 0.16f + DarkCardHi.blue * 0.84f,
    )

private fun colors(preset: JzPreset, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = preset.accentDark,
        onPrimary = if (preset == JzPreset.AMBER || preset == JzPreset.MINT) Color(0xFF10130E) else Color.White,
        primaryContainer = containerOf(preset.accentDark, true),
        onPrimaryContainer = preset.accentDark,
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
} else {
    lightColorScheme(
        primary = preset.accentLight,
        onPrimary = Color.White,
        primaryContainer = containerOf(preset.accentLight, false),
        onPrimaryContainer = preset.accentLight,
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
}

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

/** 实际生效的深浅模式（强制浅/深色时与系统不一致，语义色据此取值） */
val LocalJzDark = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun incomeColor(): Color = if (LocalJzDark.current) IncomeDark else IncomeLight

@Composable
fun expenseColor(): Color = if (LocalJzDark.current) ExpenseDark else ExpenseLight

@Composable
fun warnColor(): Color = if (LocalJzDark.current) WarnDark else WarnLight

/** 主色：一律走 colorScheme（跟随当前预设与深浅模式） */
@Composable
fun accentColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun JizhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: JzPreset = JzPreset.DEEP_SEA,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalJzDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colors(preset, darkTheme),
            typography = JzTypography,
            shapes = JzShapes,
            content = content,
        )
    }
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
