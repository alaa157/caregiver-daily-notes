package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import com.caregiver.mobile.R
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.presentation.auth.AuthForm
import com.caregiver.mobile.presentation.auth.AuthMode
import com.caregiver.mobile.presentation.auth.AuthViewModel
import com.caregiver.mobile.presentation.auth.RegisterAccount

/**
 * Add-account flow for local mode (issue #610). Reuses the register form via
 * [AuthForm] and reports the created account so the caller can switch to it.
 * The original account stays active until [onCreated] runs: [registerAccount]
 * is AccountManager.signUp, which only activates the new account on success.
 */
@Composable
fun AddAccountFlow(
    repository: AuthRepository,
    registerAccount: RegisterAccount,
    onCancel: () -> Unit,
    onCreated: (Account) -> Unit,
) {
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
        state.createdAccount?.let(onCreated)
    }

    AuthForm(
        state = state,
        mode = AuthMode.Register,
        heroTitleRes = R.string.app_name,
        heroSubtitleRes = R.string.tagline,
        submitLabelRes = R.string.register,
        onSubmit = viewModel::submit,
        onSwitchMode = onCancel,
        showDisplayName = viewModel.requiresDisplayName,
        onDisplayName = viewModel::onDisplayName,
        onBack = onCancel,
        showSwitchMode = false,
        modifier = Modifier.fillMaxSize(),
    )
}
