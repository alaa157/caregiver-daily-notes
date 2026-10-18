package com.caregiver.mobile

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.caregiver.mobile.core.network.networkAvailabilityFlow
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.OutboxSyncWorker
import com.caregiver.mobile.presentation.common.AppCenterTopBar
import com.caregiver.mobile.presentation.common.AppDrawer
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.common.SyncStatus
import com.caregiver.mobile.presentation.common.SyncStatusIndicator
import com.caregiver.mobile.presentation.common.SyncStatusViewModel
import com.caregiver.mobile.presentation.history.HistoryScreen
import com.caregiver.mobile.presentation.home.HomeScreen
import com.caregiver.mobile.presentation.home.HomeViewModel
import com.caregiver.mobile.presentation.notes.AddendumScreen
import com.caregiver.mobile.presentation.notes.NoteDetailScreen
import com.caregiver.mobile.presentation.notes.NoteEditorScreen
import com.caregiver.mobile.presentation.notes.NoteSavedScreen
import com.caregiver.mobile.presentation.notes.RecipientPickerSheet
import com.caregiver.mobile.presentation.plans.PlanEditScreen
import com.caregiver.mobile.presentation.plans.PlanProposalScreen
import com.caregiver.mobile.presentation.plans.PlanVersionsScreen
import com.caregiver.mobile.presentation.plans.PlansScreen
import com.caregiver.mobile.presentation.recipients.AddRecipientScreen
import com.caregiver.mobile.presentation.recipients.PeopleState
import com.caregiver.mobile.presentation.recipients.RecipientDetailScreen
import com.caregiver.mobile.presentation.recipients.RecipientsScreen
import com.caregiver.mobile.presentation.recipients.RecipientsViewModel
import com.caregiver.mobile.presentation.settings.SettingsScreen
import com.caregiver.mobile.presentation.summary.SummaryPeriodScreen
import com.caregiver.mobile.presentation.summary.SummaryResultScreen
import java.time.LocalDate
import kotlinx.coroutines.launch

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

    // Avatar drawer: opened from the top-bar avatar or a swipe from the start
    // edge; the stock drawer component mirrors both in RTL by itself.
    var showAccountMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Session identity: the same email→name derivation the Home greeting
    // uses, so the bar, drawer and greeting agree; "?" while it loads.
    val email by graph.settings.email.collectAsState(initial = null)
    val displayName = HomeViewModel.displayName(email)
    val avatarLetter = displayName?.take(1)?.uppercase() ?: "?"

    // Shared recipients source: the exact activity-scoped "people-shared"
    // VM the People tab uses — no new repository, no second data path.
    // Without an activity (compose-rule tests) it falls back to the local
    // store owner, mirroring RecipientsScreen.
    val activity = LocalContext.current as? ComponentActivity
    val recipientsVm: RecipientsViewModel = assistedViewModel("people-shared", activity) {
        RecipientsViewModel(graph.apis, graph.auth)
    }
    val peopleState by recipientsVm.state.collectAsState()
    val activeRecipients = (peopleState as? PeopleState.Content)
        ?.recipients?.filter { it.active }
        .orEmpty()

    // Task 8d: real sync status for the bar's end slot — the outbox rows
    // plus connectivity; tapping a failed state re-queues the same
    // WorkManager drain an offline save would trigger.
    val syncStatusVm: SyncStatusViewModel = assistedViewModel("sync-status") {
        SyncStatusViewModel(
            statuses = graph.noteRepository.observeOutboxStatuses(),
            connected = networkAvailabilityFlow(graph.context.applicationContext),
            onRetry = { OutboxSyncWorker.schedule(graph.context.applicationContext) },
        )
    }
    val syncStatus by syncStatusVm.status.collectAsState(initial = SyncStatus.SYNCED)

    // Shown only when the picker has something to ask: exactly one active
    // person goes straight to the editor instead.
    var showRecipientPicker by rememberSaveable { mutableStateOf(false) }

    // Single tab-navigation path: the bottom bar and the drawer's People
    // entry behave identically (each tab's state is saved and restored).
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Back closes an open account pop-up.
    BackHandler(enabled = showAccountMenu) {
        showAccountMenu = false
    }

    Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                // The four tab routes share the center-aligned bar; detail routes
                // keep drawing their own AppTopBar with a back button, so nothing
                // is rendered here for them.
                if (MainTab.isTabRoute(currentRoute)) {
                    AppCenterTopBar(
                        title = stringResource(R.string.app_name),
                        avatarLetter = avatarLetter,
                        scrollBehavior = scrollBehavior,
                        onAvatarClick = { showAccountMenu = !showAccountMenu },
                        avatarContentDescription = stringResource(R.string.drawer_open),
                        avatarModifier = Modifier.testTag("drawer_avatar"),
                        actions = {
                            // Task 8d: the end slot mirrors the outbox — synced,
                            // syncing, offline, or failed (tap to retry).
                            SyncStatusIndicator(
                                status = syncStatus,
                                onRetry = syncStatusVm::retry,
                            )
                        },
                    )
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, CaregiverColors.BorderSoft),
                ) {
                    // Compact strip: the tabs are a fixed [AppDimens.BottomNavH]
                    // tall, and the system navigation-bar inset is reserved
                    // *below* them (not squeezed into them and not added to the
                    // 80dp M3 default), so the bar stays short without clipping.
                    // Modifier order is load-bearing: the inset padding must
                    // wrap the fixed height, otherwise the padding eats into the
                    // 64dp strip and squeezes the icons (visible on 3-button
                    // navigation, where the inset is ~48dp).
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
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
                                    // Save/restore each tab's state: switching
                                    // tabs keeps scroll position, and the re-tap
                                    // path above handles "back to top".
                                    navigateToTab(tab.route)
                                }
                            },
                            // Show labels so destinations are discoverable.
                            alwaysShowLabel = true,
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
                                    modifier = Modifier.size(AppDimens.BottomNavIcon),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.titleRes),
                                    fontSize = 11.sp,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.testTag("tab_${tab.route}"),
                        )
                    }
                    }
                }
            },
            floatingActionButton = {
                // X pattern: the round add-note button hides while content
                // scrolls down and returns on scroll-up — driven by the same
                // nested-scroll behavior that hides the top bar.
                val fabVisible by remember {
                    derivedStateOf { scrollBehavior.state.heightOffset == 0f }
                }
                // The add-note action belongs to the tab shell only. Detail,
                // form and settings screens carry their own bottom action
                // bars, and a floating button there competes with them, so
                // the FAB fades out with the tab chrome.
                AnimatedVisibility(
                    visible = fabVisible && MainTab.isTabRoute(currentRoute),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    FloatingActionButton(
                        onClick = {
                            val only = activeRecipients.singleOrNull()
                            if (only != null) {
                                // Exactly one active person: skip the picker.
                                navController.navigate(AppRoutes.noteEditor(only.id))
                            } else {
                                showRecipientPicker = true
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .size(AppDimens.FabSize)
                            .testTag("fab_add_note"),
                    ) {
                        Icon(
                            Icons.Filled.NoteAdd,
                            contentDescription = stringResource(R.string.fab_add_note),
                            modifier = Modifier.size(AppDimens.IconMd),
                        )
                    }
                }
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
                composable(AppDestinations.AiSettings.base) {
                    com.caregiver.mobile.presentation.settings.AiSettingsScreen(graph, navController)
                }
                composable(AppDestinations.About.base) {
                    com.caregiver.mobile.presentation.settings.AboutScreen { navController.popBackStack() }
                }
            }
        }

    // Account pop-up (replaces the old left drawer).
    if (showAccountMenu) {
        // Scrim for outside-click dismissal.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { showAccountMenu = false },
        )
        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopEnd,
            onDismissRequest = { showAccountMenu = false },
        ) {
            com.caregiver.mobile.presentation.common.AccountPopupMenu(
                onDismiss = { showAccountMenu = false },
                onSignInWithGoogle = { /* integration point for Task 2 */ },
                onSettings = {
                    showAccountMenu = false
                    navController.navigate(AppDestinations.Settings.base)
                },
            )
        }
    }

    // X-style picker: a ModalBottomSheet of the active people; loading shows
    // skeleton bars, an empty list offers the add-person flow. The safety
    // banner lives inside the tab content and is never touched by this.
    if (showRecipientPicker) {
        RecipientPickerSheet(
            recipients = activeRecipients,
            loading = peopleState is PeopleState.Loading,
            onPick = { recipientId ->
                showRecipientPicker = false
                navController.navigate(AppRoutes.noteEditor(recipientId))
            },
            onAddPerson = {
                showRecipientPicker = false
                navController.navigate(AppDestinations.AddRecipient.base)
            },
            onDismiss = { showRecipientPicker = false },
        )
    }
}
