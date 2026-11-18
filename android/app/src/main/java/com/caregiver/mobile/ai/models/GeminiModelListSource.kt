package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Lists the chat models Gemini actually serves from `/v1beta/models`.
 *
 * Gemini differs from the OpenAI-compatible providers in three ways: the API
 * key goes in `x-goog-api-key`, model ids carry a `models/` prefix, and
 * capability is expressed by `supportedGenerationMethods` rather than by the
 * id. Pagination uses `nextPageToken`.
 */
class GeminiModelListSource(
    private val http: ModelListHttp = ModelListHttp(),
) : ModelListSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(baseUrl: String, apiKey: String): ModelListResult {
        val root = normalizeBaseUrl(baseUrl)
        if (root.isBlank()) return ModelListResult.Failed("invalid_url")

        val headers = mapOf(
            "x-goog-api-key" to apiKey,
            "Accept" to "application/json",
        )
        val ids = LinkedHashSet<String>()
        var pageToken: String? = null
        var pages = 0

        while (pages < 5) {
            val token = pageToken
            val query = if (token == null) {
                "?pageSize=200"
            } else {
                "?pageSize=200&pageToken=${URLEncoder.encode(token, "UTF-8")}"
            }
            when (val response = http.get("$root/v1beta/models$query", headers)) {
                is HttpJson.Failed -> return ModelListResult.Failed(response.reasonCode)
                is HttpJson.Ok -> {
                    val payload = runCatching { json.parseToJsonElement(response.body).jsonObject }
                        .getOrNull() ?: return ModelListResult.Failed("malformed")
                    val models = runCatching { payload["models"]?.jsonArray }
                        .getOrNull() ?: return ModelListResult.Failed("malformed")

                    for (element in models) {
                        val name = stringField(element, "name") ?: continue
                        val generates = runCatching {
                            element.jsonObject["supportedGenerationMethods"]
                                ?.jsonArray
                                ?.any { it.jsonPrimitive.contentOrNull == "generateContent" }
                        }.getOrNull() ?: false
                        if (!generates) continue
                        val id = name.removePrefix("models/")
                        if (id.isNotBlank() && isChatModel(AiProvider.GEMINI, id)) ids.add(id)
                    }

                    pageToken = stringField(payload, "nextPageToken")?.takeIf { it.isNotBlank() }
                    pages++
                    if (pageToken == null) break
                }
            }
        }

        if (ids.isEmpty()) return ModelListResult.Failed("empty")
        val options = ids.sorted().take(500).map {
            AiModelOption(id = it, jsonMode = true, free = false)
        }
        return ModelListResult.Ok(options)
    }

    private fun stringField(element: JsonElement, key: String): String? =
        runCatching { element.jsonObject[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}
