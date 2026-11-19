package com.caregiver.mobile.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.test.TestApiKeyCipher
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val activeAccount = MutableStateFlow<String?>("account-1")

    private fun store(cipher: ApiKeyCipher = TestApiKeyCipher()) =
        AiKeyStore(context, cipher, activeAccount)

    @Test
    fun storedKeyReadsBack() {
        val store = store()
        assertTrue(store.setApiKey("sk-test-123"))
        assertEquals("sk-test-123", store.apiKey())
        assertTrue(store.hasKey())
    }

    @Test
    fun eachProviderHasItsOwnKey() {        val store = store()
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
    fun legacySingleKeyMovesToTheOwnerAccountsOpenAiSlot() {
        val cipher = TestApiKeyCipher()
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("provider", AiProvider.GEMINI.name)
            .putString("api_key", cipher.encrypt("legacy-key", "account-1"))
            .commit()

        val store = AiKeyStore(context, cipher, activeAccount)

        // The migration targets the owner account's OPENAI slot; the legacy
        // "provider" selection is not migrated and no longer steers the copy.
        assertEquals("legacy-key", store.apiKey(AiProvider.OPENAI))
        assertEquals(ApiKeyState.Absent, store.apiKeyState(AiProvider.GEMINI))
        assertFalse(prefs.contains("api_key"))
        assertTrue(prefs.contains("account-1_api_key_OPENAI"))
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
            override fun encrypt(plain: String, accountId: String): String? = null
            override fun decrypt(stored: String, accountId: String): ApiKeyState = ApiKeyState.Absent
            override fun delete(accountId: String) {}
        })
        assertFalse(store.setApiKey("sk-test-123"))
        assertFalse(store.hasKey())
    }

    @Test
    fun anInvalidatedKeyIsNotReportedAsAbsent() {
        val store = store(object : ApiKeyCipher {
            override fun encrypt(plain: String, accountId: String): String? = "stored"
            override fun decrypt(stored: String, accountId: String): ApiKeyState = ApiKeyState.Invalidated
            override fun delete(accountId: String) {}
        })
        assertTrue(store.setApiKey("sk-test-123"))
        assertEquals(ApiKeyState.Invalidated, store.apiKeyState())
        assertNull(store.apiKey())
        assertFalse(store.hasKey())
    }

    @Test
    fun anUnreadableKeystoreIsReportedAsUnavailable() {
        val store = store(object : ApiKeyCipher {
            override fun encrypt(plain: String, accountId: String): String? = "stored"
            override fun decrypt(stored: String, accountId: String): ApiKeyState = ApiKeyState.Unavailable
            override fun delete(accountId: String) {}
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

    @Test
    fun consentIsRememberedPerProvider() {
        val store = store()
        store.setConsentGiven(AiProvider.OPENAI)

        assertTrue(store.consentGiven(AiProvider.OPENAI))
        assertFalse(
            "one provider's consent must not cover another provider's data policy",
            store.consentGiven(AiProvider.GEMINI),
        )
    }

    @Test
    fun revokingOneProvidersConsentLeavesTheOther() {
        val store = store()
        store.setConsentGiven(AiProvider.OPENAI)
        store.setConsentGiven(AiProvider.GEMINI)

        store.revokeConsent(AiProvider.OPENAI)

        assertFalse(store.consentGiven(AiProvider.OPENAI))
        assertTrue(store.consentGiven(AiProvider.GEMINI))
    }

    @Test
    fun legacyGlobalConsentMovesOnlyToTheOwnerAccountsOpenAi() {
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("provider", AiProvider.GEMINI.name)
            .putBoolean("consent", true)
            .commit()

        val store = AiKeyStore(context, TestApiKeyCipher(), activeAccount)

        // Consent migrates to the owner account's OPENAI slot only; the
        // legacy "provider" selection is not migrated.
        assertTrue(store.consentGiven(AiProvider.OPENAI))
        assertFalse(
            "the caregiver never agreed to the other providers by name",
            store.consentGiven(AiProvider.GEMINI),
        )
        assertFalse(prefs.contains("consent"))
    }
}
