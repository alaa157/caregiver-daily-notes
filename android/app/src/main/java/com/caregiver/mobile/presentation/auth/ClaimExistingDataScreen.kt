package com.caregiver.mobile.presentation.auth

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.caregiver.mobile.R
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.accounts.Account

/**
 * Blocking "claim your existing data" screen (#624). It reuses the register
 * form and cannot be dismissed into the workspace: there is no back affordance
 * and [BackHandler] consumes system back whenever an activity owner exists.
 * [registerAccount] is `AccountManager.signUp`; on success [onClaimed] hands
 * the new owner account to the migration move (#625). Failures stay on the
 * form with the localized auth error.
 */
@Composable
fun ClaimExistingDataScreen(
    repository: AuthRepository,
    registerAccount: RegisterAccount,
    onClaimed: (Account) -> Unit,
) {
    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler { }
    }

    val scope = rememberCoroutineScope()
    val latestRegister by rememberUpdatedState(registerAccount)
    val viewModel = remember(repository, scope) {
        AuthViewModel(
            AuthMode.Register,
            repository,
            scope = scope,
            registerAccount = { name, email, password -> latestRegister(name, email, password) },
        )
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.createdAccount) {
        state.createdAccount?.let(onClaimed)
    }

    AuthForm(
        state = state,
        mode = AuthMode.Register,
        heroTitleRes = R.string.migration_claim_title,
        heroSubtitleRes = R.string.migration_claim_body,
        submitLabelRes = R.string.migration_claim_submit,
        onSubmit = viewModel::submit,
        onSwitchMode = {},
        showDisplayName = viewModel.requiresDisplayName,
        onDisplayName = viewModel::onDisplayName,
        showSwitchMode = false,
        modifier = Modifier.fillMaxSize(),
    )
}
