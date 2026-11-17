package com.caregiver.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Popup
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.RootScreen
import com.caregiver.mobile.core.navigation.localLoginRequired
import com.caregiver.mobile.core.navigation.rootScreenFor
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.accounts.AccountManager
import com.caregiver.mobile.presentation.auth.AccountPicker
import com.caregiver.mobile.presentation.auth.LocalAccountDirectory
import com.caregiver.mobile.presentation.auth.LoginScreen
import com.caregiver.mobile.presentation.auth.RegisterAccount
import com.caregiver.mobile.presentation.auth.RegisterScreen
import com.caregiver.mobile.presentation.onboarding.OnboardingScreen
import com.caregiver.mobile.presentation.settings.ServerUrlScreen
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Adapts the graph's single AccountManager to the register form callback. */
internal fun registerAccountFor(manager: AccountManager): RegisterAccount =
    { displayName, email, password ->
        manager.signUp(displayName, email, password)
    }

/**
 * Login nav host shared by the server login and the LOCAL_MODE no-account
 * gate. When local accounts exist, "Switch account" opens the picker; a
 * successful switch lands on Home with a cleared back stack (#707).
 */
@Composable
private fun LocalLoginNavHost(
    graph: AppGraph,
    registerAccount: RegisterAccount?,
    includeServerUrl: Boolean,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    val directory = remember(graph) {
        graph.accountManager?.let { manager ->
            object : LocalAccountDirectory {
                override suspend fun listAccounts(): List<Account> =
                    manager.listAccounts().first()
            }
        }
    }
    NavHost(navController = navController, startDestination = AppDestinations.Login.base) {
        composable(AppDestinations.Login.base) {
            LoginScreen(
                graph.auth,
                navController,
                localAccounts = directory,
                onSwitchAccount = { showPicker = true },
            )
        }
        composable(AppDestinations.Register.base) {
            RegisterScreen(graph.auth, navController, registerAccount)
        }
        if (includeServerUrl) {
            composable(AppDestinations.ServerUrl.base) {
                ServerUrlScreen(graph, navController)
            }
        }
    }
    if (showPicker) {
        val accounts by graph.accounts.collectAsState(initial = emptyList())
        val activeId by graph.activeAccountIdFlow.collectAsState(initial = null)
        Box(
            Modifier
                .fillMaxSize()
                .clickable { showPicker = false },
        )
        Popup(alignment = Alignment.Center, onDismissRequest = { showPicker = false }) {
            AccountPicker(
                accounts = accounts,
                activeAccountId = activeId,
                onSelect = { account ->
                    showPicker = false
                    // The root gate observes the active id and swaps this whole
                    // nav host for MainScaffold; navigating to Home here would
                    // target a route this login graph does not contain.
                    scope.launch { graph.accountManager?.switchAccount(account.id) }
                },
                onAddAccount = {
                    showPicker = false
                    navController.navigate(AppDestinations.Register.base)
                },
                onDismiss = { showPicker = false },
                onRemoveAccount = { account ->
                    graph.accountManager?.removeAccount(account.id)
                        ?: Result.failure(IllegalStateException("No local accounts"))
                },
            )
        }
    }
}

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
    val registerAccount: RegisterAccount? = graph.accountManager?.let(::registerAccountFor)
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
        RootScreen.LOGIN -> LocalLoginNavHost(
            graph,
            registerAccount,
            includeServerUrl = true,
        )
        RootScreen.APP -> {
            if (localLoginRequired(localMode, hasActiveAccount)) {
                // LOCAL_MODE but no account: show local login instead of crashing.
                LocalLoginNavHost(graph, registerAccount, includeServerUrl = false)
            } else {
                MainScaffold(graph)
            }
        }
    }
}
