package com.caregiver.mobile.ai

import kotlinx.coroutines.CoroutineDispatcher
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiCompatibleLlmClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val okHttpClient: OkHttpClient = OkHttpConfig.defaultOkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxTokens: Int = 1024,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): LlmResult = withContext(ioDispatcher) {
        val httpUrl = baseUrl.toHttpUrlOrNull()
        if (httpUrl == null || httpUrl.host.isEmpty()) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        val isLoopback = httpUrl.host == "localhost" || httpUrl.host == "127.0.0.1"
        if (httpUrl.scheme == "http" && !isLoopback) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (httpUrl.scheme !in listOf("http", "https")) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (apiKey.isBlank() || apiKey.any { it.isISOControl() }) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_key", null)
        }
        val basePath = httpUrl.encodedPath
            .removeSuffix("/chat/completions")
            .removeSuffix("/chat/completions/")
            .ifEmpty { "/" }
        val normalizedUrl = httpUrl.newBuilder().encodedPath(if (basePath.endsWith("/")) basePath else "$basePath/").build()
        val url = normalizedUrl.newBuilder().addPathSegments("chat/completions").build().toString()

        // Native json mode is opt-in per request. A server that does not
        // implement `response_format` answers with a 400; the request is then
        // retried without the parameter and the shared sanitizer plus the
        // repair retry take over.
        var useJsonMode = request.jsonMode
        var result: LlmResult
        while (true) {
            result = send(request, url, buildBody(request, useJsonMode))
            if (useJsonMode && result.error == JSON_MODE_UNSUPPORTED) {
                useJsonMode = false
                continue
            }
            break
        }
        return@withContext result
    }

    private fun buildBody(request: LlmRequest, jsonMode: Boolean): JsonObject {
        val systemPrompt: String
        val dataBlock: String
        if (jsonMode) {
            // OpenAI rejects a json-mode request whose messages never mention
            // json, so the word is added when the prompt does not carry it.
            val messages = request.systemPrompt + "\n" + request.dataBlock
            systemPrompt = request.systemPrompt
            dataBlock = if (messages.contains("json")) {
                request.dataBlock
            } else {
                request.dataBlock + "\n\nReply with only json."
            }
        } else {
            systemPrompt = request.systemPrompt
            dataBlock = request.dataBlock
        }
        return buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                add(buildJsonObject { put("role", "user"); put("content", dataBlock) })
            }
            if (baseUrl.contains("api.openai.com", ignoreCase = true)) {
                put("max_completion_tokens", maxTokens)
            } else {
                put("max_tokens", maxTokens)
            }
            put("temperature", 0.2)
            if (jsonMode) {
                // Ask the provider for a json reply instead of parsing prose.
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
        }
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
                    .addHeader("Authorization", "Bearer $apiKey")
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
                        val err = when (code) {
                            401, 403 -> "invalid_key"
                            429 -> "rate_limit"
                            else -> "http_$code"
                        }
                        last = LlmResult(LlmStatus.FALLBACK, "", err, code)
                        if (code == 401 || code == 403) {
                            return last
                        }
                        if (code == 400 && raw.contains("response_format", ignoreCase = true)) {
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
                    val choice = root?.get("choices")?.jsonArray?.firstOrNull()?.jsonObject
                    val content = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                    val finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
                    if (content.isNullOrBlank()) {
                        last = LlmResult(LlmStatus.FALLBACK, "", "empty_output", resp.code)
                        if (attempt < 2) kotlinx.coroutines.delay(calculateBackoff(attempt))
                        return@use
                    }
                    if (finishReason == "length" || finishReason == "max_tokens") {
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
