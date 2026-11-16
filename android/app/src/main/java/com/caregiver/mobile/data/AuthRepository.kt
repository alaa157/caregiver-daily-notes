package com.caregiver.mobile.data

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import com.caregiver.mobile.data.accounts.AccountManager
import com.caregiver.mobile.data.accounts.PasswordProblem
import com.caregiver.mobile.data.api.ApiErrors
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.RegisterRequest
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/** Thrown by [AuthRepository.authorized] after a 401 has signed the user out. */
class LoggedOutException : IOException("Session expired")

sealed interface SignInResult {
    data object SignedIn : SignInResult
    data object InvalidCredentials : SignInResult
    data class Rejected(val code: String?, val message: String) : SignInResult
    data object Unreachable : SignInResult
}

/** Stable code for the specific password rule that failed (#598). */
fun PasswordProblem.code(): String = when (this) {
    PasswordProblem.TooShort -> "TOO_SHORT"
    PasswordProblem.TooLong -> "TOO_LONG"
    PasswordProblem.MissingLetter -> "MISSING_LETTER"
    PasswordProblem.MissingDigit -> "MISSING_DIGIT"
    PasswordProblem.OnlyWhitespace -> "ONLY_WHITESPACE"
}

/**
 * Account-layer failures mapped onto the unchanged UI contract (#598).
 * Exhaustive over all six AccountError variants — no `else` — so a new
 * variant cannot silently fall through to an unmapped rejection.
 */
fun AccountError.toSignInResult(): SignInResult = when (this) {
    AccountError.InvalidCredentials -> SignInResult.InvalidCredentials
    AccountError.EmailTaken -> SignInResult.Rejected("EMAIL_TAKEN", "Email is already registered.")
    is AccountError.WeakPassword -> SignInResult.Rejected("WEAK_PASSWORD", problem.code())
    AccountError.InvalidEmail -> SignInResult.Rejected("INVALID_EMAIL", "Enter a valid email address.")
    AccountError.AccountNotFound -> SignInResult.Rejected("ACCOUNT_NOT_FOUND", "Account not found.")
    AccountError.CannotRemoveActive -> SignInResult.Rejected("CANNOT_REMOVE_ACTIVE", "The active account cannot be removed.")
}

/**
 * Sign-in/out plus the 401 rule: any authed call rejected with 401 clears the
 * stored token and throws [LoggedOutException] so navigation returns to login
 * without leaking the previous screen's data.
 *
 * In local mode an [AccountManager] is injected and sign-in/up run against
 * local accounts; the resulting failure is mapped with [AccountError.toSignInResult]
 * so screens keep using the same [SignInResult] type. Without a manager the
 * original server/demo path runs byte-for-byte as before.
 */
class AuthRepository(
    private val apis: BackendApis,
    private val settings: SettingsStore,
    private val tokens: TokenHolder,
    private val accountManager: AccountManager? = null,
    /** Resolves the active account's APIs at call time; defaults to [apis]. */
    private val localApisFor: suspend () -> BackendApis = { apis },
) {
    val token = settings.token

    suspend fun signIn(email: String, password: String): SignInResult {
        val manager = accountManager ?: return signInRemote(email, password)
        return when (val result = manager.signIn(email, password)) {
            is Result.Success -> finishLocalSignIn(result.value.id, email)
            is Result.Failure -> result.toSignInResult()
        }
    }

    /**
     * Three-argument overload used by the local register form (#599). Server
     * and demo modes have no name on the wire, so they keep the two-argument
     * path — the name is only meaningful to the local AccountManager.
     */
    suspend fun signUp(name: String, email: String, password: String): SignInResult {
        val manager = accountManager ?: return signUpRemote(email, password)
        return when (val result = manager.signUp(name, email, password)) {
            is Result.Success -> finishLocalSignIn(result.value.id, email)
            is Result.Failure -> result.toSignInResult()
        }
    }

    /** Two-argument form delegates with a name derived from the address. */
    suspend fun signUp(email: String, password: String): SignInResult =
        signUp(deriveName(email), email, password)

    suspend fun signOut() {
        // In local mode this runs the account swap: clears the stored active
        // id and closes the account database before the session is forgotten.
        accountManager?.signOut()
        settings.clearToken()
        settings.clearEmail()
        tokens.token = null
    }

    suspend fun <T> authorized(block: suspend BackendApis.() -> T): T {
        if (tokens.token == null) {
            tokens.token = settings.token.first()
        }
        try {
            // The supplier is resolved per call so a switch to another
            // account routes authed calls at that account's database.
            return localApisFor().block()
        } catch (e: HttpException) {
            if (e.code() == 401) {
                signOut()
                throw LoggedOutException()
            }
            throw e
        }
    }

    /** Unchanged server/demo sign-in path. */
    private suspend fun signInRemote(email: String, password: String): SignInResult {
        val normalized = normalize(email)
        return attempt(normalized) { apis.auth().login(LoginRequest(normalized, password)) }
    }

    /** Unchanged server/demo sign-up path. */
    private suspend fun signUpRemote(email: String, password: String): SignInResult {
        val normalized = normalize(email)
        return attempt(normalized) { apis.auth().register(RegisterRequest(normalized, password)) }
    }

    private suspend fun finishLocalSignIn(accountId: String, email: String): SignInResult {
        // Local token is a stable opaque handle derived from the account id.
        // There is no server to authenticate to, so it is not a secret — and
        // it is never the password or the derived hash.
        val localToken = localToken(accountId)
        settings.setToken(localToken)
        settings.setEmail(normalize(email))
        tokens.token = localToken
        return SignInResult.SignedIn
    }

    private fun Result<*>.toSignInResult(): SignInResult {
        val error = (exceptionOrNull() as? AccountException)?.error
        return error?.toSignInResult()
            ?: SignInResult.Rejected(null, "Request failed. Please try again.")
    }

    private suspend fun attempt(email: String, call: suspend () -> AuthResponse): SignInResult {
        try {
            val response = call()
            settings.setToken(response.token)
            settings.setEmail(email)
            tokens.token = response.token
            return SignInResult.SignedIn
        } catch (e: HttpException) {
            if (e.code() == 401) {
                return SignInResult.InvalidCredentials
            }
            // The code drives localized UI copy; the message is kept for
            // debugging and never rendered.
            val error = ApiErrors.parse(e)
            return SignInResult.Rejected(
                error?.code,
                error?.message ?: "Request failed (${e.code()}).",
            )
        } catch (e: IOException) {
            return SignInResult.Unreachable
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            return SignInResult.Rejected(null, "Request failed. Please try again.")
        }
    }

    companion object {
        /** Mirrors the backend: trim + ROOT-lowercase before sending. */
        fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)

        /** Stable, non-secret handle for a local session (#597 decision). */
        fun localToken(accountId: String): String = "local:$accountId"

        /** Display name for callers that still use the two-argument signUp. */
        fun deriveName(email: String): String =
            normalize(email).substringBefore('@').trim().take(80).ifEmpty { "Caregiver" }
    }
}
