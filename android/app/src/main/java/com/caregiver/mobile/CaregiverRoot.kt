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
import com.caregiver.mobile.presentation.auth.RegisterAccount
import com.caregiver.mobile.presentation.auth.RegisterScreen
import com.caregiver.mobile.presentation.onboarding.OnboardingScreen
import com.caregiver.mobile.presentation.settings.ServerUrlScreen
import kotlinx.coroutines.flow.collect

/**
 * Root gate. In LOCAL_MODE the app is standalone with local accounts:
 * after onboarding, require an active account before showing MainScaffold.
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
    // Wire local registration to the graph's single AccountManager (#704):
    // that makes the form ask for a Name and lets PasswordPolicy failures
    // reach the UI as specific messages. Server/demo graphs have no manager,
    // so their two-argument register path is unchanged.
    val registerAccount: RegisterAccount? = graph.accountManager?.let { manager ->
        { displayName: String, email: String, password: String ->
            manager.signUp(displayName, email, password)
        }
    }
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
    // In LOCAL_MODE, check whether an active account exists before
    // allowing MainScaffold. Without an account, data loads fail with
    // "No active account: local database access is unavailable".
    val hasActiveAccount by produceState(initialValue = false) {
        if (!localMode) return@produceState
        // Collect, don't read once: signing in updates the active id and the
        // root must leave the local login screen without a process restart.
        // (A one-shot first() also needed an import that was missing here.)
        try {
            graph.activeAccountIdFlow.collect { id -> value = id != null }
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
                    RegisterScreen(graph.auth, navController, registerAccount)
                }
                composable(AppDestinations.ServerUrl.base) {
                    ServerUrlScreen(graph, navController)
                }
            }
        }
        RootScreen.APP -> {
            if (localMode && !hasActiveAccount) {
                // LOCAL_MODE but no account: show local login instead of crashing.
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = AppDestinations.Login.base) {
                    composable(AppDestinations.Login.base) {
                        LoginScreen(graph.auth, navController)
                    }
                    composable(AppDestinations.Register.base) {
                        RegisterScreen(graph.auth, navController, registerAccount)
                    }
                }
            } else {
                MainScaffold(graph)
            }
        }
    }
}
