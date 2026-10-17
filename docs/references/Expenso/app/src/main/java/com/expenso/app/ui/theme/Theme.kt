package com.expenso.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.expenso.app.R

// Cosmic Slate
private val CosmicSlateLight = lightColorScheme(
    primary = CosmicSlatePrimaryLight,
    onPrimary = CosmicSlateOnPrimaryLight,
    primaryContainer = CosmicSlatePrimaryContainerLight,
    onPrimaryContainer = CosmicSlateOnPrimaryContainerLight,
    secondary = CosmicSlateSecondaryLight,
    secondaryContainer = CosmicSlateSecondaryContainerLight,
    onSecondaryContainer = CosmicSlateOnSecondaryContainerLight,
    background = CosmicSlateBackgroundLight,
    onBackground = CosmicSlateTextLight,
    surface = CosmicSlateSurfaceLight,
    onSurface = CosmicSlateOnSurfaceLight,
    outline = CosmicSlateOutlineLight,
    outlineVariant = CosmicSlateOutlineVariantLight
)

private val CosmicSlateDark = darkColorScheme(
    primary = CosmicSlatePrimaryDark,
    onPrimary = CosmicSlateOnPrimaryDark,
    primaryContainer = CosmicSlatePrimaryContainerDark,
    onPrimaryContainer = CosmicSlateOnPrimaryContainerDark,
    secondary = CosmicSlateSecondaryDark,
    secondaryContainer = CosmicSlateSecondaryContainerDark,
    onSecondaryContainer = CosmicSlateOnSecondaryContainerDark,
    background = CosmicSlateBackgroundDark,
    onBackground = CosmicSlateTextDark,
    surface = CosmicSlateSurfaceDark,
    onSurface = CosmicSlateOnSurfaceDark,
    outline = CosmicSlateOutlineDark,
    outlineVariant = CosmicSlateOutlineVariantDark
)

// Forest Emerald
private val ForestEmeraldLight = lightColorScheme(
    primary = ForestEmeraldPrimaryLight,
    onPrimary = ForestEmeraldOnPrimaryLight,
    primaryContainer = ForestEmeraldPrimaryContainerLight,
    onPrimaryContainer = ForestEmeraldOnPrimaryContainerLight,
    secondary = ForestEmeraldSecondaryLight,
    secondaryContainer = ForestEmeraldSecondaryContainerLight,
    onSecondaryContainer = ForestEmeraldOnSecondaryContainerLight,
    background = ForestEmeraldBackgroundLight,
    onBackground = ForestEmeraldTextLight,
    surface = ForestEmeraldSurfaceLight,
    onSurface = ForestEmeraldOnSurfaceLight,
    outline = ForestEmeraldOutlineLight,
    outlineVariant = ForestEmeraldOutlineVariantLight
)

private val ForestEmeraldDark = darkColorScheme(
    primary = ForestEmeraldPrimaryDark,
    onPrimary = ForestEmeraldOnPrimaryDark,
    primaryContainer = ForestEmeraldPrimaryContainerDark,
    onPrimaryContainer = ForestEmeraldOnPrimaryContainerDark,
    secondary = ForestEmeraldSecondaryDark,
    secondaryContainer = ForestEmeraldSecondaryContainerDark,
    onSecondaryContainer = ForestEmeraldOnSecondaryContainerDark,
    background = ForestEmeraldBackgroundDark,
    onBackground = ForestEmeraldTextDark,
    surface = ForestEmeraldSurfaceDark,
    onSurface = ForestEmeraldOnSurfaceDark,
    outline = ForestEmeraldOutlineDark,
    outlineVariant = ForestEmeraldOutlineVariantDark
)

// Warm Terracotta
private val WarmTerracottaLight = lightColorScheme(
    primary = WarmTerracottaPrimaryLight,
    onPrimary = WarmTerracottaOnPrimaryLight,
    primaryContainer = WarmTerracottaPrimaryContainerLight,
    onPrimaryContainer = WarmTerracottaOnPrimaryContainerLight,
    secondary = WarmTerracottaSecondaryLight,
    secondaryContainer = WarmTerracottaSecondaryContainerLight,
    onSecondaryContainer = WarmTerracottaOnSecondaryContainerLight,
    background = WarmTerracottaBackgroundLight,
    onBackground = WarmTerracottaTextLight,
    surface = WarmTerracottaSurfaceLight,
    onSurface = WarmTerracottaOnSurfaceLight,
    outline = WarmTerracottaOutlineLight,
    outlineVariant = WarmTerracottaOutlineVariantLight
)

private val WarmTerracottaDark = darkColorScheme(
    primary = WarmTerracottaPrimaryDark,
    onPrimary = WarmTerracottaOnPrimaryDark,
    primaryContainer = WarmTerracottaPrimaryContainerDark,
    onPrimaryContainer = WarmTerracottaOnPrimaryContainerDark,
    secondary = WarmTerracottaSecondaryDark,
    secondaryContainer = WarmTerracottaSecondaryContainerDark,
    onSecondaryContainer = WarmTerracottaOnSecondaryContainerDark,
    background = WarmTerracottaBackgroundDark,
    onBackground = WarmTerracottaTextDark,
    surface = WarmTerracottaSurfaceDark,
    onSurface = WarmTerracottaOnSurfaceDark,
    outline = WarmTerracottaOutlineDark,
    outlineVariant = WarmTerracottaOutlineVariantDark
)

