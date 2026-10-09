package com.caregiver.mobile.core.theme

import androidx.compose.ui.unit.dp

/**
 * Single source of truth: design/tokens.json `spacing`, `radius`,
 * `component`. Units: dp on Android. Screens and components must use
 * these constants — no literal `.dp` outside theme files.
 */
object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/** Component dimensions from tokens.json `component`. */
object AppSizes {
    val touchTargetMin = 48.dp
    val buttonHeight = 48.dp
    val buttonHeightLarge = 56.dp
    val appBarHeight = 48.dp
    val bottomNavHeight = 72.dp
    val cardRadius = 14.dp
    val inputHeight = 52.dp
    val iconSize = 24.dp

    // Micro-geometry not named in tokens.json but required by components.md
    // (1px borders/dividers, avatar 40, pill 28 high, multiline min 120,
    // skeleton/progress bars). Centralized here so no `.dp` leaks into
    // screens/components; values preserved from implementation.
    val borderWidth = 1.dp
    val borderWidthStrong = 2.dp
    val avatarSize = 40.dp
    val pillHeight = 28.dp
    val multilineMinHeight = 120.dp
    val progressTrack = 6.dp
    val progressIndicator = 20.dp
    val cardElevation = 1.dp
    val trendLabelWidth = 80.dp
}

/** Corner radii from tokens.json `radius`. */
object AppRadius {
    val sm = 8.dp
    val md = 14.dp
    val lg = 20.dp
    val pill = 999.dp
}
