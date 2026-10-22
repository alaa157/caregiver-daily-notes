package com.caregiver.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.RootScreen
import com.caregiver.mobile.core.navigation.rootScreenFor
import com.caregiver.mobile.presentation.auth.LoginScreen
import com.caregiver.mobile.presentation.auth.RegisterScreen
import com.caregiver.mobile.presentation.onboarding.OnboardingScreen
import com.caregiver.mobile.presentation.settings.ServerUrlScreen
import kotlinx.coroutines.flow.collect

/**
 * Root gate. In LOCAL_MODE the app is standalone: go straight to MainScaffold,
 * no login/register/server-url screens.
 */
@Composable
fun CaregiverRoot(graph: AppGraph) {
    // The onboarding state lives in DataStore, so reading it is asynchronous.
    // Until it has been read the app shows its own background — the same
    // surface the window shows while the process starts — instead of
    // guessing and flashing the wrong screen.
    val onboardingCompleted by produceState<Boolean?>(initialValue = null) {
        try {
            graph.settings.onboardingCompleted.collect { stored -> value = stored }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // A read that failed leaves the app without its state. Showing
            // the onboarding is the safe answer: the caregiver can skip it.
            value = false
        }
    }
    val localMode = com.caregiver.mobile.BuildConfig.LOCAL_MODE
    // The token is only read when the app is not standalone: the local build
    // never signs in, and reading it would build the API stack for nothing.
    val signedIn by produceState<Boolean?>(initialValue = null) {
        if (localMode) return@produceState
        try {
            graph.auth.token.collect { token -> value = token != null }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            value = false
        }
    }
    when (rootScreenFor(onboardingCompleted, localMode, signedIn)) {
        RootScreen.PENDING -> return
        RootScreen.ONBOARDING -> {
            OnboardingScreen(graph) { }
            return
        }
        RootScreen.LOGIN -> {
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
        }
        RootScreen.APP -> MainScaffold(graph)
    }
}
