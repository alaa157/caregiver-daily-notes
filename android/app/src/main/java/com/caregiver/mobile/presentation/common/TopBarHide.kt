package com.caregiver.mobile.presentation.common

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Hide-on-scroll state for the shared [AppCenterTopBar] (56dp header rule).
 *
 * Replaces material3's stock `enterAlwaysScrollBehavior`: in 1.3.x its
 * SingleRowTopAppBar pins the collapse limit to -TopAppBarExpandedHeight (a
 * hardcoded 64dp) while the internal TopAppBarLayout lays out at
 * `height + heightOffset`. A 56dp bar therefore goes through a negative
 * layout height the first time it fully hides — IllegalStateException:
 * "Size(.., -16) is out of range" — and the process dies on scroll
 * (reproduced from demo-v0.8.0 by swiping down on Home). This state slides
 * the whole bar (background included) with our own scroll offset instead.
 */
@Stable
class TopBarHideState {

    /** Vertical translation in px: 0 = shown, -[barHeightPx] = fully hidden. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** Measured height of the whole bar node (status-bar inset included). */
    private var barHeightPx by mutableFloatStateOf(0f)

    /** Reported by the bar's `onSizeChanged`; also covers inset changes. */
    fun updateBarHeight(px: Float) {
        barHeightPx = px
    }

    /** Applies [delta] px of vertical scroll; returns the part we consumed. */
    private fun applyScroll(delta: Float): Float {
        if (barHeightPx <= 0f || delta == 0f) return 0f
        val previous = offsetPx
        val next = (previous + delta).coerceIn(-barHeightPx, 0f)
        if (next == previous) return 0f
        offsetPx = next
        return next - previous
    }

    /** Drag or fling ended: finish the slide (release direction wins if it flings). */
    private suspend fun settle(velocityY: Float) {
        if (barHeightPx <= 0f) return
        val target = when {
            velocityY > 0f -> 0f
            velocityY < 0f -> -barHeightPx
            offsetPx < -barHeightPx / 2f -> -barHeightPx
            else -> 0f
        }
        if (target == offsetPx) return
        animate(initialValue = offsetPx, targetValue = target) { value, _ ->
            offsetPx = value
        }
    }

    /** What the Scaffold attaches with `Modifier.nestedScroll`. */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Both directions: scrolling down collapses toward hidden, scrolling
            // up returns the bar first; the list still gets the rest of the gesture.
            return Offset(0f, applyScroll(available.y))
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(available.y)
            return Velocity.Zero
        }
    }
}

/** Remembers a [TopBarHideState] for the tab shell. */
@Composable
fun rememberTopBarHideState(): TopBarHideState = remember { TopBarHideState() }
