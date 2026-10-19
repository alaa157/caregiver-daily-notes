/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/AppNavigation.kt (GPL-3.0): the navigation rail and bottom
 * bar with metrolist's route-template selection, `pureBlack` theming,
 * `slimNav`, and the InteractionSource press-hold (15s on Home) replaced
 * long-press mechanics.
 *
 * Caregiver adaptations, all documented at the use sites:
 * - metrolist's `Screens` data type became [AppTabItem]; icons are
 *   ImageVectors filled/outlined pairs instead of drawable resources.
 * - metrolist's "own-route child" rule (route + "route/" prefixes) is
 *   extended with a per-tab child-ownership table, because this app's
 *   detail routes (`recipient/…`, `note/…`, `summary/…`, …) do not nest
 *   under their tab's route path.
 * - Search long-click special case dropped (no search screen here).
 * - An [itemModifier] hook lets the shell attach its `tab_*` test tags
 *   without the component knowing the tag contract.
 * - The app's compact strip look (transparent selection indicator,
 *   `surface` container, zero internal insets — the shell reserves the
 *   system bar space itself) is the default; `showIndicator` restores
 *   the stock M3 indicator pill.
 *
 * Metrolist Project (C) 2026 | Licensed under GPL-3.0 | See git history for
 * contributors.
 */

package com.caregiver.mobile.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.MainTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** One nav-shell destination: tab route, label resource, icon pair. */
data class AppTabItem(
    val route: String,
    val titleRes: Int,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
)

/**
 * Per-tab child ownership: which detail-route prefixes read as "still inside
 * this tab". Values keep the [AppDestinations] templates as the source of
 * truth and are normalized to plain prefixes before matching.
 */
@Stable
private fun tabOwnership(): Map<String, List<String>> = mapOf(
    MainTab.Home.route to listOf(
        AppDestinations.Settings.base,
        AppDestinations.AiSettings.base,
        AppDestinations.ThemeColors.base,
        AppDestinations.About.base,
    ),
    MainTab.People.route to listOf(
        AppDestinations.RecipientDetail.base,
        AppDestinations.AddRecipient.base,
        AppDestinations.Summary.base,
        AppDestinations.SummaryResult.base,
        AppDestinations.Plans.base,
        AppDestinations.PlanProposal.base,
        AppDestinations.PlanEdit.base,
        AppDestinations.PlanVersions.base,
    ),
    MainTab.Notes.route to listOf(
        AppDestinations.NoteEditor.base,
        AppDestinations.NoteSaved.base,
    ),
    MainTab.History.route to listOf(
        AppDestinations.NoteDetail.base,
        AppDestinations.Addendum.base,
    ),
).mapValues { (_, routes) ->
    routes.map { template -> template.substringBefore('{').removeSuffix("/") }
}

/**
 * Metrolist's selection template, adapted: exact tab match, or any owned
 * child prefix (distinct roots stay unambiguous — `note/…` never collides
 * with `note-editor/…` or `note-saved/…`).
 */
@Stable
private fun isRouteSelected(
    currentRoute: String?,
    item: AppTabItem,
    ownership: Map<String, List<String>>,
): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == item.route) return true
    return ownership[item.route]?.any { prefix ->
        currentRoute == prefix || currentRoute.startsWith("$prefix/")
    } == true
}

@Composable
fun AppNavigationRail(
    navigationItems: List<AppTabItem>,
    currentRoute: String?,
    onItemClick: (AppTabItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
    val haptics = LocalHapticFeedback.current
    val ownership = remember { tabOwnership() }

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor,
    ) {
        navigationItems.forEach { item ->
            val isSelected = remember(currentRoute, item.route) {
                isRouteSelected(currentRoute, item, ownership)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val icon = remember(isSelected, item) {
                if (isSelected) item.activeIcon else item.inactiveIcon
            }

            val isHomeHoldItem = item.route == MainTab.Home.route && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Press-hold detection using InteractionSource (metrolist pattern).
            if (isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isHold = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isHold = false
                                delay(15_000L)
                                isHold = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHomeLongHold?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isHold) {
                                    onItemClick(item, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isHold = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isHomeHoldItem) {
                        onItemClick(item, currentIsSelected)
                    }
                    // Press-holds are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(item.titleRes),
                    )
                },
            )
        }
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<AppTabItem>,
    currentRoute: String?,
    onItemClick: (AppTabItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    showIndicator: Boolean = false,
    itemModifier: (AppTabItem) -> Modifier = { Modifier },
    onHomeLongHold: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val haptics = LocalHapticFeedback.current
    val ownership = remember { tabOwnership() }

    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        windowInsets = windowInsets,
    ) {
        navigationItems.forEach { item ->
            val isSelected = remember(currentRoute, item.route) {
                isRouteSelected(currentRoute, item, ownership)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val icon = remember(isSelected, item) {
                if (isSelected) item.activeIcon else item.inactiveIcon
            }

            val isHomeHoldItem = item.route == MainTab.Home.route && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Press-hold detection using InteractionSource (metrolist pattern).
            if (isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isHold = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isHold = false
                                delay(15_000L)
                                isHold = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHomeLongHold?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isHold) {
                                    onItemClick(item, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isHold = false
                            }
                        }
                    }
                }
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isHomeHoldItem) {
                        onItemClick(item, currentIsSelected)
                    }
                    // Press-holds are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(item.titleRes),
                    )
                },
                label = if (!slimNav) {
                    {
                        Text(
                            text = stringResource(item.titleRes),
                            // The app's compact strip label size, kept from
                            // the previous inline bar.
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else null,
                colors = if (showIndicator) {
                    NavigationBarItemDefaults.colors()
                } else {
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent,
                    )
                },
                modifier = itemModifier(item),
            )
        }
    }
}
