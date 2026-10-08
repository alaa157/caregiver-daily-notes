package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Design truth: /workspaces/android_rtl.html palette.
 * Primary teal #0E6B66, ink #1F2A2E, background #F7F6F3.
 */
class ThemeTest {

    @Test
    fun primaryMatchesDesignTeal() {
        assertEquals(Color(0xFF0E6B66), CaregiverColors.Primary)
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
    fun statusColorsMatchDesign() {
        assertEquals(Color(0xFF8B2B25), CaregiverColors.Danger)
        assertEquals(Color(0xFFF7E3E1), CaregiverColors.DangerContainer)
        assertEquals(Color(0xFF1F5C38), CaregiverColors.Success)
        assertEquals(Color(0xFFE3F1E8), CaregiverColors.SuccessContainer)
        assertEquals(Color(0xFF24507E), CaregiverColors.Info)
        assertEquals(Color(0xFFE8F0F9), CaregiverColors.InfoContainer)
        assertEquals(Color(0xFF7A4B00), CaregiverColors.Warning)
        assertEquals(Color(0xFFFBF0D9), CaregiverColors.WarningContainer)
    }
}
