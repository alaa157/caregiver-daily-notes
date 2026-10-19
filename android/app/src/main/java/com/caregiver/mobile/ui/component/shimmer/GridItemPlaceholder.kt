/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/shimmer/GridItemPlaceholder.kt (GPL-3.0). A square thumbnail
 * with two text lines under it, for grid screens that shimmer while data
 * loads; fillMaxWidth=true makes the tile fluid instead of fixed-width.
 * Personalized for this app: Metrolist reads a BIG/SMALL grid-size
 * preference, but this app has no such setting — the BIG thumbnail height is
 * fixed — and the fill is the theme's Skeleton token instead of Material's
 * onSurface.
 */

package com.caregiver.mobile.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.core.theme.CaregiverColors

// Metrolist's BIG grid size (its SMALL variant belongs to a preference this
// app does not have).
private val GridThumbnailHeight = 128.dp
private val ThumbnailCornerRadius = 3.dp

@Composable
fun GridItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth: Boolean = false,
) {
    Column(
        modifier =
        if (fillMaxWidth) {
            modifier
                .padding(12.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(12.dp)
                .width(GridThumbnailHeight)
        },
    ) {
        Spacer(
            modifier =
            if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(GridThumbnailHeight)
            }.aspectRatio(1f)
                .clip(thumbnailShape)
                .background(CaregiverColors.Skeleton),
        )

        Spacer(modifier = Modifier.height(6.dp))

        TextPlaceholder()

        TextPlaceholder()
    }
}
