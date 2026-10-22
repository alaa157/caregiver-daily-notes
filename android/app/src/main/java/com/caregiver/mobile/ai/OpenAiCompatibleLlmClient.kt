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
    private val maxTokens: Int = DEFAULT_TOKEN_BUDGET,
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

        // Three provider quirks are handled by adapting the request instead
        // of guessing which models have them:
        //  - a model that refuses a custom temperature answers 400; the
        //    request is retried without the parameter.
        //  - a server that does not implement the native json parameter
        //    answers 400; the request is retried without it.
        //  - a reply that was cut short, or that came back empty because the
        //    budget went on the model's own reasoning, is retried with a
        //    larger budget. Asking again with the same budget fails the
        //    same way.
        var useJsonMode = request.jsonMode
        var useTemperature = true
        var budget = maxTokens
        var result: LlmResult
        while (true) {
            result = send(request, url, buildBody(request, useJsonMode, useTemperature, budget))
            if (useJsonMode && result.error == JSON_MODE_UNSUPPORTED) {
                useJsonMode = false
                continue
            }
            if (useTemperature && result.error == TEMPERATURE_UNSUPPORTED) {
                useTemperature = false
                continue
            }
            if (result.error == "truncated" || result.error == "empty_output") {
                if (budget < MAX_TOKEN_BUDGET) {
                    budget = (budget * 2).coerceAtMost(MAX_TOKEN_BUDGET)
                    continue
                }
            }
            break
        }
        // The markers are for the retry above, not for the UI: a provider
        // that keeps refusing the same request has to look like the plain
        // bad request it is.
        if (result.error == JSON_MODE_UNSUPPORTED || result.error == TEMPERATURE_UNSUPPORTED) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "http_400", result.httpCode)
        }
        return@withContext result
    }

    private fun buildBody(
        request: LlmRequest,
        jsonMode: Boolean,
        temperature: Boolean,
        budget: Int,
    ): JsonObject {
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
                put("max_completion_tokens", budget)
            } else {
                put("max_tokens", budget)
            }
            if (temperature) {
                // A low temperature keeps the prose plain. Models that refuse
                // a custom one answer 400 and the request is retried without
                // it, so the parameter never decides whether a summary works.
                put("temperature", 0.2)
            }
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
                        val err = classifyLlmHttpError(code, raw)
                        last = LlmResult(LlmStatus.FALLBACK, "", err, code)
                        if (code == 401 || code == 403) {
                            return last
                        }
                        if (code == 400 && raw.contains("response_format", ignoreCase = true)) {
                            // The endpoint does not know the native json
                            // parameter; the caller retries without it.
                            return LlmResult(LlmStatus.FALLBACK, "", JSON_MODE_UNSUPPORTED, code)
                        }
                        if (code == 400 && raw.contains("temperature", ignoreCase = true)) {
                            // The model refuses a custom temperature; the
                            // caller retries without it. Which models do
                            // this changes with every release, so the answer
                            // is what decides, not a list of model ids.
                            return LlmResult(LlmStatus.FALLBACK, "", TEMPERATURE_UNSUPPORTED, code)
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
                        // An empty answer is usually a model that spent the
                        // whole budget on its own reasoning. It comes back
                        // to the caller, which asks again with a larger
                        // budget; repeating the same request with the same
                        // budget would fail the same way.
                        return LlmResult(LlmStatus.FALLBACK, "", "empty_output", resp.code)
                    }
                    if (finishReason == "length" || finishReason == "max_tokens") {
                        // The answer was cut off at the token limit. Asking
                        // again with the same budget cuts it off again, so
                        // the caller raises the budget instead.
                        return LlmResult(LlmStatus.FALLBACK, "", "truncated", resp.code)
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
