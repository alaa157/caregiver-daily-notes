package com.caregiver.mobile.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
 * App theme. The design is light-only, so dynamic color is deliberately off:
 * fidelity to the boards beats wallpaper tinting on every device.
 */
@Composable
fun CaregiverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppLightScheme,
        typography = AppTypography,
        content = content,
    )
}
