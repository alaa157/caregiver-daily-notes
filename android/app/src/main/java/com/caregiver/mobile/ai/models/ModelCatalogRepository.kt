package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.ai.AiModelCatalog
import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.CachedModels

/** A fetched list is reused for a week before a refresh is attempted. */
const val MODEL_CACHE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

/** Where the models in a [ModelSnapshot] came from. */
enum class ModelSource { LIVE, CACHED, BUNDLED }

/** A model list plus its origin and the time it was fetched, when known. */
data class ModelSnapshot(
    val models: List<AiModelOption>,
    val source: ModelSource,
    val fetchedAtEpochMillis: Long?,
)

/**
 * Decides whether to show a live, cached or bundled list. A failed refresh
 * never discards a working cache, and neither a missing key nor a missing
 * source ever reaches the network.
 */
class ModelCatalogRepository(
    private val keyStore: AiKeyStore,
    private val sources: Map<AiProvider, ModelListSource>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun models(provider: AiProvider, forceRefresh: Boolean = false): ModelSnapshot {
        val apiKey = keyStore.apiKey(provider)
        if (apiKey.isNullOrBlank()) return bundled(provider)

        val baseUrl = keyStore.baseUrlOrDefault(provider)
        if (baseUrl.isBlank()) return bundled(provider)

        // Resolve the source before the cache: a provider without a live source
        // shows its bundled list even when an older build cached one.
        val source = sources[provider] ?: return bundled(provider)

        val cached = keyStore.cachedModels(provider)
        if (!forceRefresh && cached != null &&
            clock() - cached.fetchedAtEpochMillis < MODEL_CACHE_TTL_MILLIS
        ) {
            return ModelSnapshot(cached.models, ModelSource.CACHED, cached.fetchedAtEpochMillis)
        }

        val accountBefore = keyStore.activeAccountId()
        val result = source.fetch(baseUrl, apiKey)
        // A fetch can outlive an account switch. Never write the new account's
        // cache with the old account's result.
        if (keyStore.activeAccountId() != accountBefore) return bundled(provider)

        return when (result) {
            is ModelListResult.Ok -> {
                val models = enrich(provider, result.models)
                val fetchedAt = clock()
                keyStore.setCachedModels(provider, models, fetchedAt)
                ModelSnapshot(models, ModelSource.LIVE, fetchedAt)
            }
            is ModelListResult.Failed -> fromCacheOrBundled(provider, cached)
        }
    }

    /** Only ids the bundled list flags are recommended; nothing is guessed. */
    private fun enrich(provider: AiProvider, models: List<AiModelOption>): List<AiModelOption> {
        val recommended = AiModelCatalog.options(provider)
            .filter { it.recommendedForSummaries }
            .map { it.id }
            .toSet()
        return models.map { it.copy(recommendedForSummaries = it.id in recommended) }
    }

    private fun bundled(provider: AiProvider) = ModelSnapshot(
        models = AiModelCatalog.options(provider),
        source = ModelSource.BUNDLED,
        fetchedAtEpochMillis = null,
    )

    private fun fromCacheOrBundled(provider: AiProvider, cached: CachedModels?) =
        if (cached == null) {
            bundled(provider)
        } else {
            ModelSnapshot(cached.models, ModelSource.CACHED, cached.fetchedAtEpochMillis)
        }
}

/**
 * OpenRouter is the only provider with a live model list for now. The other
 * providers are intentionally bundled until a later issue; their source
 * classes stay here for that work.
 */
fun defaultModelSources(): Map<AiProvider, ModelListSource> = mapOf(
    AiProvider.OPEN_ROUTER to OpenAiCompatibleModelListSource(AiProvider.OPEN_ROUTER),
)