// Deep Indigo
private val DeepIndigoLight = lightColorScheme(
    primary = DeepIndigoPrimaryLight,
    onPrimary = DeepIndigoOnPrimaryLight,
    primaryContainer = DeepIndigoPrimaryContainerLight,
    onPrimaryContainer = DeepIndigoOnPrimaryContainerLight,
    secondary = DeepIndigoSecondaryLight,
    secondaryContainer = DeepIndigoSecondaryContainerLight,
    onSecondaryContainer = DeepIndigoOnSecondaryContainerLight,
    background = DeepIndigoBackgroundLight,
    onBackground = DeepIndigoTextLight,
    surface = DeepIndigoSurfaceLight,
    onSurface = DeepIndigoOnSurfaceLight,
    outline = DeepIndigoOutlineLight,
    outlineVariant = DeepIndigoOutlineVariantLight
)

private val DeepIndigoDark = darkColorScheme(
    primary = DeepIndigoPrimaryDark,
    onPrimary = DeepIndigoOnPrimaryDark,
    primaryContainer = DeepIndigoPrimaryContainerDark,
    onPrimaryContainer = DeepIndigoOnPrimaryContainerDark,
    secondary = DeepIndigoSecondaryDark,
    secondaryContainer = DeepIndigoSecondaryContainerDark,
    onSecondaryContainer = DeepIndigoOnSecondaryContainerDark,
    background = DeepIndigoBackgroundDark,
    onBackground = DeepIndigoTextDark,
    surface = DeepIndigoSurfaceDark,
    onSurface = DeepIndigoOnSurfaceDark,
    outline = DeepIndigoOutlineDark,
    outlineVariant = DeepIndigoOutlineVariantDark
)

// Sunset Gold
private val SunsetGoldLight = lightColorScheme(
    primary = SunsetGoldPrimaryLight,
    onPrimary = SunsetGoldOnPrimaryLight,
    primaryContainer = SunsetGoldPrimaryContainerLight,
    onPrimaryContainer = SunsetGoldOnPrimaryContainerLight,
    secondary = SunsetGoldSecondaryLight,
    secondaryContainer = SunsetGoldSecondaryContainerLight,
    onSecondaryContainer = SunsetGoldOnSecondaryContainerLight,
    background = SunsetGoldBackgroundLight,
    onBackground = SunsetGoldTextLight,
    surface = SunsetGoldSurfaceLight,
    onSurface = SunsetGoldOnSurfaceLight,
    outline = SunsetGoldOutlineLight,
    outlineVariant = SunsetGoldOutlineVariantLight
)

private val SunsetGoldDark = darkColorScheme(
    primary = SunsetGoldPrimaryDark,
    onPrimary = SunsetGoldOnPrimaryDark,
    primaryContainer = SunsetGoldPrimaryContainerDark,
    onPrimaryContainer = SunsetGoldOnPrimaryContainerDark,
    secondary = SunsetGoldSecondaryDark,
    secondaryContainer = SunsetGoldSecondaryContainerDark,
    onSecondaryContainer = SunsetGoldOnSecondaryContainerDark,
    background = SunsetGoldBackgroundDark,
    onBackground = SunsetGoldTextDark,
    surface = SunsetGoldSurfaceDark,
    onSurface = SunsetGoldOnSurfaceDark,
    outline = SunsetGoldOutlineDark,
    outlineVariant = SunsetGoldOutlineVariantDark
)

// Lavender Dream
private val LavenderDreamLight = lightColorScheme(
    primary = LavenderDreamPrimaryLight,
    onPrimary = LavenderDreamOnPrimaryLight,
    primaryContainer = LavenderDreamPrimaryContainerLight,
    onPrimaryContainer = LavenderDreamOnPrimaryContainerLight,
    secondary = LavenderDreamSecondaryLight,
    secondaryContainer = LavenderDreamSecondaryContainerLight,
    onSecondaryContainer = LavenderDreamOnSecondaryContainerLight,
    background = LavenderDreamBackgroundLight,
    onBackground = LavenderDreamTextLight,
    surface = LavenderDreamSurfaceLight,
    onSurface = LavenderDreamOnSurfaceLight,
    outline = LavenderDreamOutlineLight,
    outlineVariant = LavenderDreamOutlineVariantLight
)

private val LavenderDreamDark = darkColorScheme(
    primary = LavenderDreamPrimaryDark,
    onPrimary = LavenderDreamOnPrimaryDark,
    primaryContainer = LavenderDreamPrimaryContainerDark,
    onPrimaryContainer = LavenderDreamOnPrimaryContainerDark,
    secondary = LavenderDreamSecondaryDark,
    secondaryContainer = LavenderDreamSecondaryContainerDark,
    onSecondaryContainer = LavenderDreamOnSecondaryContainerDark,
    background = LavenderDreamBackgroundDark,
    onBackground = LavenderDreamTextDark,
    surface = LavenderDreamSurfaceDark,
    onSurface = LavenderDreamOnSurfaceDark,
    outline = LavenderDreamOutlineDark,
    outlineVariant = LavenderDreamOutlineVariantDark
)

