package com.caregiver.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to protected requests when signed in.
 * Login and registration carry the [NO_AUTH_MARKER] header (added by
 * [com.caregiver.mobile.data.api.AuthApi], stripped here) so they never leak
 * a previous session's token — e.g. re-login while signed in.
 */
class AuthInterceptor(private val tokens: TokenHolder) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(NO_AUTH_MARKER) != null) {
            return chain.proceed(
                request.newBuilder().removeHeader(NO_AUTH_MARKER).build(),
            )
        }
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

    companion object {
        const val NO_AUTH_MARKER = "X-No-Auth"
    }
}
