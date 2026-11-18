package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #999: pure black support in the token layer. In dark mode the four
 * token-derived surfaces go to true black so cards stop rendering as grey
 * blocks on a black page. Borders deliberately keep their specified values so
 * the raised-card edge stays visible. Light mode and the pureBlack=false path
 * are unchanged, and Theme.kt is deliberately not involved here.
 */
class PureBlackSchemeTest {

    @Test
    fun pureBlackDarkSwitchesAllSurfaceRolesToBlack() {
        val scheme = caregiverScheme(dark = true, palette = "teal", pureBlack = true)
        assertEquals(Color.Black, scheme.Background)
        assertEquals(Color.Black, scheme.Surface)
        assertEquals(Color.Black, scheme.SectionCardSurface)
        assertEquals(Color.Black, scheme.LockBar)
    }

    @Test
    fun pureBlackDarkKeepsBorderTokensUnchanged() {
        val scheme = caregiverScheme(dark = true, palette = "teal", pureBlack = true)
        assertEquals(Color(0xFF3A4548), scheme.Border)
        assertEquals(Color(0xFF2A3336), scheme.BorderSoft)
    }

    @Test
    fun pureBlackFalseIsIdenticalToTodaysDarkOutput() {
        // The default-argument call is exactly the pre-#999 behaviour, so
        // passing explicit false must produce the very same scheme.
        val today = caregiverScheme(dark = true, palette = "teal")
        assertEquals(today, caregiverScheme(dark = true, palette = "teal", pureBlack = false))

        // Pin the specified dark values literally so a silent change to the
        // default path cannot pass the equality assertion above.
        assertEquals(Color(0xFF121616), today.Background)
        assertEquals(Color(0xFF1A1F1F), today.Surface)
        assertEquals(Color(0xFF1A1F1F), today.SectionCardSurface)
        assertEquals(Color(0xFF1A1F1F), today.LockBar)
        assertEquals(Color(0xFF3A4548), today.Border)
        assertEquals(Color(0xFF2A3336), today.BorderSoft)
    }

    @Test
    fun pureBlackIsIgnoredInLightMode() {
        val normal = caregiverScheme(dark = false, palette = "teal", pureBlack = false)
        assertEquals(normal, caregiverScheme(dark = false, palette = "teal", pureBlack = true))
    }

    @Test
    fun caregiverColorsUpdatePublishesTheBlackScheme() {
        try {
            CaregiverColors.update(darkTheme = true, palette = "teal", pureBlack = true)
            assertEquals(Color.Black, CaregiverColors.Background)
            assertEquals(Color.Black, CaregiverColors.Surface)
            assertEquals(Color.Black, CaregiverColors.SectionCardSurface)
            assertEquals(Color.Black, CaregiverColors.LockBar)
        } finally {
            // Restore the singleton so other tests that read the default
            // light scheme are unaffected by test ordering.
            CaregiverColors.update(darkTheme = false, palette = "teal")
        }
    }
}
