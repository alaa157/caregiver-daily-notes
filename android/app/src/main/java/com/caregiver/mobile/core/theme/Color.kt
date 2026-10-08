package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Exact palette extracted from the Arabic design boards
 * (/workspaces/android_rtl.html). Do not improvise new brand colors;
 * later tasks reuse these tokens for every screen.
 */
object CaregiverColors {
    val Primary = Color(0xFF0E6B66)

    val Ink = Color(0xFF1F2A2E)
    val Muted = Color(0xFF5B6B70)
    val Background = Color(0xFFF7F6F3)
    val Surface = Color(0xFFFAF9F5)
    val Border = Color(0xFFC5CDD1)
    val BorderSoft = Color(0xFFE3E7EA)

    val Danger = Color(0xFF8B2B25)
    val DangerContainer = Color(0xFFF7E3E1)
    val DangerBorder = Color(0xFFEBC3BF)

    val Success = Color(0xFF1F5C38)
    val SuccessContainer = Color(0xFFE3F1E8)

    val Info = Color(0xFF24507E)
    val InfoContainer = Color(0xFFE8F0F9)
    val InfoBorder = Color(0xFFC9DAEC)

    val Warning = Color(0xFF7A4B00)
    val WarningContainer = Color(0xFFFBF0D9)
}