// Expenso
private val ExpensoLight = lightColorScheme(
    primary = ExpensoPrimaryLight,
    onPrimary = ExpensoOnPrimaryLight,
    primaryContainer = ExpensoPrimaryContainerLight,
    onPrimaryContainer = ExpensoOnPrimaryContainerLight,
    secondary = ExpensoSecondaryLight,
    secondaryContainer = ExpensoSecondaryContainerLight,
    onSecondaryContainer = ExpensoOnSecondaryContainerLight,
    background = ExpensoBackgroundLight,
    onBackground = ExpensoTextLight,
    surface = ExpensoSurfaceLight,
    onSurface = ExpensoOnSurfaceLight,
    outline = ExpensoOutlineLight,
    outlineVariant = ExpensoOutlineVariantLight
)

private val ExpensoDark = darkColorScheme(
    primary = ExpensoPrimaryDark,
    onPrimary = ExpensoOnPrimaryDark,
    primaryContainer = ExpensoPrimaryContainerDark,
    onPrimaryContainer = ExpensoOnPrimaryContainerDark,
    secondary = ExpensoSecondaryDark,
    secondaryContainer = ExpensoSecondaryContainerDark,
    onSecondaryContainer = ExpensoOnSecondaryContainerDark,
    background = ExpensoBackgroundDark,
    onBackground = ExpensoTextDark,
    surface = ExpensoSurfaceDark,
    onSurface = ExpensoOnSurfaceDark,
    outline = ExpensoOutlineDark,
    outlineVariant = ExpensoOutlineVariantDark
)

// Amoled
private val AmoledLight = lightColorScheme(
    primary = AmoledPrimaryLight,
    onPrimary = AmoledOnPrimaryLight,
    primaryContainer = AmoledPrimaryContainerLight,
    onPrimaryContainer = AmoledOnPrimaryContainerLight,
    secondary = AmoledSecondaryLight,
    secondaryContainer = AmoledSecondaryContainerLight,
    onSecondaryContainer = AmoledOnSecondaryContainerLight,
    background = AmoledBackgroundLight,
    onBackground = AmoledTextLight,
    surface = AmoledSurfaceLight,
    onSurface = AmoledOnSurfaceLight,
    outline = AmoledOutlineLight,
    outlineVariant = AmoledOutlineVariantLight
)

private val AmoledDark = darkColorScheme(
    primary = AmoledPrimaryDark,
    onPrimary = AmoledOnPrimaryDark,
    primaryContainer = AmoledPrimaryContainerDark,
    onPrimaryContainer = AmoledOnPrimaryContainerDark,
    secondary = AmoledSecondaryDark,
    secondaryContainer = AmoledSecondaryContainerDark,
    onSecondaryContainer = AmoledOnSecondaryContainerDark,
    background = AmoledBackgroundDark,
    onBackground = AmoledTextDark,
    surface = AmoledSurfaceDark,
    onSurface = AmoledOnSurfaceDark,
    outline = AmoledOutlineDark,
    outlineVariant = AmoledOutlineVariantDark
)

// Plum
private val PlumLight = lightColorScheme(
    primary = PlumPrimaryLight,
    onPrimary = PlumOnPrimaryLight,
    primaryContainer = PlumPrimaryContainerLight,
    onPrimaryContainer = PlumOnPrimaryContainerLight,
    secondary = PlumSecondaryLight,
    secondaryContainer = PlumSecondaryContainerLight,
    onSecondaryContainer = PlumOnSecondaryContainerLight,
    background = PlumBackgroundLight,
    onBackground = PlumTextLight,
    surface = PlumSurfaceLight,
    onSurface = PlumOnSurfaceLight,
    outline = PlumOutlineLight,
    outlineVariant = PlumOutlineVariantLight
)

private val PlumDark = darkColorScheme(
    primary = PlumPrimaryDark,
    onPrimary = PlumOnPrimaryDark,
    primaryContainer = PlumPrimaryContainerDark,
    onPrimaryContainer = PlumOnPrimaryContainerDark,
    secondary = PlumSecondaryDark,
    secondaryContainer = PlumSecondaryContainerDark,
    onSecondaryContainer = PlumOnSecondaryContainerDark,
    background = PlumBackgroundDark,
    onBackground = PlumTextDark,
    surface = PlumSurfaceDark,
    onSurface = PlumOnSurfaceDark,
    outline = PlumOutlineDark,
    outlineVariant = PlumOutlineVariantDark
)

private val PlumMediumLight = lightColorScheme(
    primary = PlumMediumPrimaryLight,
    onPrimary = PlumMediumOnPrimaryLight,
    primaryContainer = PlumMediumPrimaryContainerLight,
    onPrimaryContainer = PlumMediumOnPrimaryContainerLight,
    secondary = PlumMediumSecondaryLight,
    secondaryContainer = PlumMediumSecondaryContainerLight,
    onSecondaryContainer = PlumMediumOnSecondaryContainerLight,
    background = PlumMediumBackgroundLight,
    onBackground = PlumMediumTextLight,
    surface = PlumMediumSurfaceLight,
    onSurface = PlumMediumOnSurfaceLight,
    outline = PlumMediumOutlineLight,
    outlineVariant = PlumMediumOutlineVariantLight
)

