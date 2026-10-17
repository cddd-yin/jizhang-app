package com.expenso.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.expenso.app.R

// Set up Google Fonts provider for "Google Sans"
val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val RobotoFlexName = GoogleFont("Roboto Flex")

val RobotoFlexFontFamily = FontFamily(
    Font(googleFont = RobotoFlexName, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = RobotoFlexName, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = RobotoFlexName, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = RobotoFlexName, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = RobotoFlexName, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = RobotoFlexName, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
    Font(googleFont = RobotoFlexName, fontProvider = fontProvider, weight = FontWeight.Black)
)

val GoogleSansFlexName = GoogleFont("Google Sans Flex")

val GoogleSansFlexFontFamily = FontFamily(
    Font(googleFont = GoogleSansFlexName, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = GoogleSansFlexName, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = GoogleSansFlexName, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleSansFlexName, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleSansFlexName, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = GoogleSansFlexName, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
    Font(googleFont = GoogleSansFlexName, fontProvider = fontProvider, weight = FontWeight.Black)
)

// central Material 3 Typography using Roboto Flex
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = RobotoFlexFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)
