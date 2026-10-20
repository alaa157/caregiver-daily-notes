package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding 11: the stored theme mode drives the app-wide dark flag; the
 * system case must follow the OS, the explicit cases must not.
 */
class ThemeModeTest {

    @Test
    fun explicitModesIgnoreTheSystemFlag() {
        assertFalse(resolveDarkTheme(THEME_MODE_LIGHT, systemInDark = true))
        assertFalse(resolveDarkTheme(THEME_MODE_LIGHT, systemInDark = false))
        assertTrue(resolveDarkTheme(THEME_MODE_DARK, systemInDark = true))
        assertTrue(resolveDarkTheme(THEME_MODE_DARK, systemInDark = false))
    }

    @Test
    fun systemModeTracksTheSystemFlag() {
        assertTrue(resolveDarkTheme(THEME_MODE_SYSTEM, systemInDark = true))
        assertFalse(resolveDarkTheme(THEME_MODE_SYSTEM, systemInDark = false))
    }

    @Test
    fun unknownModeValuesFollowTheSystem() {
        // A value written by a future version must not crash or guess; the
        // system setting is the safe reading.
        assertTrue(resolveDarkTheme("", systemInDark = true))
        assertFalse(resolveDarkTheme("sepia", systemInDark = false))
    }

    @Test
    fun darkSchemeBuildsEveryRoleFromTheDarkPalette() {
        // The pre-fix dark value was stock Material purple and never saw the
        // caregiver tokens; these pin the raw `colorScheme` consumers —
        // drawer, avatar pop-up, FAB, top bar — to the dark palette.
        val cs = caregiverScheme(dark = true, palette = "teal")
        val scheme = appDarkScheme(cs)
        assertEquals(cs.Background, scheme.background)
        assertEquals(cs.Ink, scheme.onBackground)
        assertEquals(cs.Surface, scheme.surface)
        assertEquals(cs.Muted, scheme.onSurfaceVariant)
        assertEquals(cs.Primary, scheme.primaryContainer)
        assertEquals(cs.BorderSoft, scheme.secondaryContainer)
        assertEquals(cs.DangerContainer, scheme.errorContainer)
    }

    @Test
    fun lightSchemePinsSurfaceTokensFromTheLightPalette() {
        val cs = caregiverScheme(dark = false, palette = "teal")
        val scheme = appLightScheme(cs)
        assertEquals(cs.Background, scheme.background)
        assertEquals(cs.Ink, scheme.onBackground)
        assertEquals(cs.Surface, scheme.surface)
        assertEquals(cs.Primary, scheme.primaryContainer)
    }

    @Test
    fun darkModePutsDarkBackgroundUnderLightInk() {
        val dark = caregiverScheme(dark = true, palette = "teal")
        assertTrue(dark.Background.luminance() < dark.Ink.luminance())
        val light = caregiverScheme(dark = false, palette = "teal")
        assertTrue(light.Background.luminance() > light.Ink.luminance())
    }
}
