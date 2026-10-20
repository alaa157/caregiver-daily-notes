package com.caregiver.mobile.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.test.TestApiKeyCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The key store: a keystore failure must not crash a screen, and a key that
 * can no longer be decrypted must not look like "no key at all".
 */
@RunWith(RobolectricTestRunner::class)
class AiKeyStoreKeyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSavedSettings() {
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun store(cipher: ApiKeyCipher = TestApiKeyCipher()) =
        AiKeyStore(context, cipher)

    @Test
    fun storedKeyReadsBack() {
        val store = store()
        assertTrue(store.setApiKey("sk-test-123"))
        assertEquals("sk-test-123", store.apiKey())
        assertTrue(store.hasKey())
    }

    @Test
    fun eachProviderHasItsOwnKey() {
        val store = store()
        assertTrue(store.setApiKey("openai-key", AiProvider.OPENAI))
        assertTrue(store.setApiKey("gemini-key", AiProvider.GEMINI))

        store.setProvider(AiProvider.OPENAI)
        assertEquals("openai-key", store.apiKey())
        store.setProvider(AiProvider.GEMINI)
        assertEquals("gemini-key", store.apiKey())
        store.setProvider(AiProvider.ANTHROPIC)
        assertNull(store.apiKey())
        assertFalse(store.hasKey())
    }

    @Test
    fun clearingOneProvidersKeyLeavesTheOther() {
        val store = store()
        store.setApiKey("openai-key", AiProvider.OPENAI)
        store.setApiKey("gemini-key", AiProvider.GEMINI)

        store.clearApiKey(AiProvider.GEMINI)

        assertNull(store.apiKey(AiProvider.GEMINI))
        assertEquals("openai-key", store.apiKey(AiProvider.OPENAI))
    }

    @Test
    fun legacySingleKeyMovesToTheSelectedProvider() {
        val cipher = TestApiKeyCipher()
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("provider", AiProvider.GEMINI.name)
            .putString("api_key", cipher.encrypt("legacy-key"))
            .commit()

        val store = AiKeyStore(context, cipher)

        assertEquals("legacy-key", store.apiKey(AiProvider.GEMINI))
        assertEquals(ApiKeyState.Absent, store.apiKeyState(AiProvider.OPENAI))
        assertFalse(prefs.contains("api_key"))
        assertTrue(prefs.contains("api_key_GEMINI"))
    }

    @Test
    fun noKeyIsAbsent() {
        val store = store()
        assertEquals(ApiKeyState.Absent, store.apiKeyState())
        assertNull(store.apiKey())
        assertFalse(store.hasKey())
    }

    @Test
    fun aKeystoreThatRefusesTheWriteIsReportedNotThrown() {
        val store = store(object : ApiKeyCipher {
            override fun encrypt(plain: String): String? = null
            override fun decrypt(stored: String): ApiKeyState = ApiKeyState.Absent
        })
        assertFalse(store.setApiKey("sk-test-123"))
        assertFalse(store.hasKey())
    }

    @Test
    fun anInvalidatedKeyIsNotReportedAsAbsent() {
        val store = store(object : ApiKeyCipher {
            override fun encrypt(plain: String): String? = "stored"
            override fun decrypt(stored: String): ApiKeyState = ApiKeyState.Invalidated
        })
        assertTrue(store.setApiKey("sk-test-123"))
        assertEquals(ApiKeyState.Invalidated, store.apiKeyState())
        assertNull(store.apiKey())
        assertFalse(store.hasKey())
    }

    @Test
    fun anUnreadableKeystoreIsReportedAsUnavailable() {
        val store = store(object : ApiKeyCipher {
            override fun encrypt(plain: String): String? = "stored"
            override fun decrypt(stored: String): ApiKeyState = ApiKeyState.Unavailable
        })
        assertTrue(store.setApiKey("sk-test-123"))
        assertEquals(ApiKeyState.Unavailable, store.apiKeyState())
    }

    @Test
    fun aBlankKeyIsNotAKey() {
        val store = store()
        assertTrue(store.setApiKey(""))
        assertFalse(store.hasKey())
    }

    @Test
    fun clearingTheKeyRemovesIt() {
        val store = store()
        store.setApiKey("sk-test-123")
        store.clearApiKey()
        assertEquals(ApiKeyState.Absent, store.apiKeyState())
    }
}
