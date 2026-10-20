package com.caregiver.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model suggestions the settings screen offers. A provider that serves
 * free models has to say so, and the provider default is always one of the
 * suggestions — otherwise the field would show a model the app would not
 * suggest.
 */
class AiModelCatalogTest {
    @Test
    fun everyProviderOffersModels() {
        for (provider in AiProvider.entries) {
            assertTrue(
                "${provider.name} offers no models",
                AiModelCatalog.options(provider).isNotEmpty(),
            )
        }
    }

    @Test
    fun theProviderDefaultIsOneOfTheSuggestions() {
        for (provider in AiProvider.entries) {
            assertTrue(
                "${provider.defaultModel} is not offered for ${provider.name}",
                AiModelCatalog.options(provider).any { it.id == provider.defaultModel },
            )
        }
    }

    @Test
    fun suggestionsAreUnique() {
        for (provider in AiProvider.entries) {
            val ids = AiModelCatalog.options(provider).map { it.id }
            assertEquals(
                "duplicate suggestions for ${provider.name}",
                ids.size,
                ids.distinct().size,
            )
        }
    }

    @Test
    fun aProviderWithFreeModelsSaysSo() {
        for (provider in AiProvider.entries) {
            if (AiModelCatalog.options(provider).any { it.free }) {
                assertEquals(provider.name, AiFreeTier.FREE_MODELS, provider.freeTier)
            }
        }
    }

    @Test
    fun aProviderWithFreeModelsDefaultsToTheFreeOne() {
        for (provider in AiProvider.entries) {
            val free = AiModelCatalog.options(provider).firstOrNull { it.free } ?: continue
            assertEquals(
                "${provider.name} should default to its free model",
                free.id,
                provider.defaultModel,
            )
        }
    }

    @Test
    fun aProviderWithoutFreeModelsDoesNotPretendToHaveOne() {
        for (provider in AiProvider.entries) {
            if (provider.freeTier == AiFreeTier.FREE_MODELS) continue
            assertTrue(
                "${provider.name} offers a free model it does not have",
                AiModelCatalog.options(provider).none { it.free },
            )
        }
    }

    @Test
    fun modelIdsAreNotBlank() {
        for (provider in AiProvider.entries) {
            for (option in AiModelCatalog.options(provider)) {
                assertTrue(option.id.isNotBlank())
                assertEquals(option.id, option.id.trim())
            }
        }
    }
}
