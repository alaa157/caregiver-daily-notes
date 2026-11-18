package com.caregiver.mobile.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #971: the design tokens and the Material scheme must resolve a palette to
 * the same accent. Previously [caregiverScheme] knew only three palettes and
 * silently fell back to teal for the rest.
 */
@RunWith(AndroidJUnit4::class)
class PaletteConsistencyTest {

    @get:Rule
    val compose = createComposeRule()

    /** Every current palette and the exact hex it has always had. */
    private val hexByKey = mapOf(
        ColorPalettes.DYNAMIC to Color(0xFFED5564),
        ColorPalettes.CRIMSON to Color(0xFFEC5464),
        ColorPalettes.ROSE to Color(0xFFD81B60),
        ColorPalettes.PURPLE to Color(0xFF8E24AA),
        ColorPalettes.DEEP_PURPLE to Color(0xFF5E35B1),
        ColorPalettes.INDIGO to Color(0xFF3949AB),
        ColorPalettes.BLUE to Color(0xFF1E88E5),
        ColorPalettes.SKY_BLUE to Color(0xFF039BE5),
        ColorPalettes.CYAN to Color(0xFF00ACC1),
        ColorPalettes.TEAL to Color(0xFF00897B),
        ColorPalettes.GREEN to Color(0xFF43A047),
        ColorPalettes.LIGHT_GREEN to Color(0xFF7CB342),
        ColorPalettes.LIME to Color(0xFFC0CA33),
        ColorPalettes.YELLOW to Color(0xFFFDD835),
        ColorPalettes.AMBER to Color(0xFFFFB300),
        ColorPalettes.ORANGE to Color(0xFFFB8C00),
        ColorPalettes.DEEP_ORANGE to Color(0xFFF4511E),
        ColorPalettes.BROWN to Color(0xFF6D4C41),
        ColorPalettes.GREY to Color(0xFF757575),
        ColorPalettes.BLUE_GREY to Color(0xFF546E7A),
        ColorPalettes.METROLIST to Color(0xFF6750A4),
    )

    @Test
    fun tokensMatchMaterialPrimaryForEveryPaletteAndMode() {
        assertEquals(21, hexByKey.size)
        hexByKey.keys.forEach { key ->
            listOf(false, true).forEach { dark ->
                assertEquals(
                    "palette=$key dark=$dark",
                    palettePrimary(key),
                    caregiverScheme(dark = dark, palette = key).Primary,
                )
            }
        }
    }

    @Test
    fun canonicalTableKeepsEveryExistingHex() {
        hexByKey.forEach { (key, expected) ->
            assertEquals("palette=$key", expected, paletteAccent(key))
            assertEquals("palette=$key", expected, palettePrimary(key))
        }
        // Metrolist is the palette the redesign depends on.
        assertEquals(Color(0xFF6750A4), paletteAccent(ColorPalettes.METROLIST))
        // Unknown keys keep the caregiver fallback, never a surprise colour.
        assertEquals(Color(0xFF0E6B66), paletteAccent(null))
        assertEquals(Color(0xFF0E6B66), paletteAccent("not-a-palette"))
    }

    @Test
    fun composedThemeUsesTheSamePrimaryAsTheTokens() {
        var materialPrimary: Color? = null
        compose.setContent {
            CaregiverTheme(darkTheme = false, palette = ColorPalettes.TEAL) {
                materialPrimary = MaterialTheme.colorScheme.primary
            }
        }

        assertEquals(palettePrimary(ColorPalettes.TEAL), CaregiverColors.Primary)
        assertEquals(CaregiverColors.Primary, materialPrimary)
    }
}
