package com.caregiver.mobile.data.api

import com.caregiver.mobile.core.network.AuthInterceptor
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * POST /api/auth/register (201) and POST /api/auth/login (200) → {token}.
 * Both carry the no-auth marker so [AuthInterceptor] never attaches a stale
 * bearer token to them.
 */
interface AuthApi {
    @Headers(AuthInterceptor.NO_AUTH_MARKER + ": true")
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @Headers(AuthInterceptor.NO_AUTH_MARKER + ": true")
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse
}

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String)

/** Shared error envelope {code, message}: INVALID_CREDENTIALS, DUPLICATE_EMAIL, … */
@Serializable
data class ErrorResponse(val code: String, val message: String)