private val PlumMediumDark = darkColorScheme(
    primary = PlumMediumPrimaryDark,
    onPrimary = PlumMediumOnPrimaryDark,
    primaryContainer = PlumMediumPrimaryContainerDark,
    onPrimaryContainer = PlumMediumOnPrimaryContainerDark,
    secondary = PlumMediumSecondaryDark,
    secondaryContainer = PlumMediumSecondaryContainerDark,
    onSecondaryContainer = PlumMediumOnSecondaryContainerDark,
    background = PlumMediumBackgroundDark,
    onBackground = PlumMediumTextDark,
    surface = PlumMediumSurfaceDark,
    onSurface = PlumMediumOnSurfaceDark,
    outline = PlumMediumOutlineDark,
    outlineVariant = PlumMediumOutlineVariantDark
)

private val PlumHighLight = lightColorScheme(
    primary = PlumHighPrimaryLight,
    onPrimary = PlumHighOnPrimaryLight,
    primaryContainer = PlumHighPrimaryContainerLight,
    onPrimaryContainer = PlumHighOnPrimaryContainerLight,
    secondary = PlumHighSecondaryLight,
    secondaryContainer = PlumHighSecondaryContainerLight,
    onSecondaryContainer = PlumHighOnSecondaryContainerLight,
    background = PlumHighBackgroundLight,
    onBackground = PlumHighTextLight,
    surface = PlumHighSurfaceLight,
    onSurface = PlumHighOnSurfaceLight,
    outline = PlumHighOutlineLight,
    outlineVariant = PlumHighOutlineVariantLight
)

private val PlumHighDark = darkColorScheme(
    primary = PlumHighPrimaryDark,
    onPrimary = PlumHighOnPrimaryDark,
    primaryContainer = PlumHighPrimaryContainerDark,
    onPrimaryContainer = PlumHighOnPrimaryContainerDark,
    secondary = PlumHighSecondaryDark,
    secondaryContainer = PlumHighSecondaryContainerDark,
    onSecondaryContainer = PlumHighOnSecondaryContainerDark,
    background = PlumHighBackgroundDark,
    onBackground = PlumHighTextDark,
    surface = PlumHighSurfaceDark,
    onSurface = PlumHighOnSurfaceDark,
    outline = PlumHighOutlineDark,
    outlineVariant = PlumHighOutlineVariantDark
)

private val PlumAmoledDark = darkColorScheme(
    primary = PlumAmoledPrimaryDark,
    onPrimary = PlumAmoledOnPrimaryDark,
    primaryContainer = PlumAmoledPrimaryContainerDark,
    onPrimaryContainer = PlumAmoledOnPrimaryContainerDark,
    secondary = PlumAmoledSecondaryDark,
    secondaryContainer = PlumAmoledSecondaryContainerDark,
    onSecondaryContainer = PlumAmoledOnSecondaryContainerDark,
    background = PlumAmoledBackgroundDark,
    onBackground = PlumAmoledTextDark,
    surface = PlumAmoledSurfaceDark,
    onSurface = PlumAmoledOnSurfaceDark,
    outline = PlumAmoledOutlineDark,
    outlineVariant = PlumAmoledOutlineVariantDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPalette: String = "Default",
    content: @Composable () -> Unit
) {
    val matchingTheme = AppTheme.values().find { it.getFriendlyName() == colorPalette }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = if (matchingTheme?.isDynamic == true && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
    } else if (matchingTheme != null) {
        if (darkTheme) matchingTheme.getDarkColorScheme() else matchingTheme.getLightColorScheme()
    } else {
        when (colorPalette) {
            "Deep Indigo" -> if (darkTheme) DeepIndigoDark else DeepIndigoLight
            "Lavender Dream" -> if (darkTheme) LavenderDreamDark else LavenderDreamLight
            else -> if (darkTheme) CosmicSlateDark else CosmicSlateLight // Default is Default (formerly Cosmic Slate)
        }
    }

    val currentDensity = LocalDensity.current
    val fontScaleFreeDensity = Density(currentDensity.density, 1f)

    CompositionLocalProvider(
        LocalDensity provides fontScaleFreeDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = {
                CompositionLocalProvider(
                    LocalSpacing provides Spacing()
                ) {
                    content()
                }
            }
        )
    }
}

