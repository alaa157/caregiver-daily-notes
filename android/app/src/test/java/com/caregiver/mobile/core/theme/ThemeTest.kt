package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.presentation.common.AppDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design truth: /workspaces/android_rtl.html palette.
 * Primary teal #0E6B66, ink #1F2A2E, background #F7F6F3.
 */
class ThemeTest {

    @Test
    fun primaryMatchesDesignTeal() {
        assertEquals(Color(0xFF0E6B66), CaregiverColors.Primary)
    }

    @Test
    fun textAndBackgroundMatchDesign() {
        assertEquals(Color(0xFF1F2A2E), CaregiverColors.Ink)
        assertEquals(Color(0xFF5B6B70), CaregiverColors.Muted)
        assertEquals(Color(0xFFF7F6F3), CaregiverColors.Background)
        assertEquals(Color(0xFFFAF9F5), CaregiverColors.Surface)
        assertEquals(Color(0xFFC5CDD1), CaregiverColors.Border)
        assertEquals(Color(0xFFE3E7EA), CaregiverColors.BorderSoft)
    }

    @Test
    fun statusColorsMatchDesign() {        assertEquals(Color(0xFF8B2B25), CaregiverColors.Danger)
        assertEquals(Color(0xFFF7E3E1), CaregiverColors.DangerContainer)
        assertEquals(Color(0xFF1F5C38), CaregiverColors.Success)
        assertEquals(Color(0xFFE3F1E8), CaregiverColors.SuccessContainer)
        assertEquals(Color(0xFF24507E), CaregiverColors.Info)
        assertEquals(Color(0xFFE8F0F9), CaregiverColors.InfoContainer)
        assertEquals(Color(0xFF7A4B00), CaregiverColors.Warning)
        assertEquals(Color(0xFFFBF0D9), CaregiverColors.WarningContainer)
    }

    @Test
    fun semanticColorsAreDistinctFromTheirContainers() {
        // Tinted containers must never collapse into their signal color —
        // alerts and pills rely on the contrast between the two.
        assertTrue(CaregiverColors.Danger != CaregiverColors.DangerContainer)
        assertTrue(CaregiverColors.Success != CaregiverColors.SuccessContainer)
        assertTrue(CaregiverColors.Info != CaregiverColors.InfoContainer)
        assertTrue(CaregiverColors.Warning != CaregiverColors.WarningContainer)
    }

    @Test
    fun scaffoldVisibleRolesUsePaletteNotDefaults() {
        val scheme = appLightScheme(caregiverScheme(dark = false, palette = "teal"))
        // FAB: solid teal with white content.
        assertEquals(CaregiverColors.Primary, scheme.primaryContainer)
        assertEquals(Color.White, scheme.onPrimaryContainer)
        // Selected tab pill: neutral surface with ink content; teal accent
        // is applied at the call site.
        assertEquals(CaregiverColors.BorderSoft, scheme.secondaryContainer)
        assertEquals(CaregiverColors.Ink, scheme.onSecondaryContainer)
    }

    @Test
    fun everyScaleSpeaksPlexArabic() {
        // Bundled board typeface (res/font): one place, all screens change.
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
        scales.forEach { assertEquals(PlexArabic, it.fontFamily) }
    }

    @Test
    fun sharedDimensAliasTheSpacingScale() {
        // AppDimens entries that sit on the 8-dp rhythm must reference the
        // canonical scale, so a rhythm change propagates from one place.
        assertEquals(AppSpacing.md, AppDimens.ScreenHPadding)
        assertEquals(AppSpacing.md, AppDimens.LogoRadius)
        assertEquals(AppSpacing.md, AppDimens.AlertPad)
        assertEquals(AppSpacing.xxl, AppDimens.BackSize)
        assertEquals(AppSpacing.xxl, AppDimens.AvatarSize)
        assertEquals(AppSpacing.xxl, AppDimens.ButtonH)
        assertEquals(AppSpacing.xs, AppDimens.GapTight)
        assertEquals(AppSpacing.sm, AppDimens.GapForm)
        assertEquals(AppSpacing.lg, AppDimens.PagePad)
        assertEquals(AppSpacing.lg, AppDimens.IconMd)
    }

    @Test
    fun authRedesignColorTokensMatchDesign() {
        // Issue #864: dedicated tokens for the new Login/Register screens.
        // Muted / field border / divider intentionally match the shared
        // roles; the three specified new values are the page background and
        // the disabled-social fill and icon tint.
        assertEquals(Color(0xFFF6F8F7), CaregiverColors.AuthBackground)
        assertEquals(Color(0xFF5B6B70), CaregiverColors.AuthMuted)
        assertEquals(Color(0xFFC5CDD1), CaregiverColors.AuthFieldBorder)
        assertEquals(Color(0xFFE3E7EA), CaregiverColors.AuthDivider)
        assertEquals(Color(0xFFF0F3F2), CaregiverColors.AuthDisabledFill)
        assertEquals(Color(0xFFA3AEAD), CaregiverColors.AuthDisabledIcon)
        // The primary teal is reused, never redefined.
        assertEquals(Color(0xFF0E6B66), CaregiverColors.Primary)
    }

    @Test
    fun authRedesignColorTokensFollowDarkMode() {
        val dark = caregiverScheme(dark = true, palette = "teal")
        assertEquals(Color(0xFF121616), dark.AuthBackground)
        assertEquals(Color(0xFFB0BEC5), dark.AuthMuted)
        assertEquals(Color(0xFF3A4548), dark.AuthFieldBorder)
        assertEquals(Color(0xFF2A3336), dark.AuthDivider)
        assertEquals(Color(0xFF232C2A), dark.AuthDisabledFill)
        assertEquals(Color(0xFF6E7C78), dark.AuthDisabledIcon)
    }

    @Test
    fun authRedesignRadiusTokensMatchDesign() {
        // 16 dp for fields and the primary button, 14 dp for social buttons.
        assertEquals(16.dp, AppDimens.AuthFieldRadius)
        assertEquals(16.dp, AppDimens.AuthButtonRadius)
        assertEquals(14.dp, AppDimens.AuthSocialButtonRadius)
    }
}
