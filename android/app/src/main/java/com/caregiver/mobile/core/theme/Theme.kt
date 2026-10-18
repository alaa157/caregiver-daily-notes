package com.caregiver.mobile.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Explicit roles for everything the scaffold visibly uses. Container colors
 * come from the design palette — no Material3 purple defaults survive:
 * the FAB is solid teal (primaryContainer) and the selected tab pill is a
 * neutral surface (secondaryContainer) with teal icon/label at the call site.
 */
internal val AppLightScheme = lightColorScheme(
    primary = CaregiverColors.Primary,
    onPrimary = Color.White,
    primaryContainer = CaregiverColors.Primary,
    onPrimaryContainer = Color.White,
    secondaryContainer = CaregiverColors.BorderSoft,
    onSecondaryContainer = CaregiverColors.Ink,
    background = CaregiverColors.Background,
    onBackground = CaregiverColors.Ink,
    surface = CaregiverColors.Surface,
    onSurface = CaregiverColors.Ink,
    surfaceVariant = CaregiverColors.BorderSoft,
    onSurfaceVariant = CaregiverColors.Muted,
    outline = CaregiverColors.Border,
    error = CaregiverColors.Danger,
    onError = Color.White,
    errorContainer = CaregiverColors.DangerContainer,
    onErrorContainer = CaregiverColors.Danger,
)

/**
 * Board shapes: 12px cards/buttons/inputs, 14px large surfaces, 28px sheets.
 * Applied app-wide — every themed component follows without call-site edits.
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * App theme. The design is light-only, so dynamic color is deliberately off:
 * fidelity to the boards beats wallpaper tinting on every device.
 */
internal val AppDarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * Accent palettes (2-3 options). Stored as strings in [SettingsStore].
 */
object ColorPalettes {
    const val METROLIST = "metrolist"
    const val TEAL = "teal"
    const val INDIGO = "indigo"
    const val ROSE = "rose"
}

private fun palettePrimary(palette: String?): androidx.compose.ui.graphics.Color = when (palette?.lowercase()) {
    ColorPalettes.METROLIST -> Color(0xFF6750A4)
    ColorPalettes.INDIGO -> Color(0xFF3F51B5)
    ColorPalettes.ROSE -> Color(0xFFC2185B)
    else -> CaregiverColors.Primary
}

internal val MetrolistLightScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
)

internal val MetrolistDarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFF2B8B5),
)

@Composable
fun CaregiverTheme(darkTheme: Boolean = false, palette: Any? = null, pureBlack: Boolean = false, content: @Composable () -> Unit) {
    val p = palette?.toString()?.lowercase() ?: "teal"
    androidx.compose.runtime.SideEffect {
        com.caregiver.mobile.core.theme.CaregiverColors.update(darkTheme, p)
    }
    val base = when (p) {
        ColorPalettes.METROLIST -> if (darkTheme) MetrolistDarkScheme else MetrolistLightScheme
        else -> if (darkTheme) AppDarkScheme else AppLightScheme
    }
    val primary = palettePrimary(p)
    val scheme = if (p == ColorPalettes.METROLIST) {
        base
    } else if (darkTheme) {
        base.copy(primary = primary)
    } else {
        base.copy(primary = primary, primaryContainer = primary)
    }
    val finalScheme = if (darkTheme && pureBlack) scheme.copy(surface = Color.Black, background = Color.Black) else scheme
    MaterialTheme(
        colorScheme = finalScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
