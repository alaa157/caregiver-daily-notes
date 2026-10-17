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
import com.caregiver.mobile.presentation.settings.ServerUrlScreen

/**
 * Root gate. In LOCAL_MODE the app is standalone: go straight to MainScaffold,
 * no login/register/server-url screens.
 */
@Composable
fun CaregiverRoot(graph: AppGraph) {
    if (com.caregiver.mobile.BuildConfig.LOCAL_MODE) {
        MainScaffold(graph)
        return
    }
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
            composable(AppDestinations.ServerUrl.base) {
                ServerUrlScreen(graph, navController)
            }
        }
    } else {
        MainScaffold(graph)
    }
}
