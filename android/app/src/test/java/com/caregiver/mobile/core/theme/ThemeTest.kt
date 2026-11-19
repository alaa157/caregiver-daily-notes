package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.presentation.common.AppDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Design truth: the #971 shared accent table. The "teal" palette resolves to
 * #00897B (unknown palettes still fall back to the original #0E6B66);
 * ink #1F2A2E, background #F7F6F3.
 */
class ThemeTest {

    @Test
    fun primaryMatchesDesignTeal() {
        // #971: the teal key resolves to the shared accent table value.
        assertEquals(Color(0xFF00897B), CaregiverColors.Primary)
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
        // FAB: solid teal with the derived accent ink (black on bright accents).
        assertEquals(CaregiverColors.Primary, scheme.primaryContainer)
        assertEquals(onAccent(CaregiverColors.Primary), scheme.onPrimaryContainer)
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
        // Muted / divider intentionally match the shared roles; the field
        // border has its own accessible value (#982), and the remaining
        // specified new values are the page background and the disabled-
        // social fill and icon tint.
        assertEquals(Color(0xFFF6F8F7), CaregiverColors.AuthBackground)
        assertEquals(Color(0xFF5B6B70), CaregiverColors.AuthMuted)
        assertEquals(Color(0xFF869398), CaregiverColors.AuthFieldBorder)
        assertEquals(Color(0xFFE3E7EA), CaregiverColors.AuthDivider)
        assertEquals(Color(0xFFF0F3F2), CaregiverColors.AuthDisabledFill)
        assertEquals(Color(0xFFA3AEAD), CaregiverColors.AuthDisabledIcon)
        // The primary teal is reused, never redefined (#971 accent table).
        assertEquals(Color(0xFF00897B), CaregiverColors.Primary)
        // #982: the border is the field's only affordance in light mode.
        val light = caregiverScheme(dark = false, palette = "teal")
        assertTrue(
            "light field border contrast must be >= 3:1 (was ${contrastRatio(light.AuthFieldBorder, light.Surface)})",
            contrastRatio(light.AuthFieldBorder, light.Surface) >= 3.0,
        )
    }

    @Test
    fun authRedesignColorTokensFollowDarkMode() {
        val dark = caregiverScheme(dark = true, palette = "teal")
        assertEquals(Color(0xFF121616), dark.AuthBackground)
        assertEquals(Color(0xFFB0BEC5), dark.AuthMuted)
        assertEquals(Color(0xFF7C8A8E), dark.AuthFieldBorder)
        assertEquals(Color(0xFF2A3336), dark.AuthDivider)
        assertEquals(Color(0xFF232C2A), dark.AuthDisabledFill)
        assertEquals(Color(0xFF6E7C78), dark.AuthDisabledIcon)
        // #982: the raised Surface fill needs the same 3:1 boundary in dark.
        assertTrue(
            "dark field border contrast must be >= 3:1 (was ${contrastRatio(dark.AuthFieldBorder, dark.Surface)})",
            contrastRatio(dark.AuthFieldBorder, dark.Surface) >= 3.0,
        )
    }

    @Test
    fun authRedesignRadiusTokensMatchDesign() {
        // 16 dp for fields and the primary button, 14 dp for social buttons.
        assertEquals(16.dp, AppDimens.AuthFieldRadius)
        assertEquals(16.dp, AppDimens.AuthButtonRadius)
        assertEquals(14.dp, AppDimens.AuthSocialButtonRadius)
    }

    @Test
    fun redesignCardAndRadiusTokensMatchDesign() {
        val light = caregiverScheme(dark = false, palette = "teal")
        val dark = caregiverScheme(dark = true, palette = "teal")

        // #893: white card with a soft border on the reused auth page
        // background; dark mode keeps a raised, subtly bordered card.
        assertEquals(Color(0xFFFFFFFF), light.SectionCardSurface)
        assertEquals(Color(0xFFE3E7EA), light.SectionCardBorder)
        assertEquals(Color(0xFF1A1F1F), dark.SectionCardSurface)
        assertEquals(Color(0xFF2A3336), dark.SectionCardBorder)

        // The 20-dp card radius is a new name; existing screens keep 14 dp.
        assertEquals(20.dp, AppDimens.SectionCardRadius)
        assertEquals(14.dp, AppDimens.CardRadius)
        // The 16-dp button/field radii are reused, never redefined.
        assertEquals(16.dp, AppDimens.AuthButtonRadius)
        assertEquals(16.dp, AppDimens.AuthFieldRadius)
    }

    @Test
    fun statusChipTokensMatchDesignInBothModes() {
        val light = caregiverScheme(dark = false, palette = "teal")
        val dark = caregiverScheme(dark = true, palette = "teal")
        assertEquals(Color(0xFFDDF1EE), light.DoneChipContainer)
        assertEquals(Color(0xFF0A524E), light.DoneChipContent)
        assertEquals(Color(0xFFFFF0D6), light.NotYetChipContainer)
        assertEquals(Color(0xFF7A4A00), light.NotYetChipContent)
        // The specified pairs are identical in dark mode so the guarantee
        // holds there too.
        assertEquals(light.DoneChipContainer, dark.DoneChipContainer)
        assertEquals(light.DoneChipContent, dark.DoneChipContent)
        assertEquals(light.NotYetChipContainer, dark.NotYetChipContainer)
        assertEquals(light.NotYetChipContent, dark.NotYetChipContent)
    }

    @Test
    fun redesignTextAndChipColorsMeetContrast() {
        val light = caregiverScheme(dark = false, palette = "teal")
        val dark = caregiverScheme(dark = true, palette = "teal")
        val pairs = listOf(
            "done chip" to (light.DoneChipContent to light.DoneChipContainer),
            "not-yet chip" to (light.NotYetChipContent to light.NotYetChipContainer),
            "ink on card" to (light.Ink to light.SectionCardSurface),
            "muted on card" to (light.Muted to light.SectionCardSurface),
            "ink on page" to (light.Ink to light.AuthBackground),
            "muted on page" to (light.Muted to light.AuthBackground),
            "accent ink on primary" to (onAccent(light.Primary) to light.Primary),
            "dark ink on card" to (dark.Ink to dark.SectionCardSurface),
            "dark muted on card" to (dark.Muted to dark.SectionCardSurface),
        )
        pairs.forEach { (label, pair) ->
            assertTrue(
                "$label contrast must be >= 4.5:1 (was ${contrastRatio(pair.first, pair.second)})",
                contrastRatio(pair.first, pair.second) >= 4.5,
            )
        }
    }

    /** WCAG 2.1 relative luminance for an sRGB Compose [Color]. */
    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val (lighter, darker) = if (luminance(a) >= luminance(b)) a to b else b to a
        return (luminance(lighter) + 0.05) / (luminance(darker) + 0.05)
    }
}
