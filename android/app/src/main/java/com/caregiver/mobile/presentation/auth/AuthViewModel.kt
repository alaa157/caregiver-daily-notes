package com.caregiver.mobile.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.SignInResult
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class AuthMode { Login, Register }

sealed interface EmailError {
    data object Required : EmailError
    data object Invalid : EmailError
}

sealed interface PasswordError {
    data object Required : PasswordError
    data object TooLong : PasswordError
}

sealed interface ConfirmError {
    data object Mismatch : ConfirmError
}

sealed interface FormError {
    data object InvalidCredentials : FormError
    data object DuplicateEmail : FormError
    data object Unreachable : FormError
    data object Generic : FormError
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val emailError: EmailError? = null,
    val passwordError: PasswordError? = null,
    val confirmError: ConfirmError? = null,
    val formError: FormError? = null,
    val busy: Boolean = false,
    val signedIn: Boolean = false,
)

/**
 * All auth logic lives here so it is unit-testable without a device; the
 * Login/Register composables only render [state]. The injectable [scope]
 * exists for tests — production passes nothing and gets [viewModelScope].
 */
class AuthViewModel(
    private val mode: AuthMode,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun onEmail(value: String) {
        _state.value = _state.value.copy(email = value, emailError = null, formError = null)
    }

    fun onPassword(value: String) {
        _state.value = _state.value.copy(password = value, passwordError = null, formError = null)
    }

    fun onConfirm(value: String) {
        _state.value = _state.value.copy(confirm = value, confirmError = null, formError = null)
    }

    fun submit() {
        val current = _state.value
        val emailError = validateEmail(current.email)
        val passwordError = validatePassword(current.password)
        val confirmError =
            if (mode == AuthMode.Register && current.confirm != current.password) {
                ConfirmError.Mismatch
            } else {
                null
            }
        if (emailError != null || passwordError != null || confirmError != null) {
            _state.value = current.copy(
                emailError = emailError,
                passwordError = passwordError,
                confirmError = confirmError,
            )
            return
        }
        _state.value = current.copy(
            emailError = null,
            passwordError = null,
            confirmError = null,
            formError = null,
            busy = true,
        )
        exec.launch {
            val result = when (mode) {
                AuthMode.Login -> repository.signIn(current.email, current.password)
                AuthMode.Register -> repository.signUp(current.email, current.password)
            }
            _state.value = _state.value.copy(
                busy = false,
                signedIn = result == SignInResult.SignedIn,
                formError = when (result) {
                    SignInResult.SignedIn -> null
                    SignInResult.InvalidCredentials -> FormError.InvalidCredentials
                    is SignInResult.Rejected -> when (result.code) {
                        "DUPLICATE_EMAIL" -> FormError.DuplicateEmail
                        "INVALID_CREDENTIALS" -> FormError.InvalidCredentials
                        else -> FormError.Generic
                    }
                    SignInResult.Unreachable -> FormError.Unreachable
                },
            )
        }
    }

    companion object {
        /** BCrypt limit, mirrored from the backend — measured in bytes, not chars. */
        const val MAX_PASSWORD_BYTES = 72

        fun validateEmail(raw: String): EmailError? {
            val email = raw.trim()
            if (email.isEmpty()) {
                return EmailError.Required
            }
            return if (isPlausibleEmail(email)) null else EmailError.Invalid
        }

        /**
         * Backend-aligned shape check: the server enforces `@Email` on the
         * trimmed, lowercased value. This rejects clearly malformed addresses
         * early; anything plausible still goes to the server for the final
         * word. No password minimum exists on either side — only the 72-byte
         * maximum the backend enforces.
         */
        fun isPlausibleEmail(email: String): Boolean {
            if (email.any { it.isWhitespace() }) {
                return false
            }
            val parts = email.split("@")
            if (parts.size != 2) {
                return false
            }
            val (local, domain) = parts
            if (local.isEmpty() || domain.isEmpty()) {
                return false
            }
            // Empty labels (leading/trailing/double dots) are never valid;
            // single-label domains are the server's call, not ours.
            return domain.split(".").all { it.isNotEmpty() }
        }

        fun validatePassword(password: String): PasswordError? {
            if (password.isEmpty()) {
                return PasswordError.Required
            }
            return if (password.toByteArray(StandardCharsets.UTF_8).size > MAX_PASSWORD_BYTES) {
                PasswordError.TooLong
            } else {
                null
            }
        }
    }
}
