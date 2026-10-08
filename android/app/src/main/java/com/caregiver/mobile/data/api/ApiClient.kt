package com.caregiver.mobile.data.api

import com.caregiver.mobile.core.network.AuthInterceptor
import com.caregiver.mobile.core.network.TokenHolder
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Builds the Retrofit client. The base URL is read fresh for every service
 * lookup so the Task 7 server-URL setting takes effect without a restart;
 * unknown JSON fields are ignored so additive backend changes don't crash
 * old builds.
 */
object ApiClient {
    val json: Json = Json { ignoreUnknownKeys = true }

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    fun retrofit(baseUrl: String, tokens: TokenHolder): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .build()
        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
