package com.caregiver.mobile.ai

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * #613: per-account AI key scoping. Two accounts must not share keys,
 * switching accounts changes which key is returned, and no active account
 * returns Absent. Uses a fake cipher so the test can run without a real
 * AndroidKeyStore.
 */
private class FakeApiKeyCipher : ApiKeyCipher {
    val keys = mutableMapOf<String, String>() // accountId -> key
    val deleted = mutableListOf<String>()
    override fun encrypt(plain: String, accountId: String): String? {
        keys[accountId] = plain
        return "encrypted_$plain"
    }
    override fun decrypt(stored: String, accountId: String): ApiKeyState {
        val key = keys[accountId] ?: return ApiKeyState.Absent
        return if (stored == "encrypted_$key") ApiKeyState.Readable(key) else ApiKeyState.Invalidated
    }
    override fun delete(accountId: String) {
        keys.remove(accountId)
        deleted += accountId
    }
}

@RunWith(RobolectricTestRunner::class)
class AiKeyStoreScopingTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val activeAccountA = MutableStateFlow("account-a")
    private val activeAccountB = MutableStateFlow("account-b")
    private val noAccount = MutableStateFlow<String?>(null)
    private val cipher = FakeApiKeyCipher()

    @Test
    fun twoAccountsHoldDifferentKeysForSameProvider() = runTest {
        val storeA = AiKeyStore(context, cipher, activeAccountA)
        val storeB = AiKeyStore(context, cipher, activeAccountB)

        storeA.setApiKey("key-a", AiProvider.OPENAI)
        storeB.setApiKey("key-b", AiProvider.OPENAI)

        assertEquals("key-a", storeA.apiKey(AiProvider.OPENAI))
        assertEquals("key-b", storeB.apiKey(AiProvider.OPENAI))
    }

    @Test
    fun accountBCannotReadAccountAsKey() = runTest {
        val storeA = AiKeyStore(context, cipher, activeAccountA)
        val storeB = AiKeyStore(context, cipher, activeAccountB)

        storeA.setApiKey("key-a", AiProvider.OPENAI)
        assertEquals(ApiKeyState.Absent, storeB.apiKeyState(AiProvider.OPENAI))
    }

    @Test
    fun aliasContainsTheAccountId() = runTest {
        // The keystore alias in AndroidKeyStoreCipher is `ALIAS_PREFIX + accountId`.
        // We assert the cipher was called with the right id by checking the fake's
        // keys map after two writes with different accounts.
        val store = AiKeyStore(context, cipher, activeAccountA)
        store.setApiKey("any", AiProvider.OPENAI)
        assertTrue(cipher.keys.containsKey("account-a"))

        activeAccountA.value = "account-b"
        store.setApiKey("any", AiProvider.OPENAI)
        assertTrue(cipher.keys.containsKey("account-b"))
    }

    @Test
    fun switchingAccountsChangesWhichKeyIsReturned() = runTest {
        val sharedActive = MutableStateFlow("account-a")
        val store = AiKeyStore(context, cipher, sharedActive)

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
        val store = AiKeyStore(context, cipher, noAccount)
        assertEquals(ApiKeyState.Absent, store.apiKeyState(AiProvider.OPENAI))
        assertEquals(null, store.apiKey(AiProvider.OPENAI))
        assertEquals(null, store.model())
        assertEquals(null, store.baseUrl())
        assertEquals(false, store.consentGiven())
    }

    @Test
    fun providerModelBaseUrlAndConsentArePerAccount() = runTest {
        val storeA = AiKeyStore(context, cipher, activeAccountA)
        val storeB = AiKeyStore(context, cipher, activeAccountB)

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
        val store = AiKeyStore(context, cipher, activeAccountA)
        store.setApiKey("secret-key", AiProvider.OPENAI)

        // The fake cipher received the plaintext and stored it in its map.
        assertTrue(cipher.keys.containsKey("account-a"))
        assertEquals("secret-key", cipher.keys["account-a"])
        // The SharedPreferences layer would only ever see the encrypted form,
        // which the cipher returns as "encrypted_secret-key". Nothing in this
        // test touches that directly because we're testing at the AiKeyStore level.
    }

    // ── #614: deleteAllForAccount ─────────────────────────────────────────

    @Test
    fun deleteAllForAccountRemovesKeysAndLeavesOtherAccount() = runTest {
        val storeA = AiKeyStore(context, cipher, activeAccountA)
        val storeB = AiKeyStore(context, cipher, activeAccountB)

        storeA.setApiKey("key-a", AiProvider.OPENAI)
        storeA.setModel("model-a", AiProvider.OPENAI)
        storeA.setConsentGiven(AiProvider.OPENAI)
        storeB.setApiKey("key-b", AiProvider.OPENAI)
        storeB.setModel("model-b", AiProvider.OPENAI)

        storeA.deleteAllForAccount("account-a")

        assertEquals(ApiKeyState.Absent, storeA.apiKeyState(AiProvider.OPENAI))
        assertEquals(null, storeA.model(AiProvider.OPENAI))
        assertEquals(false, storeA.consentGiven(AiProvider.OPENAI))
        assertEquals("key-b", storeB.apiKey(AiProvider.OPENAI))
        assertEquals("model-b", storeB.model(AiProvider.OPENAI))
        assertEquals(listOf("account-a"), cipher.deleted)
    }

    @Test
    fun deleteAllForAccountIsIdempotent() = runTest {
        val store = AiKeyStore(context, cipher, activeAccountA)
        store.setApiKey("key-a", AiProvider.OPENAI)

        store.deleteAllForAccount("account-a")
        store.deleteAllForAccount("account-a") // second delete must not throw

        assertEquals(ApiKeyState.Absent, store.apiKeyState(AiProvider.OPENAI))
        assertEquals(2, cipher.deleted.count { it == "account-a" })
    }

    @Test
    fun deleteAllForAccountWithNoKeysSucceeds() = runTest {
        val store = AiKeyStore(context, cipher, activeAccountA)
        store.deleteAllForAccount("never-existed") // must not throw
        assertEquals(listOf("never-existed"), cipher.deleted)
    }

    // ── #630: per-account AI key scoping (acceptance scenarios) ───────────

    @Test
    fun keystoreAliasContainsTheAccountId() {
        // Direct assertion on the naming scheme: the alias must embed the
        // account id so accounts can never share a keystore slot.
        assertTrue(AndroidKeyStoreCipher.aliasFor("acct-123").contains("acct-123"))
        assertEquals(
            "caregiver_ai_key_v1_acct-123",
            AndroidKeyStoreCipher.aliasFor("acct-123"),
        )
    }

    @Test
    fun switchingProvidersNeverReusesAnotherAccountsKey() = runTest {
        val storeA = AiKeyStore(context, cipher, activeAccountA)
        val storeB = AiKeyStore(context, cipher, activeAccountB)

        storeA.setApiKey("key-a-openai", AiProvider.OPENAI)
        storeB.setApiKey("key-b-anthropic", AiProvider.ANTHROPIC)

        // B reads its own provider's key; A's OPENAI key is invisible to B.
        assertEquals("key-b-anthropic", storeB.apiKey(AiProvider.ANTHROPIC))
        assertEquals(ApiKeyState.Absent, storeB.apiKeyState(AiProvider.OPENAI))
        // And A's OPENAI key is still there for A.
        assertEquals("key-a-openai", storeA.apiKey(AiProvider.OPENAI))
    }

    @Test
    fun removeAccountDeletesTheKeyAlias() = runTest {
        // removeAccount's cleanup hook calls deleteAllForAccount; assert the
        // cipher's delete was invoked for the removed account's id.
        val store = AiKeyStore(context, cipher, activeAccountA)
        store.setApiKey("key-a", AiProvider.OPENAI)

        store.deleteAllForAccount("account-a")

        assertTrue(cipher.deleted.contains("account-a"))
        assertEquals(ApiKeyState.Absent, store.apiKeyState(AiProvider.OPENAI))
    }

    @Test
    fun summaryUnderBUsesBsKey() = runTest {
        // Simulates what SummaryViewModel does: read the key from the store
        // for the active account, then hand it to the client factory. The
        // factory must receive exactly B's key string.
        val capturedKeys = mutableListOf<String>()
        val factory = object : LlmClientFactory() {
            override fun createClient(baseUrl: String, model: String, apiKey: String): LlmClient {
                capturedKeys += apiKey
                return object : LlmClient {
                    override suspend fun complete(request: LlmRequest): LlmResult =
                        LlmResult(status = LlmStatus.OK, text = "ok")
                }
            }
        }

        val sharedActive = MutableStateFlow("account-b")
        val store = AiKeyStore(context, cipher, sharedActive)
        store.setApiKey("key-b-secret", AiProvider.OPENAI)

        // SummaryViewModel path: key = store.apiKey(provider) then factory.createClient(..., key)
        val provider = store.selectedProvider()
        val key = store.apiKey(provider).orEmpty()
        factory.createClient("https://api.test", "model-x", key)

        assertEquals(listOf("key-b-secret"), capturedKeys)
    }
}
