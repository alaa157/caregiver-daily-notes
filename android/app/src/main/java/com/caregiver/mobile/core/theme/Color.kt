package com.caregiver.mobile.core.theme

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** Black above this luminance, white below it. */
private const val ACCENT_INK_CUTOFF = 0.179f

/**
 * Foreground for a solid accent fill: pure black on light accents, white on
 * dark ones. Derived from the accent rather than assumed to be white, so a
 * bright palette cannot leave unreadable white text on the fill. Used by
 * both `onPrimary` in the Material scheme and the selected note tiles.
 *
 * Black has luminance 0, so it clears 4.5:1 whenever the accent's luminance
 * is at least 0.175; white clears it up to 0.183. The cut-off lies in that
 * overlap, so one of the two choices always meets AA with no gap between
 * them.
 */
internal fun onAccent(accent: Color): Color =
    if (relativeLuminance(accent) > ACCENT_INK_CUTOFF) Color.Black else Color.White

/** WCAG 2.1 relative luminance of an sRGB Compose [Color]. */
private fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}

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

    // Auth redesign (issues #864-#877). Dedicated roles so the new Login and
    // Register screens can be restyled without touching the tokens other
    // screens share. AuthMuted / AuthDivider deliberately carry the same
    // values as Muted / BorderSoft so dark mode stays consistent; the
    // auth-specific names keep the redesign decoupled from a generic role
    // whose meaning may change later. AuthFieldBorder has its own value: it
    // is the field's only affordance, so it must clear WCAG 1.4.11's 3:1
    // against the Surface fill, unlike the decorative Border token.
    val AuthBackground: Color,
    val AuthMuted: Color,
    val AuthFieldBorder: Color,
    val AuthDivider: Color,
    val AuthDisabledFill: Color,
    val AuthDisabledIcon: Color,

    // Home / Recipient Detail / Note Editor redesign (#893). Dedicated names
    // so restyling those screens cannot change any token an existing screen
    // reads. SectionCard is white with a 1-dp soft border; the two status
    // chips keep their exact specified container/content pairs in both modes
    // so the contrast guarantee holds everywhere.
    val SectionCardSurface: Color,
    val SectionCardBorder: Color,
    val DoneChipContainer: Color,
    val DoneChipContent: Color,
    val NotYetChipContainer: Color,
    val NotYetChipContent: Color,
)

object CaregiverColors {
    private val _current = mutableStateOf(caregiverScheme(dark = false, palette = "teal"))

    val current: CaregiverColorScheme
        get() = _current.value

    // Observable backing for Compose.
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
    val AuthBackground get() = _current.value.AuthBackground
    val AuthMuted get() = _current.value.AuthMuted
    val AuthFieldBorder get() = _current.value.AuthFieldBorder
    val AuthDivider get() = _current.value.AuthDivider
    val AuthDisabledFill get() = _current.value.AuthDisabledFill
    val AuthDisabledIcon get() = _current.value.AuthDisabledIcon
    val SectionCardSurface get() = _current.value.SectionCardSurface
    val SectionCardBorder get() = _current.value.SectionCardBorder
    val DoneChipContainer get() = _current.value.DoneChipContainer
    val DoneChipContent get() = _current.value.DoneChipContent
    val NotYetChipContainer get() = _current.value.NotYetChipContainer
    val NotYetChipContent get() = _current.value.NotYetChipContent

    fun update(darkTheme: Boolean, palette: String, pureBlack: Boolean = false) {
        _current.value = caregiverScheme(darkTheme, palette, pureBlack)
    }
}

/**
 * The single accent table, shared by the Material scheme and the caregiver
 * design tokens (#971). Every hex is the value the Material scheme already
 * used; unknown keys keep the caregiver teal that [caregiverScheme] fell back
 * to. Do not add a second table here — both consumers must resolve a palette
 * to exactly the same colour.
 */
