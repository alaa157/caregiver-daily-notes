package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The launch frame is painted before Compose collects the theme flows, so its
 * colour has to be a pure resolution of the mirrored `ThemePrefs` values.
 * These pin all four (dark, pure black) combinations and the exact fallback
 * hexes that values/themes.xml and values-night/themes.xml use for fresh
 * installs.
 */
class ThemePrefsWindowTest {

    @Test
    fun lightModeUsesBeige() {
        assertEquals(Color(0xFFF7F6F3), launchWindowColor(dark = false, pureBlack = false))
    }

    @Test
    fun lightModeIgnoresPureBlack() {
        assertEquals(Color(0xFFF7F6F3), launchWindowColor(dark = false, pureBlack = true))
    }

    @Test
    fun darkModeUsesDarkBackground() {
        assertEquals(Color(0xFF121616), launchWindowColor(dark = true, pureBlack = false))
    }

    @Test
    fun darkModeWithPureBlackUsesBlack() {
        assertEquals(Color.Black, launchWindowColor(dark = true, pureBlack = true))
    }
}
