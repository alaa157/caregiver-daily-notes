package com.caregiver.mobile.data.api

import com.caregiver.mobile.core.network.AuthInterceptor
import com.caregiver.mobile.core.network.TokenHolder
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/** Thrown when the stored server URL fails validation before any request. */
class InvalidBaseUrlException(val problem: UrlProblem) :
    IllegalArgumentException("Invalid server URL: $problem")

enum class UrlProblem {
    Empty,
    Unparsable,
    UnsupportedScheme,
    MissingHost,
    HttpNotAllowed,
    UserInfo,
    Query,
    Fragment,
}

sealed interface BaseUrlCheck {
    data class Valid(val normalized: String) : BaseUrlCheck
    data class Invalid(val problem: UrlProblem) : BaseUrlCheck
}

/**
 * Builds the Retrofit client. The base URL is read fresh for every service
 * lookup so the Task 7 server-URL setting takes effect without a restart;
 * unknown JSON fields are ignored so additive backend changes don't crash
 * old builds.
 */
object ApiClient {
    val json: Json = Json { ignoreUnknownKeys = true }

    /**
     * Validates a user-typed server URL. A missing scheme defaults to HTTPS —
     * never silently to HTTP. Path prefixes are preserved and the trailing
     * slash Retrofit requires is normalized.
     */
    fun checkBaseUrl(raw: String): BaseUrlCheck {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return BaseUrlCheck.Invalid(UrlProblem.Empty)
        }
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        if ("://" in trimmed) {
            val scheme = trimmed.substringBefore("://").lowercase()
            if (scheme != "https" && scheme != "http") {
                return BaseUrlCheck.Invalid(UrlProblem.UnsupportedScheme)
            }
        }
        val url = withScheme.toHttpUrlOrNull()
            ?: return BaseUrlCheck.Invalid(UrlProblem.Unparsable)
        if (url.host.isEmpty()) {
            return BaseUrlCheck.Invalid(UrlProblem.MissingHost)
        }
        if (url.scheme == "http" && !isDevHost(url.host)) {
            return BaseUrlCheck.Invalid(UrlProblem.HttpNotAllowed)
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return BaseUrlCheck.Invalid(UrlProblem.UserInfo)
        }
        if (url.query != null) {
            return BaseUrlCheck.Invalid(UrlProblem.Query)
        }
        if (url.fragment != null) {
            return BaseUrlCheck.Invalid(UrlProblem.Fragment)
        }
        val normalized = url.toString().let { if (it.endsWith("/")) it else "$it/" }
        return BaseUrlCheck.Valid(normalized)
    }

    /**
     * Development HTTP policy, documented: cleartext is only ever offered to
     * loopback and RFC 1918 private hosts — a local backend, the emulator
     * host loopback (10.0.2.2), or LAN IPs. Anything else must be HTTPS, and
     * release builds additionally deny cleartext outside the narrow
     * network-security-config exception.
     */
    fun isDevHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost" || h == "::1") {
            return true
        }
        val parts = h.split(".").map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) {
            return false
        }
        val (a, b) = parts
        return a == 127 || a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    fun retrofit(baseUrl: String, tokens: TokenHolder): Retrofit {
        val normalized = when (val check = checkBaseUrl(baseUrl)) {
            is BaseUrlCheck.Valid -> check.normalized
            is BaseUrlCheck.Invalid -> throw InvalidBaseUrlException(check.problem)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
