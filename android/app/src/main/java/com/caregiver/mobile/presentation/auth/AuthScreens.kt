package com.caregiver.mobile.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.data.AuthRepository

/** Login (board 1) wired to its ViewModel; signed-in flips at the root nav. */
@Composable
fun LoginScreen(repository: AuthRepository, navController: NavController) {
    val vm: AuthViewModel = authViewModel(AuthMode.Login, repository)
    val state by vm.state.collectAsState()
    AuthForm(
        state = state,
        mode = AuthMode.Login,
        onEmail = vm::onEmail,
        onPassword = vm::onPassword,
        onConfirm = vm::onConfirm,
        onSubmit = vm::submit,
        onSwitchMode = { navController.navigate(AppDestinations.Register.base) },
        modifier = Modifier,
    )
}

/** Register (missing-page spec): mirrors login plus the confirm field. */
@Composable
fun RegisterScreen(repository: AuthRepository, navController: NavController) {
    val vm: AuthViewModel = authViewModel(AuthMode.Register, repository)
    val state by vm.state.collectAsState()
    AuthForm(
        state = state,
        mode = AuthMode.Register,
        onEmail = vm::onEmail,
        onPassword = vm::onPassword,
        onConfirm = vm::onConfirm,
        onSubmit = vm::submit,
        onSwitchMode = { navController.navigate(AppDestinations.Login.base) },
        modifier = Modifier,
    )
}

@Composable
fun authViewModel(mode: AuthMode, repository: AuthRepository): AuthViewModel {
    val key = if (mode == AuthMode.Login) "login" else "register"
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(mode, repository) as T
            }
        },
    )
}
