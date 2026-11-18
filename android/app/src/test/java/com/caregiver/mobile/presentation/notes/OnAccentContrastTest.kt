package com.caregiver.mobile.presentation.notes

import androidx.compose.ui.graphics.Color
import com.caregiver.mobile.core.theme.ColorPalettes
import com.caregiver.mobile.core.theme.caregiverScheme
import com.caregiver.mobile.core.theme.onAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Issue #984: the selected state of the mood, appetite and medication tiles
 * is a solid [com.caregiver.mobile.core.theme.CaregiverColors.Primary] fill,
 * so its content colour must be derived from that fill rather than assumed
 * to be white. Every palette is checked in both modes against the fill the
 * app actually renders.
 */
class OnAccentContrastTest {

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

    @Test
    fun coversEveryPalette() {
        assertEquals(21, palettes.size)
        assertEquals(palettes.size, palettes.toSet().size)
    }

    @Test
    fun selectedContentMeetsContrastForEveryPaletteInBothModes() {
        listOf(false, true).forEach { dark ->
            palettes.forEach { palette ->
                val fill = caregiverScheme(dark = dark, palette = palette).Primary
                val content = onAccent(fill)
                val ratio = contrastRatio(content, fill)
                assertTrue(
                    "$palette (dark=$dark): selected content must be >= 4.5:1 on the fill (was %.2f:1)"
                        .format(ratio),
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun defaultTealKeepsWhiteAndBrightAccentsGetDarkInk() {
        val tealFill = caregiverScheme(dark = false, palette = ColorPalettes.TEAL).Primary
        assertEquals(Color.White, onAccent(tealFill))

        // Documented bright accents: white on these is 1.40-1.79:1.
        listOf(
            Color(0xFFFDD835), // yellow
            Color(0xFFC0CA33), // lime
            Color(0xFFFFB300), // amber
        ).forEach { bright ->
            assertEquals(Color(0xFF1F2A2E), onAccent(bright))
            assertTrue(contrastRatio(onAccent(bright), bright) >= 4.5)
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
