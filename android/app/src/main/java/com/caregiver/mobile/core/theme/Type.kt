package com.caregiver.mobile.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.R

/**
 * Single source of truth: design/tokens.json `typography`.
 * Latin: Inter. Arabic: Noto Naskh Arabic (bundled variable TTFs in
 * res/font, OFL licenses in android/fonts/). One FontFamily covers both
 * locales; glyph fallback follows the token order. IBM Plex removed.
 * Scale: display 28/36/700, title 22/28/700, heading 18/24/600,
 * body 16/24/400, label 14/20/500, caption 12/16/400.
 * Arabic line height is 1.2x via [arabicLineHeight] — callers pass
 * arabic=true when Locale is ar. Minimum body size is 16sp.
 */
val AppFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold),
    Font(R.font.noto_naskh_arabic, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic, FontWeight.Medium),
    Font(R.font.noto_naskh_arabic, FontWeight.SemiBold),
    Font(R.font.noto_naskh_arabic, FontWeight.Bold),
)

private const val ArabicLineHeightMultiplier = 1.2f

/** Line height for a token base value, scaled 1.2x for Arabic. */
fun arabicLineHeight(baseSp: Int, arabic: Boolean): TextUnit {
    val value = if (arabic) baseSp * ArabicLineHeightMultiplier else baseSp.toFloat()
    return value.sp
}

private val Base = Typography()

/**
 * App type scale voiced in [AppFontFamily]. Token styles map onto
 * Material3 roles: display->displayLarge, title->titleLarge/headline,
 * heading->titleMedium, body->bodyLarge/bodyMedium, label->labelLarge,
 * caption->bodySmall/labelSmall. All roles share the family in one place.
 */
val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(
        fontFamily = AppFontFamily,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    ),
    displayMedium = Base.displayMedium.copy(
        fontFamily = AppFontFamily,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    ),
    displaySmall = Base.displaySmall.copy(
        fontFamily = AppFontFamily,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineLarge = Base.headlineLarge.copy(
        fontFamily = AppFontFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = Base.headlineMedium.copy(
        fontFamily = AppFontFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = Base.headlineSmall.copy(
        fontFamily = AppFontFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = Base.titleLarge.copy(
        fontFamily = AppFontFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = Base.titleMedium.copy(
        fontFamily = AppFontFamily,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = Base.titleSmall.copy(
        fontFamily = AppFontFamily,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = Base.bodyLarge.copy(
        fontFamily = AppFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = Base.bodyMedium.copy(
        fontFamily = AppFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = Base.bodySmall.copy(
        fontFamily = AppFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = Base.labelLarge.copy(
        fontFamily = AppFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = Base.labelMedium.copy(
        fontFamily = AppFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = Base.labelSmall.copy(
        fontFamily = AppFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
)
