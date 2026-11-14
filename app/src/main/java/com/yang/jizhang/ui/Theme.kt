package com.yang.jizhang.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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
    STYLE("随界面风格", Color.Unspecified, Color.Unspecified),
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

internal enum class TabIndicator { PILL, TINT, UNDERLINE, VOYAGER }

enum class JzStyle(
    val label: String,
    val desc: String,
    val shapes: Shapes,
    internal val titleSize: Int = 26,
    internal val titleWeight: FontWeight = FontWeight.SemiBold,
    internal val navRadius: Int = 22,
    internal val navHeight: Int = 64,
    internal val navInset: Int = 0,
    internal val blurRadius: Int = 0,
    internal val indicator: TabIndicator = TabIndicator.PILL,
) {
    VOYAGER("Voyager", "蓝紫星空、暖金细节和悬浮导航", shapeSet(6, 12, 18, 24, 32),
        titleSize = 34, titleWeight = FontWeight.Bold, navRadius = 26, navHeight = 62, navInset = 14,
        blurRadius = 18, indicator = TabIndicator.VOYAGER),
    GLASS("玻璃", "大圆角卡片和磨砂底栏", shapeSet(8, 12, 16, 22, 28), blurRadius = 22),
    COMPACT("紧凑", "小圆角卡片和实底底栏", shapeSet(6, 8, 12, 14, 18)),
    IOS("苹果 iOS", "大标题、分组卡片和悬浮磨砂底栏", shapeSet(9, 12, 16, 20, 28),
        titleSize = 34, titleWeight = FontWeight.Bold, navRadius = 28, navHeight = 58, navInset = 16,
        blurRadius = 26, indicator = TabIndicator.TINT),
    ORIGIN_OS("vivo OriginOS", "圆润卡片、大号结余和胶囊导航", shapeSet(10, 16, 22, 30, 36),
        titleSize = 32, titleWeight = FontWeight.Bold, navRadius = 32, navHeight = 62, navInset = 12),
    PAPER("纸质账本", "暖色纸面、细边框和下划线导航", shapeSet(3, 5, 7, 9, 12),
        titleSize = 30, navRadius = 0, navHeight = 58, indicator = TabIndicator.UNDERLINE),
    INK("墨黑", "深色模式使用纯黑底，搭配简洁导航", shapeSet(4, 7, 10, 14, 20),
        titleSize = 30, titleWeight = FontWeight.Bold, navRadius = 0, navHeight = 56,
        indicator = TabIndicator.TINT);
}

private fun shapeSet(xs: Int, s: Int, m: Int, l: Int, xl: Int) = Shapes(
    extraSmall = RoundedCornerShape(xs.dp),
    small = RoundedCornerShape(s.dp),
    medium = RoundedCornerShape(m.dp),
    large = RoundedCornerShape(l.dp),
    extraLarge = RoundedCornerShape(xl.dp),
)

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
                    .getString(KEY_PRESET, null) ?: JzPreset.STYLE.name
            )
        }.getOrDefault(JzPreset.STYLE)

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
                    .getString(KEY_STYLE, null) ?: JzStyle.VOYAGER.name
            )
        }.getOrDefault(JzStyle.VOYAGER)

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

private val VoyagerChartColors = listOf(
    Color(0xFF8274AA), Color(0xFFBD955C), Color(0xFF68849E),
    Color(0xFF9D6F84), Color(0xFF698777), Color(0xFFAF8067),
)

@Composable
internal fun chartColors(): List<Color> =
    if (LocalJzStyle.current == JzStyle.VOYAGER) VoyagerChartColors else JzChartColors

private class Surfaces(page: Long, card: Long, panel: Long, text: Long, muted: Long, border: Long) {
    val page = Color(page)
    val card = Color(card)
    val panel = Color(panel)
    val text = Color(text)
    val muted = Color(muted)
    val border = Color(border)
}

