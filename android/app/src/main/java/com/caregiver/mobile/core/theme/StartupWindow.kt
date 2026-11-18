package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color

/**
 * The window background painted for the pre-Compose launch frame.
 *
 * The preference flows are collected too late to decide the first frame, so
 * `MainActivity` resolves the mirrored values from `ThemePrefs` and paints
 * this colour on the window before `setContent`. Keeping the resolution a
 * pure function lets the four (dark, pure black) combinations be verified
 * without an Activity or a device.
 *
 * The two fallbacks deliberately match the `windowBackground` values in
 * `values/themes.xml` and `values-night/themes.xml`, which stay as the
 * fresh-install fallback chosen by the OS configuration.
 */
private val LightWindowColor = Color(0xFFF7F6F3)
private val DarkWindowColor = Color(0xFF121616)

fun launchWindowColor(dark: Boolean, pureBlack: Boolean): Color = when {
    dark && pureBlack -> Color.Black
    dark -> DarkWindowColor
    else -> LightWindowColor
}
