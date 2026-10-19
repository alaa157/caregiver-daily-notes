package com.caregiver.mobile.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A small custom swipe row (inspired by Metrolist) that reveals a trailing
 * action when swiped left. Uses manual offset animation instead of
 * SwipeToDismissBox so the gesture and haptic behaviour can be tuned.
 */
@Composable
fun SwipeActionRow(
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: @Composable () -> Unit = { Icon(Icons.Filled.Delete, contentDescription = null) },
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionWidthPx = with(density) { 72.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        // Revealed action background
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        ) {
            actionIcon()
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -actionWidthPx / 2f) {
                                    offsetX.animateTo(-actionWidthPx, tween(150))
                                    onSwipe()
                                    offsetX.animateTo(0f, tween(150))
                                } else {
                                    offsetX.animateTo(0f, tween(150))
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newValue = (offsetX.value + dragAmount).coerceIn(-actionWidthPx, 0f)
                                offsetX.snapTo(newValue)
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
