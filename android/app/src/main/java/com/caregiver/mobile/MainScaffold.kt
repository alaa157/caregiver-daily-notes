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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.caregiver.mobile.core.navigation.MainTab

/**
 * Tab shell: 4 tabs plus the add-note action. Tab content is a placeholder
 * until its feature task lands (Tasks 3-7); the FAB parks on the Notes tab
 * until the Task 5 editor takes it over.
 */
@Composable
fun MainScaffold() {
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
            MainTab.entries.forEach { tab ->
                composable(tab.route) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(tab.titleRes))
                    }
                }
            }
        }
    }
}
