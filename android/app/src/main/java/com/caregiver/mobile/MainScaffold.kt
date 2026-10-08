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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.presentation.home.HomeScreen
import com.caregiver.mobile.presentation.recipients.AddRecipientScreen
import com.caregiver.mobile.presentation.recipients.RecipientDetailScreen
import com.caregiver.mobile.presentation.recipients.RecipientsScreen

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
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(MainTab.Notes.route) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
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
            composable(MainTab.Notes.route) { Placeholder(MainTab.Notes.route) }
            composable(MainTab.History.route) { Placeholder(MainTab.History.route) }
            composable(
                AppDestinations.RecipientDetail.base,
                arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
            ) { entry ->
                RecipientDetailScreen(entry.arguments!!.getString("recipientId")!!, graph, navController)
            }
            composable(AppDestinations.AddRecipient.base) {
                AddRecipientScreen(graph, navController)
            }
            // Tasks 5-7 replace these bodies; routes stay identical.
            future(AppDestinations.NoteEditor.base)
            future(AppDestinations.NoteSaved.base)
            future(AppDestinations.NoteDetail.base)
            future(AppDestinations.Addendum.base)
            future(AppDestinations.Summary.base)
            future(AppDestinations.SummaryResult.base)
            future(AppDestinations.Plans.base)
            future(AppDestinations.PlanProposal.base)
            future(AppDestinations.PlanEdit.base)
            future(AppDestinations.PlanVersions.base)
            future(AppDestinations.Settings.base)
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.future(route: String) {
    composable(route) { Placeholder(route) }
}

@Composable
private fun Placeholder(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label)
    }
}