internal fun paletteAccent(key: String?): Color = when (key?.lowercase()) {
    ColorPalettes.DYNAMIC -> Color(0xFFED5564)
    ColorPalettes.CRIMSON -> Color(0xFFEC5464)
    ColorPalettes.ROSE -> Color(0xFFD81B60)
    ColorPalettes.PURPLE -> Color(0xFF8E24AA)
    ColorPalettes.DEEP_PURPLE -> Color(0xFF5E35B1)
    ColorPalettes.INDIGO -> Color(0xFF3949AB)
    ColorPalettes.BLUE -> Color(0xFF1E88E5)
    ColorPalettes.SKY_BLUE -> Color(0xFF039BE5)
    ColorPalettes.CYAN -> Color(0xFF00ACC1)
    ColorPalettes.TEAL -> Color(0xFF00897B)
    ColorPalettes.GREEN -> Color(0xFF43A047)
    ColorPalettes.LIGHT_GREEN -> Color(0xFF7CB342)
    ColorPalettes.LIME -> Color(0xFFC0CA33)
    ColorPalettes.YELLOW -> Color(0xFFFDD835)
    ColorPalettes.AMBER -> Color(0xFFFFB300)
    ColorPalettes.ORANGE -> Color(0xFFFB8C00)
    ColorPalettes.DEEP_ORANGE -> Color(0xFFF4511E)
    ColorPalettes.BROWN -> Color(0xFF6D4C41)
    ColorPalettes.GREY -> Color(0xFF757575)
    ColorPalettes.BLUE_GREY -> Color(0xFF546E7A)
    ColorPalettes.METROLIST -> Color(0xFF6750A4)
    else -> Color(0xFF0E6B66)
}

fun caregiverScheme(dark: Boolean, palette: String, pureBlack: Boolean = false): CaregiverColorScheme {
    val primary = paletteAccent(palette)
    return if (dark) {
        // Pure black swaps every token-derived surface to true black so cards
        // stop rendering as grey blocks on a black page. Borders deliberately
        // keep their specified values so the raised-card edge stays visible.
        val background = if (pureBlack) Color.Black else Color(0xFF121616)
        val surface = if (pureBlack) Color.Black else Color(0xFF1A1F1F)
        CaregiverColorScheme(
            Primary = primary,
            PrimarySoft = Color(0xFF1A3A38),
            Ink = Color(0xFFE3E7EA),
            Muted = Color(0xFFB0BEC5),
            Background = background,
            Surface = surface,
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
            LockBar = surface,
            Skeleton = Color(0xFF2A3336),
            AuthBackground = Color(0xFF121616),
            AuthMuted = Color(0xFFB0BEC5),
            AuthFieldBorder = Color(0xFF7C8A8E),
            AuthDivider = Color(0xFF2A3336),
            AuthDisabledFill = Color(0xFF232C2A),
            AuthDisabledIcon = Color(0xFF6E7C78),
            SectionCardSurface = surface,
            SectionCardBorder = Color(0xFF2A3336),
            DoneChipContainer = Color(0xFFDDF1EE),
            DoneChipContent = Color(0xFF0A524E),
            NotYetChipContainer = Color(0xFFFFF0D6),
            NotYetChipContent = Color(0xFF7A4A00),
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
            AuthBackground = Color(0xFFF6F8F7),
            AuthMuted = Color(0xFF5B6B70),
            AuthFieldBorder = Color(0xFF869398),
            AuthDivider = Color(0xFFE3E7EA),
            AuthDisabledFill = Color(0xFFF0F3F2),
            AuthDisabledIcon = Color(0xFFA3AEAD),
            SectionCardSurface = Color(0xFFFFFFFF),
            SectionCardBorder = Color(0xFFE3E7EA),
            DoneChipContainer = Color(0xFFDDF1EE),
            DoneChipContent = Color(0xFF0A524E),
            NotYetChipContainer = Color(0xFFFFF0D6),
            NotYetChipContent = Color(0xFF7A4A00),
        )
    }
}
