package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Hard cap on a fetched list, before it reaches the settings screen. */
private const val MAX_MODELS = 1000

/**
 * OpenAI and xAI keep their historical cap: their path stays unchanged even
 * though OpenRouter needs more headroom for its full catalogue.
 */
private const val LEGACY_MAX_MODELS = 500

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
    val architecture: OpenRouterArchitecture? = null,
)

/** OpenRouter's per-model pricing; a missing object means "not free". */
@Serializable
private data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null,
)

/** OpenRouter's capabilities; only text output is usable for a summary. */
@Serializable
private data class OpenRouterArchitecture(
    val output_modalities: List<String> = emptyList(),
)

/**
 * Lists models for the three providers serving the OpenAI `data[].id` shape:
 * OpenAI, xAI and OpenRouter. The provider argument selects the endpoints and
 * the parsing rule; OpenAI and xAI share the original id-based filter, while
 * OpenRouter uses the capabilities in the response itself.
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

        val models = when (provider) {
            AiProvider.OPEN_ROUTER -> parseOpenRouter(payload.data)
            else -> parseOpenAiCompatible(payload.data)
        }

        return if (models.isEmpty()) {
            ModelListResult.Failed("empty")
        } else {
            ModelListResult.Ok(models)
        }
    }

    /** The original OpenAI/xAI path: id-based chat filter, never free. */
    private fun parseOpenAiCompatible(entries: List<OpenAiModelEntry>): List<AiModelOption> {
        val seen = HashSet<String>()
        return entries.mapNotNull { entry ->
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
            .take(LEGACY_MAX_MODELS)
    }

    /**
     * OpenRouter decides chat eligibility from `architecture.output_modalities`,
     * drops batch-only variants, and treats a model as free only when both
     * prices parse to zero (or the id is an explicit `:free` variant).
     */
    private fun parseOpenRouter(entries: List<OpenAiModelEntry>): List<AiModelOption> {
        val seen = HashSet<String>()
        return entries.mapNotNull { entry ->
            val id = entry.id
            if (id.isBlank() ||
                id.contains(":batch") ||
                entry.architecture?.output_modalities != listOf("text") ||
                !seen.add(id)
            ) {
                null
            } else {
                val prompt = entry.pricing?.prompt?.toDoubleOrNull()
                val completion = entry.pricing?.completion?.toDoubleOrNull()
                AiModelOption(
                    id = id,
                    free = id.endsWith(":free") || (prompt == 0.0 && completion == 0.0),
                )
            }
        }
            .sortedBy { it.id }
            .take(MAX_MODELS)
    }
}
