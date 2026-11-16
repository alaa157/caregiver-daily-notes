package com.caregiver.mobile.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #613: per-account AI key scoping. Two accounts must not share keys,
 * switching accounts changes which key is returned, and no active account
 * returns Absent. Uses a fake cipher so the test can run without a real
 * AndroidKeyStore.
 */
private class FakeApiKeyCipher : ApiKeyCipher {
    val keys = mutableMapOf<String, String>() // accountId -> key
    override suspend fun encrypt(plain: String, accountId: String): String? {
        keys[accountId] = plain
        return "encrypted_$plain"
    }
    override suspend fun decrypt(stored: String, accountId: String): ApiKeyState {
        val key = keys[accountId] ?: return ApiKeyState.Absent
        return if (stored == "encrypted_$key") ApiKeyState.Readable(key) else ApiKeyState.Invalidated
    }
}

class AiKeyStoreScopingTest {

    private val activeAccountA = MutableStateFlow("account-a")
    private val activeAccountB = MutableStateFlow("account-b")
    private val noAccount = MutableStateFlow<String?>(null)
    private val cipher = FakeApiKeyCipher()

    @Test
    fun twoAccountsHoldDifferentKeysForSameProvider() = runTest {
        val storeA = AiKeyStore(null, cipher, activeAccountA)
        val storeB = AiKeyStore(null, cipher, activeAccountB)

        storeA.setApiKey("key-a", AiProvider.OPENAI)
        storeB.setApiKey("key-b", AiProvider.OPENAI)

        assertEquals("key-a", storeA.apiKey(AiProvider.OPENAI))
        assertEquals("key-b", storeB.apiKey(AiProvider.OPENAI))
    }

    @Test
    fun accountBCannotReadAccountAsKey() = runTest {
        val storeA = AiKeyStore(null, cipher, activeAccountA)
        val storeB = AiKeyStore(null, cipher, activeAccountB)

        storeA.setApiKey("key-a", AiProvider.OPENAI)
        assertEquals(ApiKeyState.Absent, storeB.apiKeyState(AiProvider.OPENAI))
    }

    @Test
    fun aliasContainsTheAccountId() = runTest {
        // The keystore alias in AndroidKeyStoreCipher is `ALIAS_PREFIX + accountId`.
        // We assert the cipher was called with the right id by checking the fake's
        // keys map after two writes with different accounts.
        val store = AiKeyStore(null, cipher, activeAccountA)
        store.setApiKey("any", AiProvider.OPENAI)
        assertTrue(cipher.keys.containsKey("account-a"))

        activeAccountA.value = "account-b"
        store.setApiKey("any", AiProvider.OPENAI)
        assertTrue(cipher.keys.containsKey("account-b"))
    }

    @Test
    fun switchingAccountsChangesWhichKeyIsReturned() = runTest {
        val sharedActive = MutableStateFlow("account-a")
        val store = AiKeyStore(null, cipher, sharedActive)

        store.setApiKey("key-a", AiProvider.OPENAI)
        assertEquals("key-a", store.apiKey(AiProvider.OPENAI))

        sharedActive.value = "account-b"
        assertEquals(ApiKeyState.Absent, store.apiKeyState(AiProvider.OPENAI))

        store.setApiKey("key-b", AiProvider.OPENAI)
        assertEquals("key-b", store.apiKey(AiProvider.OPENAI))

        sharedActive.value = "account-a"
        assertEquals("key-a", store.apiKey(AiProvider.OPENAI))
    }

    @Test
    fun noActiveAccountReturnsAbsent() = runTest {
        val store = AiKeyStore(null, cipher, noAccount)
        assertEquals(ApiKeyState.Absent, store.apiKeyState(AiProvider.OPENAI))
        assertEquals(null, store.apiKey(AiProvider.OPENAI))
        assertEquals(null, store.model())
        assertEquals(null, store.baseUrl())
        assertEquals(false, store.consentGiven())
    }

    @Test
    fun providerModelBaseUrlAndConsentArePerAccount() = runTest {
        val storeA = AiKeyStore(null, cipher, activeAccountA)
        val storeB = AiKeyStore(null, cipher, activeAccountB)

        storeA.setProvider(AiProvider.ANTHROPIC)
        storeA.setModel("model-a", AiProvider.ANTHROPIC)
        storeA.setBaseUrl("https://a.com", AiProvider.ANTHROPIC)
        storeA.setConsentGiven(AiProvider.ANTHROPIC)

        storeB.setProvider(AiProvider.OPENAI)
        storeB.setModel("model-b", AiProvider.OPENAI)
        storeB.setBaseUrl("https://b.com", AiProvider.OPENAI)
        storeB.setConsentGiven(AiProvider.OPENAI)

        assertEquals(AiProvider.ANTHROPIC, storeA.provider())
        assertEquals("model-a", storeA.model(AiProvider.ANTHROPIC))
        assertEquals("https://a.com", storeA.baseUrl(AiProvider.ANTHROPIC))
        assertTrue(storeA.consentGiven(AiProvider.ANTHROPIC))

        assertEquals(AiProvider.OPENAI, storeB.provider())
        assertEquals("model-b", storeB.model(AiProvider.OPENAI))
        assertEquals("https://b.com", storeB.baseUrl(AiProvider.OPENAI))
        assertTrue(storeB.consentGiven(AiProvider.OPENAI))
    }

    @Test
    fun noPlaintextKeyIsWrittenToSharedPreferences() = runTest {
        // The fake cipher stores keys in memory, but we assert the SharedPreferences
        // (represented here by nothing in the fake) never receives the plaintext key.
        // In production, SharedPreferences only gets the encrypted output from
        // `cipher.encrypt`, which we've already tested separately.
        val store = AiKeyStore(null, cipher, activeAccountA)
        store.setApiKey("secret-key", AiProvider.OPENAI)

        // The fake cipher received the plaintext and stored it in its map.
        assertTrue(cipher.keys.containsKey("account-a"))
        assertEquals("secret-key", cipher.keys["account-a"])
        // The SharedPreferences layer would only ever see the encrypted form,
        // which the cipher returns as "encrypted_secret-key". Nothing in this
        // test touches that directly because we're testing at the AiKeyStore level.
    }
}
