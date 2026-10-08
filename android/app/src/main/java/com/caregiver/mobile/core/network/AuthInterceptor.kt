package com.caregiver.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every request when signed in, and
 * sends nothing when signed out — so login/register never leak a stale token
 * and authed calls never go out naked by accident.
 */
class AuthInterceptor(private val tokens: TokenHolder) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokens.token
        if (token.isNullOrBlank()) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build(),
        )
    }
}
