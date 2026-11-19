package com.caregiver.mobile.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.test.TestApiKeyCipher
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val activeAccount = MutableStateFlow<String?>("account-1")
    private lateinit var store: AiKeyStore

    @Before
    fun setUp() {
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AiKeyStore(context, TestApiKeyCipher(), activeAccount)
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
    fun legacyModelAndBaseUrlMoveToTheOwnerAccountsOpenAiSlot() {
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("provider", AiProvider.GEMINI.name)
            .putString("model", "legacy-model")
            .putString("base_url", "https://legacy.example/v1")
            .commit()

        val migrated = AiKeyStore(context, TestApiKeyCipher(), activeAccount)

        // The migration targets the owner account's OPENAI slot; the legacy
        // "provider" selection is not migrated and no longer steers the copy.
        assertEquals("legacy-model", migrated.model(AiProvider.OPENAI))
        assertEquals("https://legacy.example/v1", migrated.baseUrl(AiProvider.OPENAI))
        assertNull(migrated.model(AiProvider.GEMINI))
        assertNull(migrated.baseUrl(AiProvider.GEMINI))
        assertFalse(prefs.contains("model"))
        assertFalse(prefs.contains("base_url"))
    }
}
