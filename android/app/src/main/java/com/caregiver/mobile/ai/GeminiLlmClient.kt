package com.caregiver.mobile.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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

class GeminiLlmClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val okHttpClient: OkHttpClient = OkHttpConfig.defaultOkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxTokens: Int = 4096,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): LlmResult = withContext(ioDispatcher) {
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
        val url = try {
            httpUrl.newBuilder()
                .addPathSegment("v1beta")
                .addPathSegment("models")
                .addPathSegment("$model:generateContent")
                .build().toString()
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

        // Native json mode is opt-in per request. A proxy that does not
        // implement `responseMimeType` answers with a 400; the request is then
        // retried without the parameter and the shared sanitizer plus the
        // repair retry take over.
        var useJsonMode = request.jsonMode
        var result: LlmResult
        while (true) {
            result = send(request, safeUrl, buildBody(request, useJsonMode))
            if (useJsonMode && result.error == JSON_MODE_UNSUPPORTED) {
                useJsonMode = false
                continue
            }
            break
        }
        return@withContext result
    }

    private fun buildBody(request: LlmRequest, jsonMode: Boolean): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", request.dataBlock) })
                }
            })
        }
        put("systemInstruction", buildJsonObject {
            putJsonArray("parts") {
                add(buildJsonObject { put("text", request.systemPrompt) })
            }
        })
        put("generationConfig", buildJsonObject {
            put("maxOutputTokens", maxTokens)
            put("temperature", 0.2)
            if (jsonMode) {
                // Ask Gemini for a json reply instead of parsing prose.
                put("responseMimeType", "application/json")
            }
        })
    }

    private suspend fun send(request: LlmRequest, url: String, bodyObj: JsonObject): LlmResult {
        val reqBody = try {
            json.encodeToString(JsonObject.serializer(), bodyObj)
                .toRequestBody("application/json".toMediaType())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return LlmResult(LlmStatus.FALLBACK, "", "invalid_request", null)
        }
        val cancellation = CallCancellation()
        cancellation.install()
        try {
            return attempt(url, reqBody, request, cancellation)
        } finally {
            cancellation.close()
        }
    }

    private suspend fun attempt(
        url: String,
        reqBody: RequestBody,
        request: LlmRequest,
        cancellation: CallCancellation,
    ): LlmResult {
        var last: LlmResult = LlmResult(LlmStatus.FALLBACK, "", "network", null)
        repeat(3) { attempt ->
            try {
                val httpReq = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(reqBody)
                    .build()
                val call = okHttpClient.newCall(httpReq)
                if (request.timeoutSeconds > 0) {
                    call.timeout().timeout(request.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                }
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
                        if (code == 400 && raw.contains("responseMimeType", ignoreCase = true)) {
                            // The endpoint does not know the native json
                            // parameter; the caller retries without it.
                            return LlmResult(LlmStatus.FALLBACK, "", JSON_MODE_UNSUPPORTED, code)
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
                    // A prompt the safety filter refuses comes back without
                    // any candidate at all, and says why. Retrying it three
                    // times only wastes the caregiver's time and their quota.
                    val blockReason = root?.get("promptFeedback")?.jsonObject
                        ?.get("blockReason")?.jsonPrimitive?.contentOrNull
                    if (!blockReason.isNullOrBlank()) {
                        return LlmResult(LlmStatus.FALLBACK, "", BLOCKED, resp.code)
                    }
                    val candidate = root?.get("candidates")?.jsonArray?.firstOrNull()?.jsonObject
                    val finishReason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
                    if (finishReason in BLOCKED_FINISH_REASONS) {
                        // The model refused on safety grounds: notes about a
                        // fall or pain can trip the filter. Retrying the same
                        // prompt cannot help.
                        return LlmResult(LlmStatus.FALLBACK, "", BLOCKED, resp.code)
                    }
                    // A thinking model answers with a thought part first and
                    // the answer after it, and a reply can be split over
                    // several parts. Reading only the first part drops the
                    // answer (or the reasoning that precedes it).
                    val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    val content = parts?.mapNotNull { part ->
                        val obj = part.jsonObject
                        if (obj["thought"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
                        obj["text"]?.jsonPrimitive?.contentOrNull
                    }?.joinToString("")?.takeIf { it.isNotBlank() }
                    if (content.isNullOrBlank()) {
                        last = LlmResult(LlmStatus.FALLBACK, "", "empty_output", resp.code)
                        if (attempt < 2) kotlinx.coroutines.delay(calculateBackoff(attempt))
                        return@use
                    }
                    if (finishReason == "MAX_TOKENS") {
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

        /**
         * The provider refused the request on safety grounds. A note about a
         * fall or pain can trip the filter, so this is a distinct answer,
         * not a failure to answer.
         */
        const val BLOCKED = "blocked_safety"

        /** Finish reasons that mean the provider refused, not that it failed. */
        val BLOCKED_FINISH_REASONS = setOf(
            "SAFETY",
            "RECITATION",
            "BLOCKLIST",
            "PROHIBITED_CONTENT",
            "SPII",
            "IMAGE_SAFETY",
            "IMAGE_PROHIBITED_CONTENT",
        )
    }
}
