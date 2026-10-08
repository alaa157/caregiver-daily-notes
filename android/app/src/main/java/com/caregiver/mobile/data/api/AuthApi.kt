package com.caregiver.mobile.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** POST /api/auth/register (201) and POST /api/auth/login (200) → {token}. */
interface AuthApi {
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

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
