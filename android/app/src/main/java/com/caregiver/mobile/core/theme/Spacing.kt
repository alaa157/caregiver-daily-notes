package com.caregiver.mobile.core.theme

import androidx.compose.ui.unit.dp

/**
 * Canonical spacing scale on an 8-dp rhythm (4-dp half step for hairlines).
 * Shared AppDimens entries alias these values, so the scale stays the
 * single source of truth; component-specific sizes that fall off the
 * scale (14-dp gaps, 48-dp+ control sizes) stay local to AppDimens.
 */
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
