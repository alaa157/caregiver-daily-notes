package com.caregiver.mobile.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiKeyStoreModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: AiKeyStore

    @Before
    fun setUp() {
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AiKeyStore(context)
    }

    @Test
    fun noSavedModelReturnsNull() {
        assertNull(store.model())
    }

    @Test
    fun savedEmptyReturnsNull() {
        store.setModel("")
        assertNull(store.model())
    }

    @Test
    fun savedWhitespaceReturnsNull() {
        store.setModel("   ")
        assertNull(store.model())
    }

    @Test
    fun savedCustomReturnsModel() {
        store.setModel("custom-model")
        assertEquals("custom-model", store.model())
    }

    @Test
    fun modelsAreScopedByProvider() {
        store.setModel("openai-model", AiProvider.OPENAI)
        store.setModel("gemini-model", AiProvider.GEMINI)

        assertEquals("openai-model", store.model(AiProvider.OPENAI))
        assertEquals("gemini-model", store.model(AiProvider.GEMINI))
        assertNull(store.model(AiProvider.ANTHROPIC))
    }

    @Test
    fun baseUrlsAreScopedByProvider() {
        store.setBaseUrl("https://openai.example/v1", AiProvider.OPENAI)
        store.setBaseUrl("https://gemini.example", AiProvider.GEMINI)

        assertEquals("https://openai.example/v1", store.baseUrl(AiProvider.OPENAI))
        assertEquals("https://gemini.example", store.baseUrl(AiProvider.GEMINI))
        assertNull(store.baseUrl(AiProvider.ANTHROPIC))
        assertEquals(
            AiProvider.ANTHROPIC.defaultBaseUrl,
            store.baseUrlOrDefault(AiProvider.ANTHROPIC),
        )
    }

    @Test
    fun legacyModelAndBaseUrlMoveToTheSelectedProvider() {
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("provider", AiProvider.GEMINI.name)
            .putString("model", "legacy-model")
            .putString("base_url", "https://legacy.example/v1")
            .commit()

        val migrated = AiKeyStore(context)

        assertEquals("legacy-model", migrated.model(AiProvider.GEMINI))
        assertEquals("https://legacy.example/v1", migrated.baseUrl(AiProvider.GEMINI))
        assertNull(migrated.model(AiProvider.OPENAI))
        assertNull(migrated.baseUrl(AiProvider.OPENAI))
        assertFalse(prefs.contains("model"))
        assertFalse(prefs.contains("base_url"))
    }
}
