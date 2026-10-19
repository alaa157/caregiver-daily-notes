package com.caregiver.mobile.presentation.settings

import com.caregiver.mobile.ai.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding 8: the settings screen must not save an empty or invalid
 * configuration. Blank means "provider default" (accepted); a non-blank value
 * that cannot work is rejected and shown to the user.
 */
class AiSettingsValidationTest {

    @Test
    fun blankBaseUrlMeansProviderDefault() {
        assertTrue(AiSettingsValidation.isAcceptableBaseUrl(""))
        assertTrue(AiSettingsValidation.isAcceptableBaseUrl("   "))
    }

    @Test
    fun everyProviderDefaultIsAccepted() {
        AiProvider.values().forEach { provider ->
            assertTrue(
                "default base URL for ${provider.name} must validate: ${provider.defaultBaseUrl}",
                AiSettingsValidation.isAcceptableBaseUrl(provider.defaultBaseUrl),
            )
        }
    }

    @Test
    fun invalidBaseUrlsAreRejected() {
        assertFalse(AiSettingsValidation.isAcceptableBaseUrl("api.example.com"))
        assertFalse(AiSettingsValidation.isAcceptableBaseUrl("ftp://api.example.com"))
        assertFalse(AiSettingsValidation.isAcceptableBaseUrl("https://"))
        assertFalse(AiSettingsValidation.isAcceptableBaseUrl("https:///v1"))
        assertFalse(AiSettingsValidation.isAcceptableBaseUrl("https://exa mple.com/v1"))
    }

    @Test
    fun validCustomBaseUrlsAreAccepted() {
        assertTrue(AiSettingsValidation.isAcceptableBaseUrl("https://api.example.com/v1"))
        assertTrue(AiSettingsValidation.isAcceptableBaseUrl("http://192.168.0.10:8080/v1"))
        assertTrue(AiSettingsValidation.isAcceptableBaseUrl("  https://proxy.example.com  "))
    }

    @Test
    fun blankModelIsStoredBlankSoDefaultsCanChange() {
        // Same convention as the base URL below: blank means "use the
        // provider default"; the read side resolves it against the current
        // default (covered by SummaryViewModelModelResolutionTest).
        assertEquals("", AiSettingsValidation.normalizedModel("  ", AiProvider.OPENAI))
        assertEquals("custom-model", AiSettingsValidation.normalizedModel(" custom-model ", AiProvider.OPENAI))
    }

    @Test
    fun providerDefaultBaseUrlIsStoredAsBlankSoDefaultsCanChange() {
        assertEquals("", AiSettingsValidation.normalizedBaseUrl("  ${AiProvider.OPENAI.defaultBaseUrl}  ", AiProvider.OPENAI))
        assertEquals("", AiSettingsValidation.normalizedBaseUrl("", AiProvider.OPENAI))
        assertEquals(
            "https://proxy.example.com/v1",
            AiSettingsValidation.normalizedBaseUrl(" https://proxy.example.com/v1 ", AiProvider.OPENAI),
        )
    }
}
