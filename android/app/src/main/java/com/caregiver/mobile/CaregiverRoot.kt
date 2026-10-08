package com.caregiver.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.presentation.auth.LoginScreen
import com.caregiver.mobile.presentation.auth.RegisterScreen

/**
 * Root gate: no token → auth graph (login/register); token present → tabs.
 * A 401 anywhere signs out through AuthRepository, which flips this back to
 * login without leaking the previous screen's data.
 */
@Composable
fun CaregiverRoot(graph: AppGraph) {
    val token by graph.auth.token.collectAsState(initial = null)
    if (token == null) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = AppDestinations.Login.base) {
            composable(AppDestinations.Login.base) {
                LoginScreen(graph.auth, navController)
            }
            composable(AppDestinations.Register.base) {
                RegisterScreen(graph.auth, navController)
            }
        }
    } else {
        MainScaffold()
    }
}
