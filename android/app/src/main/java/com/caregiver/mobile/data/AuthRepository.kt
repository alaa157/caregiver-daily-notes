package com.caregiver.mobile.data

import com.caregiver.mobile.core.network.TokenHolder
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

/**
 * Sign-in/out plus the 401 rule: any authed call rejected with 401 clears the
 * stored token and throws [LoggedOutException] so navigation returns to login
 * without leaking the previous screen's data.
 */
class AuthRepository(
    private val apis: BackendApis,
    private val settings: SettingsStore,
    private val tokens: TokenHolder,
) {
    val token = settings.token

    suspend fun signIn(email: String, password: String): SignInResult {
        val normalized = normalize(email)
        return attempt(normalized) { apis.auth().login(LoginRequest(normalized, password)) }
    }

    suspend fun signUp(email: String, password: String): SignInResult {
        val normalized = normalize(email)
        return attempt(normalized) { apis.auth().register(RegisterRequest(normalized, password)) }
    }

    suspend fun signOut() {
        settings.clearToken()
        settings.clearEmail()
        tokens.token = null
    }

    suspend fun <T> authorized(block: suspend BackendApis.() -> T): T {
        if (tokens.token == null) {
            tokens.token = settings.token.first()
        }
        try {
            return apis.block()
        } catch (e: HttpException) {
            if (e.code() == 401) {
                signOut()
                throw LoggedOutException()
            }
            throw e
        }
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
    }
}
