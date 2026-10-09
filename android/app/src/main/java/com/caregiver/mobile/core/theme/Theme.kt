package com.caregiver.mobile.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Explicit roles from design/tokens.json. Primary buttons use primary fill
 * with surface label; FAB is solid primary with surface content; selected
 * tab pill is primarySoft with primary content. No Material3 defaults,
 * no white, no gradients, no neon.
 */
internal val AppLightScheme = lightColorScheme(
    primary = CaregiverColors.Primary,
    onPrimary = CaregiverColors.Surface,
    primaryContainer = CaregiverColors.Primary,
    onPrimaryContainer = CaregiverColors.Surface,
    secondaryContainer = CaregiverColors.PrimarySoft,
    onSecondaryContainer = CaregiverColors.TextPrimary,
    background = CaregiverColors.Background,
    onBackground = CaregiverColors.TextPrimary,
    surface = CaregiverColors.Surface,
    onSurface = CaregiverColors.TextPrimary,
    surfaceVariant = CaregiverColors.PrimarySoft,
    onSurfaceVariant = CaregiverColors.TextSecondary,
    outline = CaregiverColors.Border,
    error = CaregiverColors.Danger,
    onError = CaregiverColors.Surface,
    errorContainer = CaregiverColors.Danger,
    onErrorContainer = CaregiverColors.Surface,
)

/**
 * Radii from tokens.json: sm 8, md 14, lg 20, pill 999.
 * Buttons/inputs/cards use md (14); small elements sm (8);
 * large sheets lg (20). Applied app-wide.
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadius.sm),
    small = RoundedCornerShape(AppRadius.sm),
    medium = RoundedCornerShape(AppRadius.md),
    large = RoundedCornerShape(AppRadius.lg),
    extraLarge = RoundedCornerShape(AppRadius.lg),
)

/**
 * App theme. The design is light-only, so dynamic color is deliberately off:
 * fidelity to the boards beats wallpaper tinting on every device.
 */
@Composable
fun CaregiverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppLightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
