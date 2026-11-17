package com.caregiver.mobile.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.SignInResult
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import com.caregiver.mobile.data.accounts.InvalidDisplayNameException
import com.caregiver.mobile.data.accounts.PasswordProblem
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthMode { Login, Register }

/** Local-account registration callback: AccountManager.signUp's shape (#590). */
typealias RegisterAccount =
    suspend (displayName: String, email: String, password: String) -> Result<Account>

/**
 * Reverse of `PasswordProblem.code()` in AuthRepository (#705). A rejected
 * result only carries the code as a string, so the consumer maps it back to
 * the typed problem the form already knows how to localize.
 */
private fun passwordProblemFromCode(code: String): PasswordProblem? = when (code) {
    "TOO_SHORT" -> PasswordProblem.TooShort
    "TOO_LONG" -> PasswordProblem.TooLong
    "MISSING_LETTER" -> PasswordProblem.MissingLetter
    "MISSING_DIGIT" -> PasswordProblem.MissingDigit
    "ONLY_WHITESPACE" -> PasswordProblem.OnlyWhitespace
    else -> null
}

sealed interface EmailError {
    data object Required : EmailError
    data object Invalid : EmailError
}

sealed interface PasswordError {
    data object Required : PasswordError
    data object TooLong : PasswordError
    data class Policy(val problem: PasswordProblem) : PasswordError
}

sealed interface NameError {
    data object Required : NameError
    data object TooLong : NameError
}

sealed interface FormError {
    data object InvalidCredentials : FormError
    data object DuplicateEmail : FormError
    data object Unreachable : FormError
    data object Generic : FormError
}

data class AuthUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: NameError? = null,
    val emailError: EmailError? = null,
    val passwordError: PasswordError? = null,
    val formError: FormError? = null,
    val busy: Boolean = false,
    val signedIn: Boolean = false,
    val createdAccount: Account? = null,
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
    private val registerAccount: RegisterAccount? = null,
    /** Injectable demo action for tests; production uses [AuthRepository.enterDemo]. */
    private val enterDemoSession: (suspend () -> SignInResult)? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Local registration asks for a display name; server/demo register does not. */
    val requiresDisplayName: Boolean
        get() = mode == AuthMode.Register && registerAccount != null

    fun onDisplayName(value: String) {
        _state.value = _state.value.copy(displayName = value, nameError = null, formError = null)
    }

    fun onEmail(value: String) {
        _state.value = _state.value.copy(email = value, emailError = null, formError = null)
    }

    fun onPassword(value: String) {
        _state.value = _state.value.copy(password = value, passwordError = null, formError = null)
    }

    /**
     * One-tap offline demo (issue #880). Seeds and activates a local demo
     * account through a dedicated path — it never touches the credential
     * fields and never calls [submit]/sign-in, so there is no hard-coded
     * login to fail with "invalid credentials". A single tap leaves the
     * login form and lands in the local demo.
     */
    fun demoSignIn() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(
            busy = true,
            email = "",
            password = "",
            nameError = null,
            emailError = null,
            passwordError = null,
            confirmError = null,
            formError = null,
            createdAccount = null,
        )
        exec.launch {
            val result = try {
                if (enterDemoSession != null) enterDemoSession() else repository.enterDemo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SignInResult.Rejected(null, "Offline demo unavailable.")
            }
            _state.value = _state.value.copy(
                busy = false,
                signedIn = result == SignInResult.SignedIn,
                // A demo failure is never rendered as a credential error.
                formError = if (result == SignInResult.SignedIn) null else FormError.Generic,
            )
        }
    }

    fun submit() {
        val current = _state.value
        val emailError = validateEmail(current.email)
        val passwordError = validatePassword(current.password)
        val nameError = if (requiresDisplayName) validateDisplayName(current.displayName) else null
        if (emailError != null || passwordError != null || nameError != null) {
            _state.value = current.copy(
                nameError = nameError,
                emailError = emailError,
                passwordError = passwordError,
            )
            return
        }
        _state.value = current.copy(
            nameError = null,
            emailError = null,
            passwordError = null,
            formError = null,
            busy = true,
            createdAccount = null,
        )
        exec.launch {
            if (mode == AuthMode.Register && registerAccount != null) {
                val accountResult = try {
                    registerAccount(current.displayName.trim(), current.email, current.password)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure<Account>(e)
                }
                if (accountResult.isSuccess) {
                    _state.value = _state.value.copy(
                        busy = false,
                        signedIn = true,
                        formError = null,
                        createdAccount = accountResult.getOrNull(),
                    )
                } else {
                    val failure = accountResult.exceptionOrNull()
                    val accountError = (failure as? AccountException)?.error
                    val weakProblem = (accountError as? AccountError.WeakPassword)?.problem
                    _state.value = _state.value.copy(
                        busy = false,
                        signedIn = false,
                        nameError = if (failure is InvalidDisplayNameException) NameError.TooLong else null,
                        emailError = if (accountError == AccountError.InvalidEmail) EmailError.Invalid else null,
                        passwordError = weakProblem?.let { PasswordError.Policy(it) },
                        formError = when (accountError) {
                            AccountError.EmailTaken -> FormError.DuplicateEmail
                            AccountError.InvalidCredentials -> FormError.InvalidCredentials
                            AccountError.InvalidEmail -> null
                            is AccountError.WeakPassword -> null
                            else -> FormError.Generic
                        },
                    )
                }
                return@launch
            }
            val result = when (mode) {
                AuthMode.Login -> repository.signIn(current.email, current.password)
                AuthMode.Register -> repository.signUp(current.email, current.password)
            }
            // The codes below are emitted by AuthRepository.toSignInResult —
            // keep this mapping in sync with that producer (#705).
            val rejected = result as? SignInResult.Rejected
            val weakProblem = rejected
                ?.takeIf { it.code == "WEAK_PASSWORD" }
                ?.let { passwordProblemFromCode(it.message) }
            _state.value = _state.value.copy(
                busy = false,
                signedIn = result == SignInResult.SignedIn,
                emailError = if (rejected?.code == "INVALID_EMAIL") EmailError.Invalid else null,
                passwordError = weakProblem?.let { PasswordError.Policy(it) },
                formError = when (result) {
                    SignInResult.SignedIn -> null
                    SignInResult.InvalidCredentials -> FormError.InvalidCredentials
                    is SignInResult.Rejected -> when (result.code) {
                        // Local accounts emit EMAIL_TAKEN; the server still
                        // sends DUPLICATE_EMAIL. Both mean the same to the UI.
                        "EMAIL_TAKEN", "DUPLICATE_EMAIL" -> FormError.DuplicateEmail
                        "INVALID_CREDENTIALS" -> FormError.InvalidCredentials
                        "INVALID_EMAIL" -> null
                        "WEAK_PASSWORD" -> if (weakProblem == null) FormError.Generic else null
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

        /** Mirrors AccountManager.signUp's display-name rule (#590). */
        fun validateDisplayName(raw: String): NameError? {
            val name = raw.trim()
            if (name.isEmpty()) {
                return NameError.Required
            }
            return if (name.length > com.caregiver.mobile.data.accounts.AccountManager.MAX_DISPLAY_NAME_LENGTH) {
                NameError.TooLong
            } else {
                null
            }
        }

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
