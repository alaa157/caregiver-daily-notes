package com.caregiver.mobile.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Demo sign-in state (screens.md: idle, submitting, error, success). */
data class DemoSignInState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
)

/**
 * No-login demo gate (Step 6). Fields are layout-only per the spec screen;
 * nothing is validated and no password is ever stored. "Start demo" sets
 * the local session; success flips the root to Home via the session flow.
 */
class DemoSignInViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(DemoSignInState())
    val state: StateFlow<DemoSignInState> = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, failed = false) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, failed = false) }

    fun startDemo() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, failed = false) }
            try {
                graph.ensureSeeded()
                graph.demoSession.startDemo()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, failed = true) }
            }
        }
    }
}
