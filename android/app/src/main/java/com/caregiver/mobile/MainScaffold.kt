package com.caregiver.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.history.HistoryScreen
import com.caregiver.mobile.presentation.home.HomeScreen
import com.caregiver.mobile.presentation.notes.AddendumScreen
import com.caregiver.mobile.presentation.notes.NoteDetailScreen
import com.caregiver.mobile.presentation.notes.NoteEditorScreen
import com.caregiver.mobile.presentation.notes.NoteSavedScreen
import com.caregiver.mobile.presentation.recipients.AddRecipientScreen
import com.caregiver.mobile.presentation.recipients.RecipientDetailScreen
import com.caregiver.mobile.presentation.recipients.RecipientsScreen
import com.caregiver.mobile.presentation.summary.SummaryPeriodScreen
import com.caregiver.mobile.presentation.summary.SummaryResultScreen
import java.time.LocalDate

/**
 * Tab shell: 4 tabs plus the add-note action. Home and People are live
 * (Task 4); every future destination is registered as a labeled placeholder
 * so detail actions can navigate today and feature tasks replace the body.
 */
@Composable
fun MainScaffold(graph: AppGraph) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(MainTab.Home.route)
                                launchSingleTop = true
                            }
                        },
                        label = { Text(stringResource(tab.titleRes)) },
                        icon = { /* Task 8: tab icons in the design language */ },
                        // Explicit in-palette selection: teal icon/label on a
                        // neutral pill. Test tags keep the UI tests offline-safe.
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CaregiverColors.Primary,
                            selectedTextColor = CaregiverColors.Primary,
                            indicatorColor = CaregiverColors.BorderSoft,
                        ),
                        modifier = Modifier.testTag("tab_${tab.route}"),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(MainTab.Notes.route) }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.fab_add_note),
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.Home.route,
            modifier = Modifier.padding(innerPadding),
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
            // Task 7 replaces these bodies; routes stay identical.
            future(AppDestinations.Plans.base)
            future(AppDestinations.PlanProposal.base)
            future(AppDestinations.PlanEdit.base)
            future(AppDestinations.PlanVersions.base)
            future(AppDestinations.Settings.base)
        }
    }
}

private fun NavGraphBuilder.future(route: String) {
    composable(route) { Placeholder(route) }
}

@Composable
private fun Placeholder(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label)
    }
}
