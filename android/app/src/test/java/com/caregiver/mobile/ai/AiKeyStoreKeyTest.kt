package com.caregiver.mobile.ai

import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.test.TestApiKeyCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The key store: a keystore failure must not crash a screen, and a key that
 * can no longer be decrypted must not look like "no key at all".
 */
@RunWith(RobolectricTestRunner::class)
class AiKeyStoreKeyTest {
    private fun store(cipher: ApiKeyCipher = TestApiKeyCipher()) =
        AiKeyStore(ApplicationProvider.getApplicationContext(), cipher)

    @Test
    fun storedKeyReadsBack() {
        val store = store()
        assertTrue(store.setApiKey("sk-test-123"))
        assertEquals("sk-test-123", store.apiKey())
        assertTrue(store.hasKey())
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