private val voyagerLight = Surfaces(0xFFE2DFF1, 0xFFF4F0FC, 0xFFDED7F0, 0xFF211B37, 0xFF504465, 0xFF86769F)
private val voyagerDark = Surfaces(0xFF101026, 0xFF211C3B, 0xFF2D2650, 0xFFF7EEFC, 0xFFCFC1DF, 0xFF635584)
private val neutralLight = Surfaces(0xFFFAFAFA, 0xFFFFFFFF, 0xFFF5F5F5, 0xFF404040, 0xFF666666, 0xFFE6E6E6)
private val neutralDark = Surfaces(0xFF171717, 0xFF262626, 0xFF303030, 0xFFE5E5E5, 0xFFA1A1A1, 0xFF2E2E2E)
private val iosLight = Surfaces(0xFFF2F2F7, 0xFFFFFFFF, 0xFFE8E8ED, 0xFF17171B, 0xFF606068, 0xFFDADAE0)
private val iosDark = Surfaces(0xFF0B0B0D, 0xFF1C1C1E, 0xFF2C2C2E, 0xFFF5F5F7, 0xFFAEAEB2, 0xFF38383A)
private val originLight = Surfaces(0xFFF0F4FD, 0xFFFFFFFF, 0xFFE4EDFC, 0xFF172339, 0xFF4B5B74, 0xFFD4DFF1)
private val originDark = Surfaces(0xFF101621, 0xFF1B2639, 0xFF293750, 0xFFEBF2FF, 0xFFABBBD4, 0xFF334764)
private val paperLight = Surfaces(0xFFF5EDE0, 0xFFFFF9EF, 0xFFEDE1D0, 0xFF392B23, 0xFF655247, 0xFFD8C4AF)
private val paperDark = Surfaces(0xFF1B1613, 0xFF271F19, 0xFF382C23, 0xFFF3E6D6, 0xFFC4AD98, 0xFF5A4739)
private val inkLight = Surfaces(0xFFF1F1F1, 0xFFFFFFFF, 0xFFE5E5E5, 0xFF111111, 0xFF515151, 0xFFCCCCCC)
private val inkDark = Surfaces(0xFF000000, 0xFF0D0D0D, 0xFF1C1C1C, 0xFFF5F5F5, 0xFFB4B4B4, 0xFF363636)

private fun JzStyle.surfaces(dark: Boolean): Surfaces = when (this) {
    JzStyle.VOYAGER -> if (dark) voyagerDark else voyagerLight
    JzStyle.IOS -> if (dark) iosDark else iosLight
    JzStyle.ORIGIN_OS -> if (dark) originDark else originLight
    JzStyle.PAPER -> if (dark) paperDark else paperLight
    JzStyle.INK -> if (dark) inkDark else inkLight
    JzStyle.GLASS, JzStyle.COMPACT -> if (dark) neutralDark else neutralLight
}

internal fun JzStyle.defaultAccent(dark: Boolean): Color = when (this) {
    JzStyle.VOYAGER -> if (dark) Color(0xFFDEC393) else Color(0xFF725025)
    JzStyle.IOS -> if (dark) Color(0xFF459EFF) else Color(0xFF0063CE)
    JzStyle.ORIGIN_OS -> if (dark) Color(0xFF91B1FF) else Color(0xFF3159CD)
    JzStyle.PAPER -> if (dark) Color(0xFFF0B28A) else Color(0xFF8C3D26)
    JzStyle.INK -> if (dark) Color(0xFFF0F0F0) else Color(0xFF272727)
    JzStyle.GLASS, JzStyle.COMPACT -> if (dark) JzPreset.SKY.accentDark else JzPreset.SKY.accentLight
}

internal fun readableOn(color: Color): Color =
    if (color.luminance() > 0.179f) Color.Black else Color.White

