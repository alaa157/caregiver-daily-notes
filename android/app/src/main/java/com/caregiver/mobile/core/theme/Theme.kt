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
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF00332F),
    primaryContainer = Color(0xFF0E6B66),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF2A3336),
    onSecondaryContainer = Color(0xFFDCE5E7),
    background = Color(0xFF121616),
    onBackground = Color(0xFFE3E7EA),
    surface = Color(0xFF1A1F1F),
    onSurface = Color(0xFFE3E7EA),
    surfaceVariant = Color(0xFF2A3336),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF5B6B70),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF330000),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFCDD2),
)

@Composable
fun CaregiverTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkScheme else AppLightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
