package com.caregiver.mobile.core.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale on an 8-dp rhythm (4-dp half step for hairlines).
 * Currently unreferenced — screens consume [AppDimens] — kept as the
 * canonical scale so future layouts converge here instead of inventing
 * local values.
 */
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
