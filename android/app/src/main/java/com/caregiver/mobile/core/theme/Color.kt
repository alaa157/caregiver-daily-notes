package com.caregiver.mobile.core.theme

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware caregiver palette. Replaces the old fixed [CaregiverColors]
 * object so every screen follows dark mode and the chosen accent palette.
 */
data class CaregiverColorScheme(
    val Primary: Color,
    val PrimarySoft: Color,
    val Ink: Color,
    val Muted: Color,
    val Background: Color,
    val Surface: Color,
    val Border: Color,
    val BorderSoft: Color,
    val Danger: Color,
    val DangerContainer: Color,
    val DangerBorder: Color,
    val Success: Color,
    val SuccessContainer: Color,
    val Info: Color,
    val InfoContainer: Color,
    val InfoBorder: Color,
    val Warning: Color,
    val WarningContainer: Color,
    val PillGrayContainer: Color,
    val PillGrayContent: Color,
    val PillLightBlueContainer: Color,
    val PillLightBlueContent: Color,
    val AvatarContainer: Color,
    val AvatarContent: Color,
    val LockBar: Color,
    val Skeleton: Color,
)

object CaregiverColors {
    private val _current = mutableStateOf(caregiverScheme(dark = false, palette = "teal"))

    val current: CaregiverColorScheme
        get() = _current.value

    // Observable backing for Compose.
    private var _state by mutableStateOf(caregiverScheme(dark = false, palette = "teal"))
    val state: androidx.compose.runtime.State<CaregiverColorScheme>
        get() = _current

    // Delegated properties (read current scheme).
    val Primary get() = _current.value.Primary
    val PrimarySoft get() = _current.value.PrimarySoft
    val Ink get() = _current.value.Ink
    val Muted get() = _current.value.Muted
    val Background get() = _current.value.Background
    val Surface get() = _current.value.Surface
    val Border get() = _current.value.Border
    val BorderSoft get() = _current.value.BorderSoft
    val Danger get() = _current.value.Danger
    val DangerContainer get() = _current.value.DangerContainer
    val DangerBorder get() = _current.value.DangerBorder
    val Success get() = _current.value.Success
    val SuccessContainer get() = _current.value.SuccessContainer
    val Info get() = _current.value.Info
    val InfoContainer get() = _current.value.InfoContainer
    val InfoBorder get() = _current.value.InfoBorder
    val Warning get() = _current.value.Warning
    val WarningContainer get() = _current.value.WarningContainer
    val PillGrayContainer get() = _current.value.PillGrayContainer
    val PillGrayContent get() = _current.value.PillGrayContent
    val PillLightBlueContainer get() = _current.value.PillLightBlueContainer
    val PillLightBlueContent get() = _current.value.PillLightBlueContent
    val AvatarContainer get() = _current.value.AvatarContainer
    val AvatarContent get() = _current.value.AvatarContent
    val LockBar get() = _current.value.LockBar
    val Skeleton get() = _current.value.Skeleton

    fun update(darkTheme: Boolean, palette: String) {
        _current.value = caregiverScheme(darkTheme, palette)
    }
}

fun caregiverScheme(dark: Boolean, palette: String): CaregiverColorScheme {
    val primary = when (palette.lowercase()) {
        "indigo" -> Color(0xFF3F51B5)
        "rose" -> Color(0xFFC2185B)
        "metrolist" -> Color(0xFF6750A4)
        else -> Color(0xFF0E6B66)
    }
    return if (dark) {
        CaregiverColorScheme(
            Primary = primary,
            PrimarySoft = Color(0xFF1A3A38),
            Ink = Color(0xFFE3E7EA),
            Muted = Color(0xFFB0BEC5),
            Background = Color(0xFF121616),
            Surface = Color(0xFF1A1F1F),
            Border = Color(0xFF3A4548),
            BorderSoft = Color(0xFF2A3336),
            Danger = Color(0xFFEF9A9A),
            DangerContainer = Color(0xFF5C1A1A),
            DangerBorder = Color(0xFF8B2B25),
            Success = Color(0xFF81C784),
            SuccessContainer = Color(0xFF1B3A24),
            Info = Color(0xFF90CAF9),
            InfoContainer = Color(0xFF1A2F4A),
            InfoBorder = Color(0xFF24507E),
            Warning = Color(0xFFFFB74D),
            WarningContainer = Color(0xFF3A2A0A),
            PillGrayContainer = Color(0xFF2A3336),
            PillGrayContent = Color(0xFFB0BEC5),
            PillLightBlueContainer = Color(0xFF1A2F4A),
            PillLightBlueContent = Color(0xFF90CAF9),
            AvatarContainer = Color(0xFF1A2F4A),
            AvatarContent = Color(0xFF90CAF9),
            LockBar = Color(0xFF1A1F1F),
            Skeleton = Color(0xFF2A3336),
        )
    } else {
        CaregiverColorScheme(
            Primary = primary,
            PrimarySoft = Color(0xFFDCE5D6),
            Ink = Color(0xFF1F2A2E),
            Muted = Color(0xFF5B6B70),
            Background = Color(0xFFF7F6F3),
            Surface = Color(0xFFFAF9F5),
            Border = Color(0xFFC5CDD1),
            BorderSoft = Color(0xFFE3E7EA),
            Danger = Color(0xFF8B2B25),
            DangerContainer = Color(0xFFF7E3E1),
            DangerBorder = Color(0xFFEBC3BF),
            Success = Color(0xFF1F5C38),
            SuccessContainer = Color(0xFFE3F1E8),
            Info = Color(0xFF24507E),
            InfoContainer = Color(0xFFE8F0F9),
            InfoBorder = Color(0xFFC9DAEC),
            Warning = Color(0xFF7A4B00),
            WarningContainer = Color(0xFFFBF0D9),
            PillGrayContainer = Color(0xFFE8EDF3),
            PillGrayContent = Color(0xFF34495E),
            PillLightBlueContainer = Color(0xFFE4EEF8),
            PillLightBlueContent = Color(0xFF1F4A86),
            AvatarContainer = Color(0xFFDCE9F5),
            AvatarContent = Color(0xFF24507E),
            LockBar = Color(0xFFEEF1F4),
            Skeleton = Color(0xFFE3E7EA),
        )
    }
}