enum class AppTheme(
    val titleRes: Int,
    val primaryLight: Color,
    val primaryDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val tertiaryLight: Color,
    val tertiaryDark: Color,
    val backgroundLight: Color,
    val backgroundDark: Color,
    val isDynamic: Boolean = false
) {
    MaterialYou(
        titleRes = R.string.theme_material_you,
        primaryLight = Color(0xFF6750A4),
        primaryDark = Color(0xFFD0BCFF),
        secondaryLight = Color(0xFF625B71),
        secondaryDark = Color(0xFFCCC2DC),
        tertiaryLight = Color(0xFF7D5260),
        tertiaryDark = Color(0xFFEFB8C8),
        backgroundLight = Color(0xFFFFFBFF),
        backgroundDark = Color(0xFF1C1B1F),
        isDynamic = true,
    ),
    Catppuccin(
        titleRes = R.string.theme_catppuccin,
        primaryLight = Color(0xFF4C6B9A),
        primaryDark = Color(0xFF9BA8CF),
        secondaryLight = Color(0xFFB76B8F),
        secondaryDark = Color(0xFFD4A5B8),
        tertiaryLight = Color(0xFFB8763E),
        tertiaryDark = Color(0xFF8AB8A8),
        backgroundLight = Color(0xFFEFF1F5),
        backgroundDark = Color(0xFF1E1E2E),
    ),
    Cloudflare(
        titleRes = R.string.theme_cloudflare,
        primaryLight = Color(0xFFF6821F),
        primaryDark = Color(0xFFFFB77C),
        secondaryLight = Color(0xFF6B5E4C),
        secondaryDark = Color(0xFFD6C5AC),
        tertiaryLight = Color(0xFF855316),
        tertiaryDark = Color(0xFFFABD71),
        backgroundLight = Color(0xFFFFFBF7),
        backgroundDark = Color(0xFF1A1612),
    ),
    CottonCandy(
        titleRes = R.string.theme_cotton_candy,
        primaryLight = Color(0xFFE993C1),
        primaryDark = Color(0xFFFFB1D5),
        secondaryLight = Color(0xFF70A2C2),
        secondaryDark = Color(0xFF9ED0EF),
        tertiaryLight = Color(0xFF9C68AC),
        tertiaryDark = Color(0xFFDEB0E9),
        backgroundLight = Color(0xFFFFF8FA),
        backgroundDark = Color(0xFF1A1418),
    ),
    Doom(
        titleRes = R.string.theme_doom,
        primaryLight = Color(0xFFBB2929),
        primaryDark = Color(0xFFFF6B6B),
        secondaryLight = Color(0xFF6B5353),
        secondaryDark = Color(0xFFD6BABA),
        tertiaryLight = Color(0xFF8C4A4A),
        tertiaryDark = Color(0xFFFFB4AB),
        backgroundLight = Color(0xFFFFF8F7),
        backgroundDark = Color(0xFF1A1010),
    ),
    GreenApple(
        titleRes = R.string.theme_green_apple,
        primaryLight = Color(0xFF2E7D32),
        primaryDark = Color(0xFF81C784),
        secondaryLight = Color(0xFF4A6349),
        secondaryDark = Color(0xFFB0CFB1),
        tertiaryLight = Color(0xFF3D7B5F),
        tertiaryDark = Color(0xFF8FD5B7),
        backgroundLight = Color(0xFFF6FFF6),
        backgroundDark = Color(0xFF0F1A0F),
    ),
    Gruvbox(
        titleRes = R.string.theme_gruvbox,
        primaryLight = Color(0xFF9D5B3F),
        primaryDark = Color(0xFFD89B6A),
        secondaryLight = Color(0xFF7A7556),
        secondaryDark = Color(0xFFB0AE8A),
        tertiaryLight = Color(0xFF4A7B7C),
        tertiaryDark = Color(0xFF8AAFA8),
        backgroundLight = Color(0xFFFBF1C7),
        backgroundDark = Color(0xFF282828),
    ),
    Kanagawa(
        titleRes = R.string.theme_kanagawa,
        primaryLight = Color(0xFF5A7785),
        primaryDark = Color(0xFF7E9CD8),
        secondaryLight = Color(0xFF8A7A6E),
        secondaryDark = Color(0xFFDCA561),
        tertiaryLight = Color(0xFF6A8E7F),
        tertiaryDark = Color(0xFF98BB6C),
        backgroundLight = Color(0xFFF2ECBC),
        backgroundDark = Color(0xFF1F1F28),
    ),
    Lavender(
        titleRes = R.string.theme_lavender,
        primaryLight = Color(0xFF7C5AB8),
        primaryDark = Color(0xFFCFBCFF),
        secondaryLight = Color(0xFF635B70),
        secondaryDark = Color(0xFFCBC3DA),
        tertiaryLight = Color(0xFF7E525A),
        tertiaryDark = Color(0xFFF2B8C1),
        backgroundLight = Color(0xFFFCF8FF),
        backgroundDark = Color(0xFF16121A),
    ),
    Midnight(
        titleRes = R.string.theme_midnight,
        primaryLight = Color(0xFF0D47A1),
        primaryDark = Color(0xFF90CAF9),
        secondaryLight = Color(0xFF455A64),
        secondaryDark = Color(0xFFB0BEC5),
        tertiaryLight = Color(0xFF1565C0),
        tertiaryDark = Color(0xFF64B5F6),
        backgroundLight = Color(0xFFF5F9FF),
        backgroundDark = Color(0xFF0D1117),
    ),
    Mocha(
        titleRes = R.string.theme_mocha,
        primaryLight = Color(0xFF795548),
        primaryDark = Color(0xFFBCAAA4),
        secondaryLight = Color(0xFF5D4037),
        secondaryDark = Color(0xFFA1887F),
        tertiaryLight = Color(0xFF6D4C41),
        tertiaryDark = Color(0xFFD7CCC8),
        backgroundLight = Color(0xFFFFF9F5),
        backgroundDark = Color(0xFF1A1512),
    ),
    Strawberry(
        titleRes = R.string.theme_strawberry,
        primaryLight = Color(0xFFD81B60),
        primaryDark = Color(0xFFF48FB1),
        secondaryLight = Color(0xFF6B4958),
        secondaryDark = Color(0xFFD6B0C1),
        tertiaryLight = Color(0xFFC2185B),
        tertiaryDark = Color(0xFFF8BBD9),
        backgroundLight = Color(0xFFFFF5F8),
        backgroundDark = Color(0xFF1A1015),
    ),
    Tidal(
        titleRes = R.string.theme_tidal,
        primaryLight = Color(0xFF00796B),
        primaryDark = Color(0xFF80CBC4),
        secondaryLight = Color(0xFF4A635E),
        secondaryDark = Color(0xFFB0CFC9),
        tertiaryLight = Color(0xFF00897B),
        tertiaryDark = Color(0xFF4DB6AC),
        backgroundLight = Color(0xFFF2FFFD),
        backgroundDark = Color(0xFF0F1A18),
    ),
    Nord(
        titleRes = R.string.theme_nord,
        primaryLight = Color(0xFF5E81AC),
        primaryDark = Color(0xFF88C0D0),
        secondaryLight = Color(0xFF4C566A),
        secondaryDark = Color(0xFFD8DEE9),
        tertiaryLight = Color(0xFFB48EAD),
        tertiaryDark = Color(0xFFD8A9C4),
        backgroundLight = Color(0xFFECEFF4),
        backgroundDark = Color(0xFF2E3440),
    ),
    RosePine(
        titleRes = R.string.theme_rose_pine,
        primaryLight = Color(0xFF907AA9),
        primaryDark = Color(0xFFC4A7E7),
        secondaryLight = Color(0xFFB4637A),
        secondaryDark = Color(0xFFEBBCBA),
        tertiaryLight = Color(0xFF7A9A8A),
        tertiaryDark = Color(0xFF9CCFD8),
        backgroundLight = Color(0xFFFAF4ED),
        backgroundDark = Color(0xFF232136),
    ),
    TakoGreen(
        titleRes = R.string.theme_tako_green,
        primaryLight = Color(0xFF66BB6A),
        primaryDark = Color(0xFFA5D6A7),
        secondaryLight = Color(0xFF546E7A),
        secondaryDark = Color(0xFF90A4AE),
        tertiaryLight = Color(0xFF43A047),
        tertiaryDark = Color(0xFF81C784),
        backgroundLight = Color(0xFFF5FFF5),
        backgroundDark = Color(0xFF121A12),
    ),
    TokyoNight(
        titleRes = R.string.theme_tokyo_night,
        primaryLight = Color(0xFF3D5A80),
        primaryDark = Color(0xFF7D9BC1),
        secondaryLight = Color(0xFF6B5B95),
        secondaryDark = Color(0xFFA89DC9),
        tertiaryLight = Color(0xFF4A6B5C),
        tertiaryDark = Color(0xFF8AB4A3),
        backgroundLight = Color(0xFFF0F1F5),
        backgroundDark = Color(0xFF1A1B26),
    ),
    YinYang(
        titleRes = R.string.theme_yin_yang,
        primaryLight = Color(0xFF424242),
        primaryDark = Color(0xFFBDBDBD),
        secondaryLight = Color(0xFF616161),
        secondaryDark = Color(0xFFE0E0E0),
        tertiaryLight = Color(0xFF757575),
        tertiaryDark = Color(0xFFEEEEEE),
        backgroundLight = Color(0xFFFAFAFA),
        backgroundDark = Color(0xFF121212),
    ),
    Yotsuba(
        titleRes = R.string.theme_yotsuba,
        primaryLight = Color(0xFFFF8A65),
        primaryDark = Color(0xFFFFAB91),
        secondaryLight = Color(0xFF6D5D5B),
        secondaryDark = Color(0xFFD6C4C2),
        tertiaryLight = Color(0xFFFF7043),
        tertiaryDark = Color(0xFFFFCCBC),
        backgroundLight = Color(0xFFFFF8F5),
        backgroundDark = Color(0xFF1A1412),
    ),
    Sapphire(
        titleRes = R.string.theme_sapphire,
        primaryLight = Color(0xFF1E88E5),
        primaryDark = Color(0xFF64B5F6),
        secondaryLight = Color(0xFF5C6BC0),
        secondaryDark = Color(0xFF9FA8DA),
        tertiaryLight = Color(0xFF0288D1),
        tertiaryDark = Color(0xFF4FC3F7),
        backgroundLight = Color(0xFFF3F8FF),
        backgroundDark = Color(0xFF0D1620),
    ),
    Sunset(
        titleRes = R.string.theme_sunset,
        primaryLight = Color(0xFFE65100),
        primaryDark = Color(0xFFFF9E80),
        secondaryLight = Color(0xFFEF6C00),
        secondaryDark = Color(0xFFFFCC80),
        tertiaryLight = Color(0xFFF4511E),
        tertiaryDark = Color(0xFFFF8A65),
        backgroundLight = Color(0xFFFFF5F0),
        backgroundDark = Color(0xFF1A120D),
    ),
    Ocean(
        titleRes = R.string.theme_ocean,
        primaryLight = Color(0xFF006064),
        primaryDark = Color(0xFF4DD0E1),
        secondaryLight = Color(0xFF00838F),
        secondaryDark = Color(0xFF80DEEA),
        tertiaryLight = Color(0xFF0097A7),
        tertiaryDark = Color(0xFF26C6DA),
        backgroundLight = Color(0xFFF0FFFF),
        backgroundDark = Color(0xFF0A1A1C),
    ),
    Forest(
        titleRes = R.string.theme_forest,
        primaryLight = Color(0xFF1B5E20),
        primaryDark = Color(0xFF66BB6A),
        secondaryLight = Color(0xFF33691E),
        secondaryDark = Color(0xFF9CCC65),
        tertiaryLight = Color(0xFF2E7D32),
        tertiaryDark = Color(0xFFA5D6A7),
        backgroundLight = Color(0xFFF1F8E9),
        backgroundDark = Color(0xFF0D1A0D),
    ),
    RoseGold(
        titleRes = R.string.theme_rose_gold,
        primaryLight = Color(0xFFB76E79),
        primaryDark = Color(0xFFE8A9B0),
        secondaryLight = Color(0xFFAD8075),
        secondaryDark = Color(0xFFDDBFB8),
        tertiaryLight = Color(0xFFD4A5A5),
        tertiaryDark = Color(0xFFF5D5D5),
        backgroundLight = Color(0xFFFFF5F5),
        backgroundDark = Color(0xFF1A1315),
    ),
    Violet(
        titleRes = R.string.theme_violet,
        primaryLight = Color(0xFF6A1B9A),
        primaryDark = Color(0xFFCE93D8),
        secondaryLight = Color(0xFF7B1FA2),
        secondaryDark = Color(0xFFE1BEE7),
        tertiaryLight = Color(0xFF8E24AA),
        tertiaryDark = Color(0xFFBA68C8),
        backgroundLight = Color(0xFFFCF5FF),
        backgroundDark = Color(0xFF150D1A),
    ),
    Amber(
        titleRes = R.string.theme_amber,
        primaryLight = Color(0xFFFF8F00),
        primaryDark = Color(0xFFFFCA28),
        secondaryLight = Color(0xFFFFA000),
        secondaryDark = Color(0xFFFFD54F),
        tertiaryLight = Color(0xFFFFB300),
        tertiaryDark = Color(0xFFFFE082),
        backgroundLight = Color(0xFFFFFBF0),
        backgroundDark = Color(0xFF1A1508),
    ),
    Coral(
        titleRes = R.string.theme_coral,
        primaryLight = Color(0xFFFF5252),
        primaryDark = Color(0xFFFF8A80),
        secondaryLight = Color(0xFFFF6E40),
        secondaryDark = Color(0xFFFFAB91),
        tertiaryLight = Color(0xFFFF7043),
        tertiaryDark = Color(0xFFFFCCBC),
        backgroundLight = Color(0xFFFFF5F5),
        backgroundDark = Color(0xFF1A1010),
    ),
    Slate(
        titleRes = R.string.theme_slate,
        primaryLight = Color(0xFF455A64),
        primaryDark = Color(0xFF90A4AE),
        secondaryLight = Color(0xFF546E7A),
        secondaryDark = Color(0xFFB0BEC5),
        tertiaryLight = Color(0xFF607D8B),
        tertiaryDark = Color(0xFFCFD8DC),
        backgroundLight = Color(0xFFF5F7F8),
        backgroundDark = Color(0xFF151A1C),
    ),
    Dracula(
        titleRes = R.string.theme_dracula,
        primaryLight = Color(0xFF6272A4),
        primaryDark = Color(0xFFBD93F9),
        secondaryLight = Color(0xFF44475A),
        secondaryDark = Color(0xFFFF79C6),
        tertiaryLight = Color(0xFF50FA7B),
        tertiaryDark = Color(0xFF8BE9FD),
        backgroundLight = Color(0xFFF8F8F2),
        backgroundDark = Color(0xFF282A36),
    ),
    Monochrome(
        titleRes = R.string.theme_monochrome,
        primaryLight = Color(0xFF212121),
        primaryDark = Color(0xFFE0E0E0),
        secondaryLight = Color(0xFF424242),
        secondaryDark = Color(0xFFBDBDBD),
        tertiaryLight = Color(0xFF616161),
        tertiaryDark = Color(0xFF9E9E9E),
        backgroundLight = Color(0xFFFFFFFF),
        backgroundDark = Color(0xFF0A0A0A),
    );

    /**
     * Get the light color scheme for this theme
     */
    fun getLightColorScheme(): ColorScheme {
        val surfaceTint = primaryLight.copy(alpha = 0.05f).compositeOver(backgroundLight)
        return lightColorScheme(
            primary = primaryLight,
            onPrimary = Color.White,
            primaryContainer = primaryLight.copy(alpha = 0.15f).compositeOver(Color.White),
            onPrimaryContainer = primaryLight.darken(0.3f),
            secondary = secondaryLight,
            onSecondary = Color.White,
            secondaryContainer = secondaryLight.copy(alpha = 0.15f).compositeOver(Color.White),
            onSecondaryContainer = secondaryLight.darken(0.3f),
            tertiary = tertiaryLight,
            onTertiary = Color.White,
            tertiaryContainer = tertiaryLight.copy(alpha = 0.15f).compositeOver(Color.White),
            onTertiaryContainer = tertiaryLight.darken(0.3f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF93000A),
            background = backgroundLight,
            onBackground = Color(0xFF1C1B1F),
            surface = backgroundLight,
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = primaryLight.copy(alpha = 0.08f).compositeOver(Color(0xFFF0F0F0)),
            onSurfaceVariant = Color(0xFF49454F),
            outline = secondaryLight.copy(alpha = 0.5f).compositeOver(Color(0xFF79747E)),
            outlineVariant = primaryLight.copy(alpha = 0.12f).compositeOver(Color(0xFFCAC4D0)),
            inverseSurface = backgroundDark,
            inverseOnSurface = Color(0xFFF4EFF4),
            inversePrimary = primaryDark,
            surfaceContainerLowest = backgroundLight,
            surfaceContainerLow = surfaceTint,
            surfaceContainer = primaryLight.copy(alpha = 0.06f).compositeOver(backgroundLight),
            surfaceContainerHigh = primaryLight.copy(alpha = 0.08f).compositeOver(backgroundLight),
            surfaceContainerHighest = primaryLight.copy(alpha = 0.11f).compositeOver(backgroundLight),
        )
    }

    /**
     * Get the dark color scheme for this theme
     */
    fun getDarkColorScheme(): ColorScheme {
        val surfaceTint = primaryDark.copy(alpha = 0.05f).compositeOver(backgroundDark)
        return darkColorScheme(
            primary = primaryDark,
            onPrimary = primaryLight.darken(0.5f),
            primaryContainer = primaryLight.darken(0.3f),
            onPrimaryContainer = primaryDark.lighten(0.1f),
            secondary = secondaryDark,
            onSecondary = secondaryLight.darken(0.5f),
            secondaryContainer = secondaryLight.darken(0.3f),
            onSecondaryContainer = secondaryDark.lighten(0.1f),
            tertiary = tertiaryDark,
            onTertiary = tertiaryLight.darken(0.5f),
            tertiaryContainer = tertiaryLight.darken(0.3f),
            onTertiaryContainer = tertiaryDark.lighten(0.1f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = backgroundDark,
            onBackground = Color(0xFFE6E1E5),
            surface = backgroundDark,
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = primaryDark.copy(alpha = 0.12f).compositeOver(Color(0xFF2A2A2A)),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = secondaryDark.copy(alpha = 0.4f).compositeOver(Color(0xFF938F99)),
            outlineVariant = primaryDark.copy(alpha = 0.15f).compositeOver(Color(0xFF49454F)),
            inverseSurface = backgroundLight,
            inverseOnSurface = Color(0xFF313033),
            inversePrimary = primaryLight,
            surfaceContainerLowest = backgroundDark.darken(0.2f),
            surfaceContainerLow = surfaceTint,
            surfaceContainer = primaryDark.copy(alpha = 0.05f).compositeOver(backgroundDark),
            surfaceContainerHigh = primaryDark.copy(alpha = 0.08f).compositeOver(backgroundDark),
            surfaceContainerHighest = primaryDark.copy(alpha = 0.11f).compositeOver(backgroundDark),
        )
    }

    /**
     * Get the AMOLED (pure black) color scheme for this theme
     */
    fun getAmoledColorScheme(): ColorScheme = getDarkColorScheme().copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = primaryDark.copy(alpha = 0.08f).compositeOver(Color(0xFF1A1A1A)),
        surfaceContainer = Color(0xFF0A0A0A),
        surfaceContainerLow = Color(0xFF050505),
        surfaceContainerLowest = Color.Black,
        surfaceContainerHigh = primaryDark.copy(alpha = 0.05f).compositeOver(Color(0xFF151515)),
        surfaceContainerHighest = primaryDark.copy(alpha = 0.08f).compositeOver(Color(0xFF1F1F1F)),
        surfaceDim = Color.Black,
        surfaceBright = primaryDark.copy(alpha = 0.06f).compositeOver(Color(0xFF2A2A2A)),
    )
}

