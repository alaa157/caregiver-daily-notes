package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Hard cap on a fetched list, before it reaches the settings screen. */
private const val MAX_MODELS = 500

private val SUPPORTED_PROVIDERS = setOf(
    AiProvider.OPENAI,
    AiProvider.XAI,
    AiProvider.OPEN_ROUTER,
)

@Serializable
private data class ModelListPayload(
    val data: List<OpenAiModelEntry> = emptyList(),
)

@Serializable
private data class OpenAiModelEntry(
    val id: String = "",
    val pricing: OpenRouterPricing? = null,
)

/** OpenRouter's per-model pricing; a missing object means "not free". */
@Serializable
private data class OpenRouterPricing(
    val prompt: String? = null,
)

/**
 * Lists models for the three providers serving the OpenAI `data[].id` shape:
 * OpenAI, xAI and OpenRouter. The provider argument selects the endpoints and
 * the id filter; the parsing and sorting is shared.
 */
class OpenAiCompatibleModelListSource(
    private val provider: AiProvider,
    private val http: ModelListHttp = ModelListHttp(),
) : ModelListSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(baseUrl: String, apiKey: String): ModelListResult {
        if (provider !in SUPPORTED_PROVIDERS) {
            return ModelListResult.Failed("unsupported_provider")
        }

        val root = normalizeBaseUrl(baseUrl)
        if (root.isBlank()) return ModelListResult.Failed("invalid_url")

        val body = when (val response = http.get(
            "$root/models",
            mapOf("Authorization" to "Bearer $apiKey", "Accept" to "application/json"),
        )) {
            is HttpJson.Ok -> response.body
            // The helper's reason code is already the one callers expect.
            is HttpJson.Failed -> return ModelListResult.Failed(response.reasonCode)
        }

        val payload = runCatching { json.decodeFromString<ModelListPayload>(body) }.getOrNull()
            ?: return ModelListResult.Failed("malformed")

        val seen = HashSet<String>()
        val models = payload.data.mapNotNull { entry ->
            val id = entry.id
            if (id.isBlank() || !isChatModel(provider, id) || !seen.add(id)) {
                null
            } else {
                AiModelOption(
                    id = id,
                    free = provider == AiProvider.OPEN_ROUTER &&
                        (id.endsWith(":free") || entry.pricing?.prompt == "0"),
                )
            }
        }
            .sortedBy { it.id }
            .take(MAX_MODELS)

        return if (models.isEmpty()) {
            ModelListResult.Failed("empty")
        } else {
            ModelListResult.Ok(models)
        }
    }
}
