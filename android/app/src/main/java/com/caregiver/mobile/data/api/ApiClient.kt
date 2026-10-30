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
     * Cleartext hosts, mirroring `res/xml/network_security_config.xml`.
     *
     * Keep this list and that file in sync. A host accepted here but absent
     * there passes validation and is then blocked by the platform, which
     * surfaces as a generic "network" failure instead of a base-URL error.
     * LAN ranges are deliberately absent: the security config cannot express
     * a CIDR range, and permitting arbitrary LAN cleartext would be a
     * downgrade for every build.
     */
    private val CLEARTEXT_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /** True only for hosts Android permits cleartext traffic to. */
    fun isDevHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        return h in CLEARTEXT_HOSTS
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
