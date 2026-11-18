package com.caregiver.mobile.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.caregiver.mobile.R

/**
 * Board typeface: IBM Plex Sans Arabic (OFL), bundled in `res/font` so no
 * phone install or downloadable-fonts provider is needed. Plex Arabic
 * carries its own Latin glyphs, so English mode needs no second family.
 */
val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

private val Base = Typography()

/**
 * App type scale: Material3 defaults voiced in Plex Arabic. The family is
 * applied to every scale in one place, so all screens change together.
 */
val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = PlexArabic),
    displayMedium = Base.displayMedium.copy(fontFamily = PlexArabic),
    displaySmall = Base.displaySmall.copy(fontFamily = PlexArabic),
    headlineLarge = Base.headlineLarge.copy(fontFamily = PlexArabic),
    headlineMedium = Base.headlineMedium.copy(fontFamily = PlexArabic),
    headlineSmall = Base.headlineSmall.copy(fontFamily = PlexArabic),
    titleLarge = Base.titleLarge.copy(fontFamily = PlexArabic),
    titleMedium = Base.titleMedium.copy(fontFamily = PlexArabic),
    titleSmall = Base.titleSmall.copy(fontFamily = PlexArabic),
    bodyLarge = Base.bodyLarge.copy(fontFamily = PlexArabic),
    bodyMedium = Base.bodyMedium.copy(fontFamily = PlexArabic),
    bodySmall = Base.bodySmall.copy(fontFamily = PlexArabic),
    labelLarge = Base.labelLarge.copy(fontFamily = PlexArabic),
    labelMedium = Base.labelMedium.copy(fontFamily = PlexArabic),
    labelSmall = Base.labelSmall.copy(fontFamily = PlexArabic),
)
