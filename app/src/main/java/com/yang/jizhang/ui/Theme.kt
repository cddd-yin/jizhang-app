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
    /** 深色模式主色（MATERIAL_YOU 为占位实际取系统动态色；CUSTOM 由种子色派生） */
    val accentDark: Color,
    /** 浅色模式主色 */
    val accentLight: Color,
) {
    MATERIAL_YOU("动态取色", Color(0xFF6750A4), Color(0xFF6750A4)), // Material You（Android 12+ 壁纸取色，官方设计语言）
    CUSTOM("自定义", Color(0xFF7C7C8A), Color(0xFF5B5B66)),        // v5.2 用户自选种子色派生
    SKY("天码", Color(0xFF00A6F4), Color(0xFF0084D1)),        // v5.0 默认 · ZCode 天蓝（sky-500/sky-600）
    DEEP_SEA("深海", Color(0xFF8B8CF8), Color(0xFF5A5BD8)),   // 藤紫
    MINT("薄荷", Color(0xFF3DDCB4), Color(0xFF0FA968)),       // 薄荷绿
    SAKURA("樱花", Color(0xFFF78FB3), Color(0xFFE24C7B)),     // 樱花粉
    AMBER("琥珀", Color(0xFFF5B544), Color(0xFFB25E09)),      // 琥珀橙
}

/** 深浅模式 */
enum class JzMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"),
}

/** 风格方案（v5.2.0）：圆角与玻璃层的组合档位 */
enum class JzStyle(val label: String, val desc: String) {
    GLASS("玻璃", "大圆角 + 毛玻璃底栏"),
    COMPACT("紧凑", "小圆角 + 实底底栏，更省电"),
}

/** 从种子色派生深/浅两档主色（HSV：深色提亮保可读，浅色压深保对比） */
fun deriveAccentsFromSeed(seedArgb: Int): Pair<Color, Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seedArgb, hsv)
    val dark = android.graphics.Color.HSVToColor(
        floatArrayOf(hsv[0], (hsv[1] * 0.85f).coerceAtMost(0.9f), 0.76f),
    )
    val light = android.graphics.Color.HSVToColor(
        floatArrayOf(hsv[0], (hsv[1] * 0.90f).coerceAtMost(0.95f), 0.52f),
    )
    return Color(dark.toInt()) to Color(light.toInt())
}

/** 主题偏好持久化（SharedPreferences 轻量存取） */
object ThemePrefs {
    private const val KEY_PRESET = "theme_preset"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_STYLE = "theme_style"
    private const val KEY_SEED = "theme_custom_seed"

    fun preset(context: Context): JzPreset =
        runCatching {
            JzPreset.valueOf(
                context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
                    .getString(KEY_PRESET, null) ?: JzPreset.SKY.name
            )
        }.getOrDefault(JzPreset.SKY)

    fun mode(context: Context): JzMode =
        runCatching {
            JzMode.valueOf(
                context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
                    .getString(KEY_MODE, null) ?: JzMode.SYSTEM.name
            )
        }.getOrDefault(JzMode.SYSTEM)

    fun style(context: Context): JzStyle =
        runCatching {
            JzStyle.valueOf(
                context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
                    .getString(KEY_STYLE, null) ?: JzStyle.GLASS.name
            )
        }.getOrDefault(JzStyle.GLASS)

    /** 自定义种子色（ARGB），未设置返回 null */
    fun customSeed(context: Context): Int? =
        context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
            .takeIf { it.contains(KEY_SEED) }?.getInt(KEY_SEED, 0)

    fun save(context: Context, preset: JzPreset, mode: JzMode) {
        context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESET, preset.name)
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun saveStyle(context: Context, style: JzStyle) {
        context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
            .edit().putString(KEY_STYLE, style.name).apply()
    }

    fun saveCustomSeed(context: Context, argb: Int) {
        context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
            .edit().putInt(KEY_SEED, argb).apply()
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

// ── 表面（v5.0.0：ZCode 中性灰阶 token，提取自 app.asar Tailwind CSS） ──
private val DarkPage = Color(0xFF171717)      // neutral-900
private val DarkCard = Color(0xFF262626)      // neutral-800
private val DarkCardHi = Color(0xFF303030)    // neutral-700 面板
private val DarkOn = Color(0xFFE5E5E5)        // neutral-200
private val DarkSub = Color(0xFFA1A1A1)       // neutral-400
private val DarkStroke = Color(0xFF2E2E2E)    // neutral-50@10%

private val LightPage = Color(0xFFFAFAFA)     // neutral-50
private val LightCard = Color(0xFFFFFFFF)
private val LightCardHi = Color(0xFFF5F5F5)   // neutral-100
private val LightOn = Color(0xFF404040)       // neutral-700
private val LightSub = Color(0xFF737373)      // neutral-500
private val LightStroke = Color(0xFFE6E6E6)   // neutral-950@10%

/** 主色容器：主色降饱和落深色表面（选中态底色等） */
private fun containerOf(accent: Color, dark: Boolean): Color =
    if (dark) accent.copy(alpha = 0.16f).compositeOverDark() else accent.copy(alpha = 0.10f)

private fun Color.compositeOverDark(): Color =
    Color(
        red = red * 0.16f + DarkCardHi.red * 0.84f,
        green = green * 0.16f + DarkCardHi.green * 0.84f,
        blue = blue * 0.16f + DarkCardHi.blue * 0.84f,
    )

private fun luma(c: Color): Float = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

private fun colors(
    preset: JzPreset,
    dark: Boolean,
    accentDarkOverride: Color? = null,
    accentLightOverride: Color? = null,
): androidx.compose.material3.ColorScheme {
    val accentD = accentDarkOverride ?: preset.accentDark
    val accentL = accentLightOverride ?: preset.accentLight
    // 亮色主色（黄/亮绿系）配深前景字，其余配白字
    val onAccentFor = if (luma(if (dark) accentD else accentL) > 0.62f) Color(0xFF10130E) else Color.White
    return if (dark) {
        darkColorScheme(
            primary = accentD,
            onPrimary = onAccentFor,
            primaryContainer = containerOf(accentD, true),
            onPrimaryContainer = accentD,
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
        primary = accentL,
        onPrimary = onAccentFor,
        primaryContainer = containerOf(accentL, false),
        onPrimaryContainer = accentL,
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

val JzShapesGlass = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),      // Liquid Glass 大圆角
    extraLarge = RoundedCornerShape(28.dp),
)

val JzShapesCompact = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

/** 兼容旧名 */
val JzShapes = JzShapesGlass

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
    style: JzStyle = JzStyle.GLASS,
    customSeed: Int? = null,
    content: @Composable () -> Unit,
) {
    // Material You（v4.1.0）：Android 12+ 整套配色直接取系统动态色（官方 API）
    val scheme = if (preset == JzPreset.MATERIAL_YOU && android.os.Build.VERSION.SDK_INT >= 31) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(ctx)
        else androidx.compose.material3.dynamicLightColorScheme(ctx)
    } else if (preset == JzPreset.CUSTOM && customSeed != null) {
        // v5.2：自定义种子色派生（HSV：深色提亮 / 浅色压深）
        val (dark, light) = deriveAccentsFromSeed(customSeed)
        colors(JzPreset.CUSTOM, darkTheme, dark, light)
    } else {
        colors(preset, darkTheme)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalJzDark provides darkTheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = JzTypography,
            shapes = if (style == JzStyle.GLASS) JzShapesGlass else JzShapesCompact,
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
