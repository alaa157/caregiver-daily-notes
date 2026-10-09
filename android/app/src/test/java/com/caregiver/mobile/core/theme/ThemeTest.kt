package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Design truth: design/tokens.json `color` + `typography`.
 * Primary sage #4A6850, background cream #F5EEDF, surface #FBF8F1.
 */
class ThemeTest {

    @Test
    fun primaryMatchesTokenSage() {
        assertEquals(Color(0xFF4A6850), CaregiverColors.Primary)
        assertEquals(Color(0xFF3B5441), CaregiverColors.PrimaryPressed)
        assertEquals(Color(0xFFDCE5D6), CaregiverColors.PrimarySoft)
        assertEquals(Color(0xFFB8923F), CaregiverColors.AccentGold)
    }

    @Test
    fun textAndBackgroundMatchTokens() {
        assertEquals(Color(0xFFF5EEDF), CaregiverColors.Background)
        assertEquals(Color(0xFFFBF8F1), CaregiverColors.Surface)
        assertEquals(Color(0xFFDDD5C2), CaregiverColors.Border)
        assertEquals(Color(0xFF22302A), CaregiverColors.TextPrimary)
        assertEquals(Color(0xFF5E6B62), CaregiverColors.TextSecondary)
    }

    @Test
    fun statusColorsMatchTokens() {
        assertEquals(Color(0xFF3F7A4F), CaregiverColors.Success)
        assertEquals(Color(0xFF9A6B1F), CaregiverColors.Warning)
        assertEquals(Color(0xFFA3413A), CaregiverColors.Danger)
        assertEquals(Color(0xFF2F6B7A), CaregiverColors.Info)
    }

    @Test
    fun scaffoldVisibleRolesUsePaletteNotDefaults() {
        // FAB: solid sage with surface content.
        assertEquals(CaregiverColors.Primary, AppLightScheme.primaryContainer)
        assertEquals(CaregiverColors.Surface, AppLightScheme.onPrimaryContainer)
        // Selected tab pill: soft sage with primary text (call-site accent).
        assertEquals(CaregiverColors.PrimarySoft, AppLightScheme.secondaryContainer)
        assertEquals(CaregiverColors.TextPrimary, AppLightScheme.onSecondaryContainer)
    }

    @Test
    fun everyScaleSpeaksTokenFamily() {
        // Bundled token typefaces (res/font): Inter + Noto Naskh Arabic.
        // Same-reference comparison — guards the wiring, not font equality.
        val scales = listOf(
            AppTypography.displayLarge,
            AppTypography.displayMedium,
            AppTypography.displaySmall,
            AppTypography.headlineLarge,
            AppTypography.headlineMedium,
            AppTypography.headlineSmall,
            AppTypography.titleLarge,
            AppTypography.titleMedium,
            AppTypography.titleSmall,
            AppTypography.bodyLarge,
            AppTypography.bodyMedium,
            AppTypography.bodySmall,
            AppTypography.labelLarge,
            AppTypography.labelMedium,
            AppTypography.labelSmall,
        )
        assertEquals(15, scales.size)
        scales.forEach { assertEquals(AppFontFamily, it.fontFamily) }
    }

    @Test
    fun tokenTypeScaleSizes() {
        // display 28, title 22, heading 18, body 16, label 14, caption 12.
        assertEquals(28, AppTypography.displayLarge.fontSize.value.toInt())
        assertEquals(22, AppTypography.titleLarge.fontSize.value.toInt())
        assertEquals(18, AppTypography.titleMedium.fontSize.value.toInt())
        assertEquals(16, AppTypography.bodyLarge.fontSize.value.toInt())
        assertEquals(14, AppTypography.labelLarge.fontSize.value.toInt())
        assertEquals(12, AppTypography.bodySmall.fontSize.value.toInt())
    }

    @Test
    fun tokenSpacingAndComponentSizes() {
        assertEquals(4, AppSpacing.xxs.value.toInt())
        assertEquals(8, AppSpacing.xs.value.toInt())
        assertEquals(12, AppSpacing.sm.value.toInt())
        assertEquals(16, AppSpacing.md.value.toInt())
        assertEquals(24, AppSpacing.lg.value.toInt())
        assertEquals(32, AppSpacing.xl.value.toInt())
        assertEquals(48, AppSpacing.xxl.value.toInt())
        assertEquals(48, AppSizes.buttonHeight.value.toInt())
        assertEquals(56, AppSizes.buttonHeightLarge.value.toInt())
        assertEquals(72, AppSizes.bottomNavHeight.value.toInt())
        assertEquals(52, AppSizes.inputHeight.value.toInt())
        assertEquals(24, AppSizes.iconSize.value.toInt())
        assertEquals(8, AppRadius.sm.value.toInt())
        assertEquals(14, AppRadius.md.value.toInt())
        assertEquals(20, AppRadius.lg.value.toInt())
    }
}
