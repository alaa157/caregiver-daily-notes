package com.caregiver.mobile.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = CaregiverColors.Primary,
    onPrimary = Color.White,
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
        colorScheme = LightScheme,
        typography = AppTypography,
        content = content,
    )
}
