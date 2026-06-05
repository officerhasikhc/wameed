content:
package com.wameed.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.wameed.R

/* ============================================================================
 *  WAMEED Typography — خط Cairo العربي العصري
 *  أحجام متوازنة مع letter spacing مناسب للعربية
 * ============================================================================ */

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

val CairoFont = GoogleFont("Cairo")

val CairoFontFamily = FontFamily(
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.ExtraBold),
)

private val Cairo = CairoFontFamily

val Typography = Typography(
    /* عناوين كبيرة جداً (نادراً ما تُستخدم) */
    displayLarge = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 44.sp
    ),
    displaySmall = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp
    ),

    /* عناوين الشاشات */
    headlineLarge = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp
    ),

    /* عناوين البطاقات والأقسام */
    titleLarge = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp
    ),

    /* النصوص العادية */
    bodyLarge = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp
    ),

    /* تسميات الأزرار والـ chips */
    labelLarge = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Cairo, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)