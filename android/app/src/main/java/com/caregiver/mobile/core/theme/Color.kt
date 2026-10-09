package com.caregiver.mobile.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth: design/tokens.json `color`.
 * Thirteen tokens only. Do not add new brand colors; soft container
 * backgrounds are derived at the call site via 10% alpha (AlertSafety /
 * AlertInfo) or primarySoft, per components.md — never new hex values.
 */
object CaregiverColors {
    val Primary = Color(0xFF4A6850)
    val PrimaryPressed = Color(0xFF3B5441)
    val PrimarySoft = Color(0xFFDCE5D6)
    val AccentGold = Color(0xFFB8923F)

    val Background = Color(0xFFF5EEDF)
    val Surface = Color(0xFFFBF8F1)
    val Border = Color(0xFFDDD5C2)

    val TextPrimary = Color(0xFF22302A)
    val TextSecondary = Color(0xFF5E6B62)

    val Success = Color(0xFF3F7A4F)
    val Warning = Color(0xFF9A6B1F)
    val Danger = Color(0xFFA3413A)
    val Info = Color(0xFF2F6B7A)
}
