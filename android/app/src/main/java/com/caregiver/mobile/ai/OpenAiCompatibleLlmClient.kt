package com.caregiver.mobile.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class ChatMessage(val role: String, val content: String)

class OpenAiCompatibleLlmClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val okHttpClient: OkHttpClient = com.caregiver.mobile.core.network.OkHttpConfig.defaultOkHttpClient(),
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
        if (httpUrl.scheme == "http" && !com.caregiver.mobile.data.api.ApiClient.isDevHost(httpUrl.host)) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (httpUrl.scheme !in listOf("http", "https")) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (apiKey.isBlank() || apiKey.any { it.isWhitespace() || it.isISOControl() }) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_key", null)
        }
        val basePath = httpUrl.encodedPath
            .removeSuffix("/chat/completions")
            .removeSuffix("/chat/completions/")
            .ifEmpty { "/" }
        val normalizedUrl = try {
            httpUrl.newBuilder().encodedPath(if (basePath.endsWith("/")) basePath else "$basePath/").build()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        val url = try {
            normalizedUrl.newBuilder().addPathSegments("chat/completions").build().toString()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        val finalHttpUrl = url.toHttpUrlOrNull()
        if (finalHttpUrl == null || finalHttpUrl.host.isEmpty() ||
            (finalHttpUrl.scheme == "http" && !com.caregiver.mobile.data.api.ApiClient.isDevHost(finalHttpUrl.host)) ||
            finalHttpUrl.scheme !in listOf("http", "https")) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        val safeUrl = finalHttpUrl.toString()
        val isOpenAi = baseUrl.contains("api.openai.com", ignoreCase = true)
        val bodyObj = try {
            buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    add(buildJsonObject { put("role", "system"); put("content", request.systemPrompt) })
                    add(buildJsonObject { put("role", "user"); put("content", request.dataBlock) })
                }
                if (isOpenAi) {
                    put("max_completion_tokens", maxTokens)
                } else {
                    put("max_tokens", maxTokens)
                }
                put("temperature", 0.2)
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
        var last: LlmResult = LlmResult(LlmStatus.FALLBACK, "", "network", null)
        repeat(3) { attempt ->
            try {
                val httpReq = Request.Builder()
                    .url(safeUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(reqBody)
                    .build()
                val call = okHttpClient.newCall(httpReq)
                coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause -> if (cause is kotlinx.coroutines.CancellationException) call.cancel() }
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
                            return@withContext last
                        }
                        if (code == 429 || code >= 500) {
                            if (attempt < 2) kotlinx.coroutines.delay(calculateBackoff(attempt))
                            return@use
                        }
                        return@withContext last
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
                    return@withContext LlmResult(LlmStatus.OK, content)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = LlmResult(LlmStatus.FALLBACK, "", "network", null)
                if (attempt < 2) kotlinx.coroutines.delay(calculateBackoff(attempt))
            }
        }
        return@withContext last
    }

    fun calculateBackoff(attempt: Int, baseMs: Long = BACKOFF_BASE_MS): Long {
        return baseMs * (1L shl attempt.coerceAtMost(4))
    }

    companion object {
        const val BACKOFF_BASE_MS = 500L
    }
}
