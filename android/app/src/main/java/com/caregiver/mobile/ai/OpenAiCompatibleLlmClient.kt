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
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxTokens: Int = 1024,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val bodyObj = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", request.systemPrompt) })
                add(buildJsonObject { put("role", "user"); put("content", request.dataBlock) })
            }
            put("max_tokens", maxTokens)
            put("temperature", 0.2)
        }
        val reqBody = json.encodeToString(JsonObject.serializer(), bodyObj)
            .toRequestBody("application/json".toMediaType())
        var last: LlmResult = LlmResult(LlmStatus.FALLBACK, "", "network", null)
        repeat(3) { attempt ->
            val httpReq = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(reqBody)
                .build()
            try {
                okHttpClient.newCall(httpReq).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val code = resp.code
                        val err = when (code) {
                            401, 403 -> "invalid_key"
                            429 -> "rate_limit"
                            else -> "http_$code"
                        }
                        last = LlmResult(LlmStatus.FALLBACK, "", err, code)
                        if (code >= 500) {
                            kotlinx.coroutines.delay(1000L * (attempt + 1))
                            return@use
                        }
                        return@withContext last
                    }
                    val content = try {
                        val root = json.parseToJsonElement(raw).jsonObject
                        root["choices"]?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("message")?.jsonObject
                            ?.get("content")?.jsonPrimitive?.contentOrNull
                    } catch (e: Exception) {
                        null
                    }
                    if (content.isNullOrBlank()) {
                        last = LlmResult(LlmStatus.FALLBACK, "", "empty_output", resp.code)
                        kotlinx.coroutines.delay(1000L * (attempt + 1))
                        return@use
                    }
                    return@withContext LlmResult(LlmStatus.OK, content)
                }
            } catch (e: Exception) {
                last = LlmResult(LlmStatus.FALLBACK, "", "network", null)
                kotlinx.coroutines.delay(1000L * (attempt + 1))
            }
        }
        return@withContext last
    }
}