fun AppTheme.getFriendlyName(): String {
    return when (this) {
        AppTheme.MaterialYou -> "Material You"
        AppTheme.CottonCandy -> "Cotton Candy"
        AppTheme.GreenApple -> "Green Apple"
        AppTheme.RosePine -> "Rose Pine"
        AppTheme.TakoGreen -> "Tako Green"
        AppTheme.TokyoNight -> "Tokyo Night"
        AppTheme.YinYang -> "Yin Yang"
        AppTheme.RoseGold -> "Rose Gold"
        else -> name
    }
}

// Extension functions for color manipulation
private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun Color.lighten(factor: Float): Color {
    return Color(
        red = (red + (1 - red) * factor).coerceIn(0f, 1f),
        green = (green + (1 - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun Color.compositeOver(background: Color): Color {
    val bgAlpha = background.alpha
    val fgAlpha = alpha
    val a = fgAlpha + bgAlpha * (1f - fgAlpha)
    return if (a == 0f) {
        Color.Transparent
    } else {
        Color(
            red = (red * fgAlpha + background.red * bgAlpha * (1f - fgAlpha)) / a,
            green = (green * fgAlpha + background.green * bgAlpha * (1f - fgAlpha)) / a,
            blue = (blue * fgAlpha + background.blue * bgAlpha * (1f - fgAlpha)) / a,
            alpha = a
        )
    }
}
