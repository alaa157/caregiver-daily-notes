package com.caregiver.mobile

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.navigation.ScrollToTop
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppCenterTopBar
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.history.HistoryScreen
import com.caregiver.mobile.presentation.home.HomeScreen
import com.caregiver.mobile.presentation.home.HomeViewModel
import com.caregiver.mobile.presentation.notes.AddendumScreen
import com.caregiver.mobile.presentation.notes.NoteDetailScreen
import com.caregiver.mobile.presentation.notes.NoteEditorScreen
import com.caregiver.mobile.presentation.notes.NoteSavedScreen
import com.caregiver.mobile.presentation.plans.PlanEditScreen
import com.caregiver.mobile.presentation.plans.PlanProposalScreen
import com.caregiver.mobile.presentation.plans.PlanVersionsScreen
import com.caregiver.mobile.presentation.plans.PlansScreen
import com.caregiver.mobile.presentation.recipients.AddRecipientScreen
import com.caregiver.mobile.presentation.recipients.RecipientDetailScreen
import com.caregiver.mobile.presentation.recipients.RecipientsScreen
import com.caregiver.mobile.presentation.settings.SettingsScreen
import com.caregiver.mobile.presentation.summary.SummaryPeriodScreen
import com.caregiver.mobile.presentation.summary.SummaryResultScreen
import java.time.LocalDate

/** Destination cross-fade duration; shorter than Nav defaults so tab taps feel instant. */
private const val TabTransitionMs = 180

