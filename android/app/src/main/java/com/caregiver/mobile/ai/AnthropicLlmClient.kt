package com.caregiver.mobile.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import com.caregiver.mobile.core.network.OkHttpConfig
import com.caregiver.mobile.data.api.ApiClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicLlmClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val okHttpClient: OkHttpClient = OkHttpConfig.defaultOkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxTokens: Int = 1024,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        val httpUrl = baseUrl.toHttpUrlOrNull()
        if (httpUrl == null || httpUrl.host.isEmpty()) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (httpUrl.query != null || httpUrl.fragment != null ||
            httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (httpUrl.scheme == "http" && !ApiClient.isDevHost(httpUrl.host)) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (httpUrl.scheme !in listOf("http", "https")) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (apiKey.isBlank() || apiKey.any { it.isWhitespace() || it.isISOControl() }) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_key", null)
        }
        val normalizedUrl = try {
            if (httpUrl.encodedPath.endsWith("/v1") || httpUrl.encodedPath.endsWith("/v1/")) {
                httpUrl.newBuilder().encodedPath(httpUrl.encodedPath.removeSuffix("/").removeSuffix("/v1") + "/").build()
            } else {
                httpUrl
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        val url = try {
            normalizedUrl.newBuilder().addPathSegments("v1/messages").build().toString()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        val finalHttpUrl = url.toHttpUrlOrNull()
        if (finalHttpUrl == null || finalHttpUrl.host.isEmpty() ||
            (finalHttpUrl.scheme == "http" && !ApiClient.isDevHost(finalHttpUrl.host)) ||
            finalHttpUrl.scheme !in listOf("http", "https")) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        val safeUrl = finalHttpUrl.toString()
        val bodyObj = try {
            buildJsonObject {
                put("model", model)
                put("max_tokens", maxTokens)
                put("system", request.systemPrompt)
                putJsonArray("messages") {
                    add(buildJsonObject { put("role", "user"); put("content", request.dataBlock) })
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_request", null)
        }
        val reqBody = try {
            json.encodeToString(JsonObject.serializer(), bodyObj)
                .toRequestBody("application/json".toMediaType())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_request", null)
        }
        val cancellation = CallCancellation()
        cancellation.install()
        try {
            return@withContext attempt(safeUrl, reqBody, cancellation)
        } finally {
            cancellation.close()
        }
    }

    private suspend fun attempt(
        url: String,
        reqBody: RequestBody,
        cancellation: CallCancellation,
    ): LlmResult {
        var last: LlmResult = LlmResult(LlmStatus.FALLBACK, "", "network", null)
        repeat(3) { attempt ->
            try {
                val httpReq = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(reqBody)
                    .build()
                val call = okHttpClient.newCall(httpReq)
                cancellation.track(call)
                call.execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val code = resp.code
                        val err = when (code) { 401, 403 -> "invalid_key"; 429 -> "rate_limit"; else -> "http_$code" }
                        last = LlmResult(LlmStatus.FALLBACK, "", err, code)
                        if (code == 401 || code == 403) {
                            return last
                        }
                        // 429 surfaces immediately: auto-retrying quota errors
                        // inside a user-triggered request stalls the UI in
                        // real backoff; the app shows "rate_limit" and the
                        // user retries when ready. 5xx still retries.
                        if (code >= 500) {
                            if (attempt < 2) kotlinx.coroutines.delay(calculateBackoff(attempt))
                            return@use
                        }
                        return last
                    }
                    val root = try { json.parseToJsonElement(raw).jsonObject } catch (e: Exception) { null }
                    val content = root?.get("content")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                    val stopReason = root?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                    if (content.isNullOrBlank()) {
                        last = LlmResult(LlmStatus.FALLBACK, "", "empty_output", resp.code)
                        if (attempt < 2) kotlinx.coroutines.delay(calculateBackoff(attempt))
                        return@use
                    }
                    if (stopReason == "max_tokens") {
                        last = LlmResult(LlmStatus.FALLBACK, "", "truncated", resp.code)
                        if (attempt < 2) kotlinx.coroutines.delay(calculateBackoff(attempt))
                        return@use
                    }
                    return LlmResult(LlmStatus.OK, content)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = LlmResult(LlmStatus.FALLBACK, "", "network", null)
                if (attempt < 2) kotlinx.coroutines.delay(calculateBackoff(attempt))
            }
        }
        return last
    }

    fun calculateBackoff(attempt: Int, baseMs: Long = BACKOFF_BASE_MS): Long {
        return baseMs * (1L shl attempt.coerceAtMost(4))
    }

    companion object {
        const val BACKOFF_BASE_MS = 500L
    }
}
