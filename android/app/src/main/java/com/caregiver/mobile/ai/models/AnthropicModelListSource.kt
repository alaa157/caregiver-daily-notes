package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

private const val PAGE_SIZE = 100
private const val MAX_PAGES = 5
private const val MAX_MODELS = 500
private const val ANTHROPIC_VERSION = "2023-06-01"

private val listingJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class AnthropicModelPage(
    val data: List<AnthropicModelId> = emptyList(),
    val has_more: Boolean = false,
    val last_id: String? = null,
)

@Serializable
private data class AnthropicModelId(val id: String)

/**
 * Lists the models served by Anthropic's `/v1/models` endpoint.
 *
 * Anthropic is not OpenAI-compatible: the key goes in `x-api-key` with an
 * `anthropic-version` header (never `Authorization`), and the list is cursor
 * paginated with `has_more` / `last_id`.
 */
class AnthropicModelListSource(
    private val http: ModelListHttp = ModelListHttp(),
) : ModelListSource {

    override suspend fun fetch(baseUrl: String, apiKey: String): ModelListResult {
        val root = normalizeBaseUrl(baseUrl)
        if (root.isEmpty()) return ModelListResult.Failed("invalid_url")

        val headers = mapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to ANTHROPIC_VERSION,
            "Accept" to "application/json",
        )

        val ids = LinkedHashSet<String>()
        var lastId: String? = null
        var pages = 0

        while (pages < MAX_PAGES) {
            val body = when (val response = http.get(modelListUrl(root, lastId), headers)) {
                is HttpJson.Failed -> return ModelListResult.Failed(response.reasonCode)
                is HttpJson.Ok -> response.body
            }

            val page = try {
                listingJson.decodeFromString<AnthropicModelPage>(body)
            } catch (e: SerializationException) {
                return ModelListResult.Failed("malformed")
            }

            page.data.map { it.id.trim() }
                .filter { it.isNotEmpty() && isChatModel(AiProvider.ANTHROPIC, it) }
                .forEach(ids::add)

            pages++
            if (!page.has_more) break
            lastId = page.last_id?.takeIf { it.isNotEmpty() } ?: break
        }

        if (ids.isEmpty()) return ModelListResult.Failed("empty")

        val models = ids.sorted()
            .take(MAX_MODELS)
            .map { AiModelOption(id = it, jsonMode = true) }
        return ModelListResult.Ok(models)
    }
}

/** First page asks for `limit=100`; later pages add the URL-encoded cursor. */
private fun modelListUrl(root: String, lastId: String?): String {
    val first = "$root/v1/models?limit=$PAGE_SIZE"
    if (lastId == null) return first
    return "$first&after_id=${URLEncoder.encode(lastId, "UTF-8")}"
}