internal fun styleColors(style: JzStyle, dark: Boolean, accent: Color): ColorScheme {
    val palette = style.surfaces(dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = readableOn(accent),
        primaryContainer = accent.copy(alpha = if (dark) 0.18f else 0.12f).compositeOver(palette.card),
        onPrimaryContainer = palette.text,
        secondary = palette.card,
        onSecondary = palette.text,
        secondaryContainer = palette.panel,
        onSecondaryContainer = palette.text,
        tertiary = if (dark) WarnDark else WarnLight,
        onTertiary = if (dark) Color(0xFF22180A) else Color.White,
        background = palette.page,
        onBackground = palette.text,
        surface = palette.card,
        onSurface = palette.text,
        surfaceVariant = palette.panel,
        onSurfaceVariant = palette.muted,
        surfaceContainerLowest = palette.page,
        surfaceContainerLow = palette.card,
        surfaceContainer = palette.card,
        surfaceContainerHigh = palette.panel,
        surfaceContainerHighest = palette.panel,
        surfaceTint = accent,
        outline = palette.border,
        outlineVariant = palette.border,
        error = if (dark) ExpenseDark else if (style == JzStyle.VOYAGER) VoyagerExpenseLight else ExpenseLight,
        onError = Color.White,
        errorContainer = if (dark) Color(0xFF3B1A1C) else Color(0xFFFDE9E9),
        onErrorContainer = if (dark) Color(0xFFFFD9D9) else Color(0xFF7F1D1D),
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

internal val JzStyle.usesFlatBalance: Boolean
    get() = this == JzStyle.VOYAGER || this == JzStyle.PAPER || this == JzStyle.INK

internal val LocalJzStyle = androidx.compose.runtime.staticCompositionLocalOf { JzStyle.GLASS }

@Composable
internal fun PageTitle(text: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val style = LocalJzStyle.current
    androidx.compose.material3.Text(
        text,
        modifier = modifier,
        fontFamily = if (style == JzStyle.VOYAGER) FontFamily.Serif else null,
        fontSize = style.titleSize.sp,
        fontWeight = style.titleWeight,
        letterSpacing = (-0.3).sp,
    )
}

/** 实际生效的深浅模式（强制浅/深色时与系统不一致，语义色据此取值） */
val LocalJzDark = androidx.compose.runtime.staticCompositionLocalOf { true }

private val VoyagerIncomeLight = Color(0xFF08613E)
private val VoyagerExpenseLight = Color(0xFF9D2733)

@Composable
fun incomeColor(): Color = when {
    LocalJzDark.current -> IncomeDark
    LocalJzStyle.current == JzStyle.VOYAGER -> VoyagerIncomeLight
    else -> IncomeLight
}

@Composable
fun expenseColor(): Color = when {
    LocalJzDark.current -> ExpenseDark
    LocalJzStyle.current == JzStyle.VOYAGER -> VoyagerExpenseLight
    else -> ExpenseLight
}

@Composable
fun warnColor(): Color = if (LocalJzDark.current) WarnDark else WarnLight

/** 主色：一律走 colorScheme（跟随当前预设与深浅模式） */
@Composable
fun accentColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun JizhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: JzPreset = JzPreset.STYLE,
    style: JzStyle = JzStyle.VOYAGER,
    customSeed: Int? = null,
    powerSaving: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        preset == JzPreset.MATERIAL_YOU && android.os.Build.VERSION.SDK_INT >= 31 -> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val dynamic = if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(ctx)
                else androidx.compose.material3.dynamicLightColorScheme(ctx)
            if (style == JzStyle.GLASS || style == JzStyle.COMPACT) dynamic
            else styleColors(style, darkTheme, dynamic.primary)
        }
        else -> {
            val accent = when {
                preset == JzPreset.STYLE -> style.defaultAccent(darkTheme)
                preset == JzPreset.CUSTOM && customSeed != null -> {
                    val (dark, light) = deriveAccentsFromSeed(customSeed)
                    if (darkTheme) dark else light
                }
                else -> {
                    val resolved = if (preset == JzPreset.MATERIAL_YOU) JzPreset.DEEP_SEA else preset
                    if (darkTheme) resolved.accentDark else resolved.accentLight
                }
            }
            styleColors(style, darkTheme, accent)
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalJzDark provides darkTheme,
        LocalJzStyle provides style,
        LocalPowerSaving provides powerSaving,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = JzTypography,
            shapes = style.shapes,
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
