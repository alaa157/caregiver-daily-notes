package com.caregiver.mobile

import androidx.activity.ComponentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import com.caregiver.mobile.core.navigation.hasUnsavedChanges
import com.caregiver.mobile.core.network.networkAvailabilityFlow
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.OutboxSyncWorker
import com.caregiver.mobile.presentation.common.AppCenterTopBar
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.common.rememberTopBarHideState
import com.caregiver.mobile.presentation.common.selectableRecipients
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
import com.caregiver.mobile.ui.component.AppNavigationBar
import com.caregiver.mobile.ui.component.AppTabItem
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

    // Tab order for the NavHost transitions below: each tab route maps to its
    // bottom-bar index, every other destination reads as -1 ("deeper than a
    // tab"), and comparing the two indices picks the slide direction —
    // forward moves enter from the right, back moves from the left.
    val routeIndexMap = remember {
        MainTab.entries.mapIndexed { index, tab -> tab.route to index }.toMap()
    }

    // Metrolist-pattern nav shell input (ui/component/AppNavigation.kt):
    // each tab as an AppTabItem — route, label resource, and the
    // filled/outlined icon pair the bar picks by selection state.
    val navigationItems = remember {
        MainTab.entries.map { tab ->
            AppTabItem(
                route = tab.route,
                titleRes = tab.titleRes,
                activeIcon = when (tab) {
                    MainTab.Home -> Icons.Filled.Home
                    MainTab.People -> Icons.Filled.People
                    MainTab.Notes -> Icons.Filled.NoteAdd
                    MainTab.History -> Icons.Filled.History
                },
                inactiveIcon = when (tab) {
                    MainTab.Home -> Icons.Outlined.Home
                    MainTab.People -> Icons.Outlined.People
                    MainTab.Notes -> Icons.Outlined.NoteAdd
                    MainTab.History -> Icons.Outlined.History
                },
            )
        }
    }

    // X-style shared top bar, tab routes only; it hides on scroll-down and
    // returns on scroll-up. Slide it with our own offset instead of material3's
    // stock scroll behavior: that behavior collapses a hardcoded 64dp while
    // this bar is 56dp, so its first full hide lays the bar out at -16dp and
    // crashes the process mid-scroll. See TopBarHide.
    val topBarHide = rememberTopBarHideState()

    // The shared bar now animates instead of appearing and vanishing: it
    // fades out when a detail route takes over and fades back in on return.
    // Re-deriving it from the back stack lets the fade run in step with the
    // destination transition.
    var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(backStack) {
        shouldShowTopBar = MainTab.isTabRoute(backStack?.destination?.route)
    }

    // Account pop-up: opened from the top-bar avatar; dismissed by tapping
    // outside or choosing an item. (Replaces the old left drawer.)
    var showAccountMenu by remember { mutableStateOf(false) }

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
        RecipientsViewModel(graph.apis, graph.auth, admin = graph.recipientAdmin)
    }
    val peopleState by recipientsVm.state.collectAsStateWithLifecycle()
    val activeRecipients = selectableRecipients(
        (peopleState as? PeopleState.Content)?.recipients.orEmpty(),
    )

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

    // A tab tap can leave a detail form. Wait for confirmation instead of
    // letting the pop discard edits the screen has marked as unsaved.
    var pendingTabRoute by remember { mutableStateOf<String?>(null) }

    // Single tab-navigation path: every tab tap lands on that tab's root.
    // If the tab is already in the back stack (for example People while a
    // recipient detail is open), pop its children so the existing root entry
    // — and its scroll position — is reused. Otherwise pop back to Home
    // before pushing the tab. restoreState is deliberately off: restoring a
    // saved child stack would reopen a detail screen instead of the tab.
    val navigateToTabNow: (String) -> Unit = { route ->
        if (!navController.popBackStack(route, inclusive = false)) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }
    val navigateToTab: (String) -> Unit = { route ->
        if (backStack?.hasUnsavedChanges() == true) {
            pendingTabRoute = route
        } else {
            navigateToTabNow(route)
        }
    }

    // Back closes an open account pop-up.
    BackHandler(enabled = showAccountMenu) {
        showAccountMenu = false
    }

    Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.nestedScroll(topBarHide.nestedScrollConnection),
            topBar = {
                // The four tab routes share the center-aligned bar; detail routes
                // keep drawing their own AppTopBar with a back button, so this
                // one fades away for them instead of vanishing mid-transition.
                AnimatedVisibility(
                    visible = shouldShowTopBar,
                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 200)),
                ) {
                    Row {
                        AppCenterTopBar(
                            title = stringResource(R.string.app_name),
                            avatarLetter = avatarLetter,
                            onAvatarClick = { showAccountMenu = !showAccountMenu },
                            avatarContentDescription = stringResource(R.string.account_menu_open),
                            avatarModifier = Modifier.testTag("drawer_avatar"),
                            modifier = Modifier
                                .offset { IntOffset(0, topBarHide.offsetPx.roundToInt()) }
                                .onSizeChanged { topBarHide.updateBarHeight(it.height.toFloat()) },
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
                    // Metrolist-pattern bar (ui/component/AppNavigation.kt):
                    // route-template selection with a per-tab child-ownership
                    // table; the tab-root re-tap contract (one
                    // ScrollToTop.emit per tap), the tab_* test tags via the
                    // itemModifier hook, the transparent selection indicator,
                    // and the zero-inset compact strip all carry over.
                    AppNavigationBar(
                        navigationItems = navigationItems,
                        currentRoute = currentRoute,
                        onItemClick = { item, _ ->
                            if (currentRoute == item.route) {
                                // Re-tapping the tab that is already the
                                // current destination asks its list to scroll
                                // back to top; it must not push a duplicate.
                                ScrollToTop.emit(item.route)
                            } else {
                                // A tab stays visually selected while one of
                                // its children is open. Tapping it is still a
                                // request for the tab root, not a no-op.
                                navigateToTab(item.route)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .height(AppDimens.BottomNavH),
                        // The bar's tag contract rides through the hook so
                        // the shell keeps composing tags per tab.
                        itemModifier = { item -> Modifier.testTag("tab_${item.route}") },
                    )
                }
            },
            floatingActionButton = {
                // The add action belongs to the tab shell only. Detail, form
                // and settings screens carry their own bottom action bars, and
                // a floating button there competes with them, so the FAB only
                // shows on a tab route. It stays put while the list scrolls.
                val onPeople = currentRoute == MainTab.People.route
                AnimatedVisibility(
                    visible = MainTab.isTabRoute(currentRoute),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (onPeople) {
                                navController.navigate(AppDestinations.AddRecipient.base) { launchSingleTop = true }
                            } else {
                                val only = activeRecipients.singleOrNull()
                                if (only != null) {
                                    // Exactly one active person: skip the picker.
                                    navController.navigate(AppRoutes.noteEditor(only.id))
                                } else {
                                    showRecipientPicker = true
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(AppDimens.FabSize)
                            .testTag(if (onPeople) "fab_add_person" else "fab_add_note"),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(
                                if (onPeople) R.string.people_add else R.string.fab_add_note,
                            ),
                            modifier = Modifier.size(AppDimens.IconMd),
                        )
                    }
                }
            },
        ) { innerPadding ->
            // Direction-aware slide + fade (Metrolist pattern): a forward move
            // — deeper than any tab, or a later tab — brings the incoming
            // screen in from the right by an eighth of the width while the
            // outgoing one leaves to the left; back mirrors both, and pops
            // use the mirrored pop transitions. The 200ms fade keeps this
            // far shorter than the Navigation-Compose 700ms default.
            NavHost(
                navController = navController,
                startDestination = MainTab.Home.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    val currentRouteIndex = routeIndexMap[targetState.destination.route] ?: -1
                    val previousRouteIndex = routeIndexMap[initialState.destination.route] ?: -1

                    if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex) {
                        slideInHorizontally { it / 8 } + fadeIn(tween(200))
                    } else {
                        slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                    }
                },
                exitTransition = {
                    val currentRouteIndex = routeIndexMap[initialState.destination.route] ?: -1
                    val targetRouteIndex = routeIndexMap[targetState.destination.route] ?: -1

                    if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex) {
                        slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                    } else {
                        slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                    }
                },
                popEnterTransition = {
                    val currentRouteIndex = routeIndexMap[targetState.destination.route] ?: -1
                    val previousRouteIndex = routeIndexMap[initialState.destination.route] ?: -1

                    if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex) {
                        slideInHorizontally { it / 8 } + fadeIn(tween(200))
                    } else {
                        slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                    }
                },
                popExitTransition = {
                    val currentRouteIndex = routeIndexMap[initialState.destination.route] ?: -1
                    val targetRouteIndex = routeIndexMap[targetState.destination.route] ?: -1

                    if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex) {
                        slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                    } else {
                        slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                    }
                },
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
                composable(AppDestinations.AiSettings.base) { entry ->
                    com.caregiver.mobile.presentation.settings.AiSettingsScreen(graph, navController, entry)
                }
                composable(AppDestinations.About.base) {
                    com.caregiver.mobile.presentation.settings.AboutScreen(
                        updateChecker = graph.updateChecker,
                    ) { navController.popBackStack() }
                }
                composable(AppDestinations.ThemeColors.base) {
                    com.caregiver.mobile.presentation.settings.ThemeColorsScreen(graph, navController)
                }
            }
        }

    pendingTabRoute?.let { route ->
        AlertDialog(
            onDismissRequest = { pendingTabRoute = null },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingTabRoute = null
                    navigateToTabNow(route)
                }) {
                    Text(stringResource(R.string.unsaved_changes_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTabRoute = null }) {
                    Text(stringResource(R.string.unsaved_changes_keep_editing))
                }
            },
        )
    }

    // Account pop-up (replaces the old left drawer).
    if (showAccountMenu) {
        // Scrim for outside-click dismissal.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable { showAccountMenu = false },
        )
        androidx.compose.ui.window.Popup(
            alignment = Alignment.Center,
            onDismissRequest = { showAccountMenu = false },
        ) {
            com.caregiver.mobile.presentation.common.AccountPopupMenu(
                onDismiss = { showAccountMenu = false },
                accountName = displayName,
                avatarLetter = avatarLetter,
                // Standalone local-first builds have no accounts at all, so
                // the Google row is hidden instead of showing a dead toast.
                showSignInWithGoogle = !graph.isLocalMode,
                onSignInWithGoogle = {
                    showAccountMenu = false
                    activity?.let { ctx ->
                        android.widget.Toast.makeText(
                            ctx,
                            ctx.getString(R.string.experimental_google_signin),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onSettings = {
                    showAccountMenu = false
                    navController.navigate(AppDestinations.Settings.base) {
                        launchSingleTop = true
                    }
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
