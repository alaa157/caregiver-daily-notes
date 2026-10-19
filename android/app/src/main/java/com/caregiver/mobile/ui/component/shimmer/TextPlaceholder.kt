/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/shimmer/TextPlaceholder.kt (GPL-3.0). A line of text waiting
 * to load: fixed height, random 25–75% width (so repeated lines look
 * organic), square by default. Personalized for this app: the fill is the
 * theme's Skeleton token instead of Material's onSurface, matching the
 * existing static SkeletonBar skeletons.
 */

package com.caregiver.mobile.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.core.theme.CaregiverColors
import kotlin.random.Random

@Composable
fun TextPlaceholder(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: CornerBasedShape = RoundedCornerShape(0.dp)
) {
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .height(height)
            .fillMaxWidth(remember { 0.25f + Random.nextFloat() * 0.5f })
            .clip(shape)
            .background(CaregiverColors.Skeleton)
    )
}
