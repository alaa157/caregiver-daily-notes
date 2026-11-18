/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/HideOnScrollFAB.kt (GPL-3.0), personalized: painter
 * resources became ImageVector icons (this app's icon convention); the
 * music-recognition secondary button became a generic [secondary] slot
 * (compose anything — e.g. a SmallFloatingActionButton); the player-aware
 * window inset became the plain system navigation inset (horizontal +
 * bottom only, since this app's Scaffold reserves the rest there is not an
 * overlapping player layer).
 *
 * Metrolist Project (C) 2026 | Licensed under GPL-3.0 | See git history for
 * contributors.
 */

package com.caregiver.mobile.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.ui.utils.isScrollingUp

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: (@Composable () -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible && lazyListState.isScrollingUp(),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier =
        Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .then(modifier),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            if (secondary != null) {
                secondary()
                Spacer(modifier = Modifier.height(12.dp))
            }
            FloatingActionButton(
                onClick = onClick,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                )
            }
        }
    }
}
