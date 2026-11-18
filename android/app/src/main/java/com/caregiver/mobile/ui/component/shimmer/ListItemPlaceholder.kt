/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/shimmer/ListItemPlaceholder.kt (GPL-3.0). A fixed-height row —
 * thumbnail plus two text lines — for list screens that shimmer while data
 * loads. Personalized for this app: Metrolist keeps the dimensions in its
 * shared Dimensions.kt; this app has no such constants, so the original
 * values live here as file-private defaults, and the fill is the theme's
 * Skeleton token instead of Material's onSurface.
 */

package com.caregiver.mobile.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.core.theme.CaregiverColors

// Metrolist's Dimensions.kt values, kept private here (no shared constants
// file in this app yet).
private val ListItemHeight = 64.dp
private val ListThumbnailSize = 48.dp
private val ThumbnailCornerRadius = 3.dp

@Composable
fun ListItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        modifier
            .height(ListItemHeight)
            .padding(horizontal = 6.dp),
    ) {
        Spacer(
            modifier =
            Modifier
                .padding(6.dp)
                .size(ListThumbnailSize)
                .clip(thumbnailShape)
                .background(CaregiverColors.Skeleton),
        )

        Column(
            modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
        ) {
            TextPlaceholder()
            TextPlaceholder()
        }
    }
}