/**
 * Tab shell: 4 tabs plus the add-note action. All destinations are live;
 * detail screens navigate with typed [AppRoutes] builders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(graph: AppGraph) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // X-style shared top bar, tab routes only; it hides on scroll-down and
    // returns on scroll-up (nested scroll is connected to the Scaffold below).
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Avatar initial: the same email→name derivation the Home greeting uses,
    // so the bar and the greeting agree; "?" while the session state loads.
    val email by graph.settings.email.collectAsState(initial = null)
    val avatarLetter = HomeViewModel.displayName(email)?.take(1)?.uppercase() ?: "?"

    Scaffold(
        containerColor = CaregiverColors.Background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // The four tab routes share the center-aligned bar; detail routes
            // keep drawing their own AppTopBar with a back button, so nothing
            // is rendered here for them.
            if (MainTab.entries.any { it.route == currentRoute }) {
                AppCenterTopBar(
                    title = stringResource(R.string.app_name),
                    avatarLetter = avatarLetter,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        // Placeholder sync/offline status; Task 8d wires the
                        // real state. Decorative (non-interactive) until then.
                        Icon(
                            Icons.Outlined.CloudQueue,
                            contentDescription = null,
                            tint = CaregiverColors.Muted,
                            modifier = Modifier.size(AppDimens.IconMd),
                        )
                    },
                )
            }
        },
        bottomBar = {
            Surface(
                color = CaregiverColors.Surface,
                border = BorderStroke(1.dp, CaregiverColors.BorderSoft),
            ) {
                // Compact strip: the tabs are a fixed [AppDimens.BottomNavH]
                // tall, and the system navigation-bar inset is reserved
                // *below* them (not squeezed into them and not added to the
                // 80dp M3 default), so the bar stays short without clipping.
                NavigationBar(
                    containerColor = CaregiverColors.Surface,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(AppDimens.BottomNavH),
                ) {
                MainTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                // X pattern: re-tapping the ACTIVE tab must not
                                // navigate. It asks that tab's list to scroll
                                // back to top (screens start collecting
                                // ScrollToTop.events in a later task).
                                ScrollToTop.emit(tab.route)
                            } else {
                                navController.navigate(tab.route) {
                                    // Save/restore each tab's state: switching
                                    // tabs keeps scroll position, and the
                                    // re-tap path above handles "back to top".
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        // Icon-only bar (X style): no labels anywhere, so each
                        // icon carries its tab name for TalkBack.
                        alwaysShowLabel = false,
                        icon = {
                            Icon(
                                imageVector = if (selected) {
                                    when (tab) {
                                        MainTab.Home -> Icons.Filled.Home
                                        MainTab.People -> Icons.Filled.People
                                        MainTab.Notes -> Icons.Filled.NoteAdd
                                        MainTab.History -> Icons.Filled.History
                                    }
                                } else {
                                    when (tab) {
                                        MainTab.Home -> Icons.Outlined.Home
                                        MainTab.People -> Icons.Outlined.People
                                        MainTab.Notes -> Icons.Outlined.NoteAdd
                                        MainTab.History -> Icons.Outlined.History
                                    }
                                },
                                contentDescription = stringResource(tab.titleRes),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        // No pill indicator and no label: state is carried by
                        // colour (teal vs muted) and the filled/outlined icon
                        // swap. Test tags keep the UI tests offline-safe.
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CaregiverColors.Primary,
                            unselectedIconColor = CaregiverColors.Muted,
                            indicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.testTag("tab_${tab.route}"),
                    )
                }
                }
            }
        },
        floatingActionButton = {
            // "Add note" starts the existing note-creation flow, whose
            // first step is recipient selection: the editor route requires
            // a recipient id, and picking happens on the People tab
            // (list → detail → Add note → editor). Routing here preserves
            // that product flow instead of inventing a recipient-less one.
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(MainTab.People.route) },
                icon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                text = {
                    Text(
                        stringResource(R.string.fab_add_note),
                        fontWeight = FontWeight.Bold,
                    )
                },
                containerColor = CaregiverColors.Primary,
                contentColor = CaregiverColors.Surface,
                shape = RoundedCornerShape(AppDimens.FabRadius),
                modifier = Modifier.height(AppDimens.ButtonLargeH),
            )
        },
    ) { innerPadding ->
        // Fast cross-fade between destinations: the Navigation-Compose
        // default is a 700ms tween, which reads as sluggish on tab switches.
        NavHost(
            navController = navController,
            startDestination = MainTab.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(TabTransitionMs)) },
            exitTransition = { fadeOut(tween(TabTransitionMs)) },
            popEnterTransition = { fadeIn(tween(TabTransitionMs)) },
            popExitTransition = { fadeOut(tween(TabTransitionMs)) },
        ) {
            composable(MainTab.Home.route) { HomeScreen(graph, navController) }
            composable(MainTab.People.route) { RecipientsScreen(graph, navController) }
            composable(MainTab.Notes.route) {
                val today = LocalDate.now()
                HistoryScreen(
                    graph = graph,
                    navController = navController,
                    initialFrom = today,
                    initialTo = today,
                    showFilters = false,
                    title = stringResource(R.string.notes_today),
                )
            }
            composable(MainTab.History.route) { HistoryScreen(graph, navController) }
            composable(
                AppDestinations.RecipientDetail.base,
                arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
            ) { entry ->
                RecipientDetailScreen(AppRoutes.arg(entry, "recipientId"), graph, navController)
            }
            composable(AppDestinations.AddRecipient.base) {
                AddRecipientScreen(graph, navController)
            }
            composable(
                AppDestinations.NoteEditor.base,
                arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
            ) { entry ->
                NoteEditorScreen(AppRoutes.arg(entry, "recipientId"), graph, navController)
            }
            composable(
                AppDestinations.NoteSaved.base,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) { entry ->
                NoteSavedScreen(AppRoutes.arg(entry, "noteId"), graph, navController)
            }
            composable(
                AppDestinations.NoteDetail.base,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) { entry ->
                NoteDetailScreen(AppRoutes.arg(entry, "noteId"), graph, navController)
            }
            composable(
                AppDestinations.Addendum.base,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) { entry ->
                AddendumScreen(AppRoutes.arg(entry, "noteId"), graph, navController)
            }
            composable(
                AppDestinations.Summary.base,
                arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
            ) { entry ->
                SummaryPeriodScreen(AppRoutes.arg(entry, "recipientId"), graph, navController)
            }
            composable(
                AppDestinations.SummaryResult.base,
                arguments = listOf(
                    navArgument("recipientId") { type = NavType.StringType },
                    navArgument("periodDays") { type = NavType.IntType },
                ),
            ) { entry ->
                SummaryResultScreen(
                    AppRoutes.arg(entry, "recipientId"),
                    AppRoutes.argInt(entry, "periodDays"),
                    graph,
                    navController,
                )
            }
            // Task 7 wired the plan and settings destinations; no placeholders remain.
            composable(AppDestinations.Plans.base) {
                PlansScreen(graph, navController)
            }
            composable(
                AppDestinations.PlanProposal.base,
                arguments = listOf(navArgument("planId") { type = NavType.StringType }),
            ) { entry ->
                PlanProposalScreen(AppRoutes.arg(entry, "planId"), graph, navController)
            }
            composable(
                AppDestinations.PlanEdit.base,
                arguments = listOf(navArgument("planId") { type = NavType.StringType }),
            ) { entry ->
                PlanEditScreen(AppRoutes.arg(entry, "planId"), graph, navController)
            }
            composable(
                AppDestinations.PlanVersions.base,
                arguments = listOf(navArgument("planId") { type = NavType.StringType }),
            ) { entry ->
                PlanVersionsScreen(AppRoutes.arg(entry, "planId"), graph, navController)
            }
            composable(AppDestinations.Settings.base) {
                SettingsScreen(graph, navController)
            }
        }
    }
}
