package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Every accent palette in both modes gets a foreground chosen from the
 * accent, never a hardcoded white. The ratios below are WCAG 2.1 contrast.
 */
class OnAccentContrastTest {
    private val darkInk = Color.Black

    private val palettes = listOf(
        ColorPalettes.DYNAMIC,
        ColorPalettes.CRIMSON,
        ColorPalettes.ROSE,
        ColorPalettes.PURPLE,
        ColorPalettes.DEEP_PURPLE,
        ColorPalettes.INDIGO,
        ColorPalettes.BLUE,
        ColorPalettes.SKY_BLUE,
        ColorPalettes.CYAN,
        ColorPalettes.TEAL,
        ColorPalettes.GREEN,
        ColorPalettes.LIGHT_GREEN,
        ColorPalettes.LIME,
        ColorPalettes.YELLOW,
        ColorPalettes.AMBER,
        ColorPalettes.ORANGE,
        ColorPalettes.DEEP_ORANGE,
        ColorPalettes.BROWN,
        ColorPalettes.GREY,
        ColorPalettes.BLUE_GREY,
        ColorPalettes.METROLIST,
    )

    /** Accents that must switch from white to black. */
    private val lightAccents = listOf(
        ColorPalettes.TEAL,
        ColorPalettes.BLUE,
        ColorPalettes.SKY_BLUE,
        ColorPalettes.CYAN,
        ColorPalettes.GREEN,
        ColorPalettes.LIGHT_GREEN,
        ColorPalettes.LIME,
        ColorPalettes.YELLOW,
        ColorPalettes.AMBER,
        ColorPalettes.ORANGE,
        ColorPalettes.DEEP_ORANGE,
        ColorPalettes.CRIMSON,
        ColorPalettes.DYNAMIC,
    )

    /** Deep accents whose white foreground already reads well. */
    private val darkAccents = listOf(
        ColorPalettes.ROSE,
        ColorPalettes.PURPLE,
        ColorPalettes.DEEP_PURPLE,
        ColorPalettes.INDIGO,
        ColorPalettes.BROWN,
        ColorPalettes.GREY,
        ColorPalettes.BLUE_GREY,
        ColorPalettes.METROLIST,
    )

    @Test
    fun lightAccentsUseBlack() {
        lightAccents.forEach { key ->
            assertEquals("$key should use black", darkInk, onAccent(palettePrimary(key)))
        }
    }

    @Test
    fun deepAccentsKeepWhite() {
        darkAccents.forEach { key ->
            assertEquals("$key should keep white", Color.White, onAccent(palettePrimary(key)))
        }
    }

    @Test
    fun everyPaletteHasAReadableForegroundInBothModes() {
        for (dark in listOf(false, true)) {
            palettes.forEach { key ->
                val fills = listOf(
                    palettePrimary(key),
                    caregiverScheme(dark = dark, palette = key).Primary,
                )
                fills.forEach { fill ->
                    val foreground = onAccent(fill)
                    val ratio = contrastRatio(foreground, fill)
                    assertTrue(
                        "$key (dark=$dark): ratio $ratio for ${if (foreground == Color.Black) "black" else "white"}",
                        foreground == Color.Black || foreground == Color.White,
                    )
                    assertTrue(
                        "$key (dark=$dark) contrast must be >= 4.5:1 (was $ratio)",
                        ratio >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun appSchemesDerivePrimaryForegroundFromTheAccent() {
        for (dark in listOf(false, true)) {
            val scheme = if (dark) {
                appDarkScheme(caregiverScheme(dark = true, palette = "teal"))
            } else {
                appLightScheme(caregiverScheme(dark = false, palette = "teal"))
            }
            assertEquals(onAccent(scheme.primary), scheme.onPrimary)
            assertEquals(onAccent(scheme.primaryContainer), scheme.onPrimaryContainer)
            // Error reds are untouched.
            assertEquals(Color.White, appLightScheme(caregiverScheme(false, "teal")).onError)
        }
    }

    @Test
    fun metrolistSchemesKeepTheirForegrounds() {
        // Their primary roles already clear 4.5:1 with the existing inks.
        assertEquals(Color.White, MetrolistLightScheme.onPrimary)
        assertEquals(Color(0xFF21005D), MetrolistLightScheme.onPrimaryContainer)
        assertEquals(Color(0xFF381E72), MetrolistDarkScheme.onPrimary)
        assertEquals(Color(0xFFEADDFF), MetrolistDarkScheme.onPrimaryContainer)
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
