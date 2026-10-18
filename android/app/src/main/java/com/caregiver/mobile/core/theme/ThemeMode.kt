package com.caregiver.mobile.core.theme

/**
 * The stored `themeMode` values (see SettingsStore) and their mapping to a
 * dark flag. Kept separate from the theme composables so the mapping stays
 * plain Kotlin: the settings chips, MainActivity and unit tests share one
 * definition instead of each re-typing the string literals.
 */
const val THEME_MODE_LIGHT = "light"
const val THEME_MODE_DARK = "dark"
const val THEME_MODE_SYSTEM = "system"

/**
 * Explicit modes win; anything else — "system" or a value written by a
 * future version — follows the OS setting rather than guessing.
 */
fun resolveDarkTheme(themeMode: String, systemInDark: Boolean): Boolean = when (themeMode) {
    THEME_MODE_DARK -> true
    THEME_MODE_LIGHT -> false
    else -> systemInDark
}
