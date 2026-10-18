package com.caregiver.mobile.core.theme

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
}
