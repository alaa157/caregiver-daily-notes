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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicLlmClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val okHttpClient: OkHttpClient = com.caregiver.mobile.core.network.OkHttpConfig.defaultOkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxTokens: Int = 1024,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim()
        val httpUrl = cleanBase.toHttpUrlOrNull()
        if (httpUrl == null || httpUrl.host.isEmpty() || httpUrl.scheme !in listOf("http", "https")) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_url", null)
        }
        if (apiKey.any { it.isISOControl() }) {
            return@withContext LlmResult(LlmStatus.FALLBACK, "", "invalid_key", null)
        }
        val cleanKey = apiKey.trim()
        val url = cleanBase.trimEnd('/') + "/v1/messages"
        val bodyObj = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", request.systemPrompt)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "user"); put("content", request.dataBlock) })
            }
        }
        val reqBody = json.encodeToString(JsonObject.serializer(), bodyObj)
            .toRequestBody("application/json".toMediaType())
        var last: LlmResult = LlmResult(LlmStatus.FALLBACK, "", "network", null)
        repeat(3) { attempt ->
            try {
                val httpReq = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-api-key", cleanKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(reqBody)
                    .build()
                okHttpClient.newCall(httpReq).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val code = resp.code
                        val err = when (code) { 401, 403 -> "invalid_key"; 429 -> "rate_limit"; else -> "http_$code" }
                        last = LlmResult(LlmStatus.FALLBACK, "", err, code)
                        if (code >= 500) {
                            kotlinx.coroutines.delay(calculateBackoff(attempt))
                            return@use
                        }
                        return@withContext last
                    }
                    val content = try {
                        val root = json.parseToJsonElement(raw).jsonObject
                        root["content"]?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                    } catch (e: Exception) { null }
                    if (content.isNullOrBlank()) {
                        last = LlmResult(LlmStatus.FALLBACK, "", "empty_output", resp.code)
                        kotlinx.coroutines.delay(calculateBackoff(attempt))
                        return@use
                    }
                    return@withContext LlmResult(LlmStatus.OK, content)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = LlmResult(LlmStatus.FALLBACK, "", "network", null)
                kotlinx.coroutines.delay(calculateBackoff(attempt))
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
