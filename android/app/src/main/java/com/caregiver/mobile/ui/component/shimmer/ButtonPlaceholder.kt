/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/shimmer/ButtonPlaceholder.kt (GPL-3.0). A pill the size of a
 * Material button, for screens that shimmer while an action loads.
 * Personalized for this app: the fill is the theme's Skeleton token instead
 * of Material's onSurface.
 */

package com.caregiver.mobile.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.caregiver.mobile.core.theme.CaregiverColors

@Composable
fun ButtonPlaceholder(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .height(ButtonDefaults.MinHeight)
            .clip(RoundedCornerShape(50))
            .background(CaregiverColors.Skeleton),
    )
}
