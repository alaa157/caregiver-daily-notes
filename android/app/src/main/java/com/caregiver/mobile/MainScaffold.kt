package com.caregiver.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.history.HistoryScreen
import com.caregiver.mobile.presentation.home.HomeScreen
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

/**
 * Tab shell: 4 tabs plus the add-note action. All destinations are live;
 * detail screens navigate with typed [AppRoutes] builders.
 */
@Composable
fun MainScaffold(graph: AppGraph) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = CaregiverColors.Background,
        bottomBar = {
            Surface(
                color = CaregiverColors.Surface,
                border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Border),
            ) {
                NavigationBar(
                    containerColor = CaregiverColors.Surface,
                    modifier = Modifier.height(AppSizes.bottomNavHeight),
                ) {
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
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MainTab.Home -> Icons.Filled.Home
                                    MainTab.People -> Icons.Filled.People
                                    MainTab.Notes -> Icons.Filled.NoteAdd
                                    MainTab.History -> Icons.Filled.History
                                },
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        // Token selection: primary icon/label on a primarySoft
                        // pill. Test tags keep the UI tests offline-safe.
                        // Tokens: bottomNav 72dp, active primary, inactive
                        // textSecondary.
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CaregiverColors.Primary,
                            selectedTextColor = CaregiverColors.Primary,
                            unselectedIconColor = CaregiverColors.TextSecondary,
                            unselectedTextColor = CaregiverColors.TextSecondary,
                            indicatorColor = CaregiverColors.PrimarySoft,
                        ),
                        modifier = Modifier.testTag("tab_${tab.route}"),
                    )
                }
                }
            }
        },
        floatingActionButton = {
            // HTML People/History FAB: pill 56dp, radius 28, teal, label + plus.
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(MainTab.Notes.route) },
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
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.height(AppSizes.buttonHeightLarge),
            )
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
