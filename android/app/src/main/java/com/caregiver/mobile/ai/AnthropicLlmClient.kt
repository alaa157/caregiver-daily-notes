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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicLlmClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxTokens: Int = 1024,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/v1/messages"
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
        val httpReq = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(reqBody)
            .build()
        try {
            okHttpClient.newCall(httpReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val code = resp.code
                    val err = when (code) { 401, 403 -> "invalid_key"; 429 -> "rate_limit"; else -> "http_$code" }
                    return@withContext LlmResult(LlmStatus.FALLBACK, "", err, code)
                }
                val content = try {
                    val root = json.parseToJsonElement(raw).jsonObject
                    root["content"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                } catch (e: Exception) { null }
                if (content.isNullOrBlank()) {
                    return@withContext LlmResult(LlmStatus.FALLBACK, "", "empty_output", resp.code)
                }
                LlmResult(LlmStatus.OK, content)
            }
        } catch (e: Exception) {
            LlmResult(LlmStatus.FALLBACK, "", "network", null)
        }
    }
}
