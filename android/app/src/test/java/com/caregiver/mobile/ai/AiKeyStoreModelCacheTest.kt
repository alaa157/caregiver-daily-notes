package com.caregiver.mobile.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The cache touches no keys, so a cipher that does nothing is enough here. */
private class NoopCipher : ApiKeyCipher {
    override fun encrypt(plain: String, accountId: String): String? = plain
    override fun decrypt(stored: String, accountId: String): ApiKeyState =
        ApiKeyState.Readable(stored)

    override fun delete(accountId: String) = Unit
}

/**
 * #990: the model-list cache is per account and per provider, survives a new
 * store instance, and never throws on a bad stored value.
 */
@RunWith(RobolectricTestRunner::class)
class AiKeyStoreModelCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accountA = MutableStateFlow<String?>("account-a")
    private val accountB = MutableStateFlow<String?>("account-b")
    private val noAccount = MutableStateFlow<String?>(null)

    @Before
    fun clearSavedSettings() {
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun store(account: MutableStateFlow<String?> = accountA) =
        AiKeyStore(context, NoopCipher(), account)

    @Test
    fun roundTripKeepsIdsFlagsAndTimestamp() {
        val models = listOf(
            AiModelOption("gpt-6-luna"),
            AiModelOption(
                id = "openrouter/free",
                free = true,
                jsonMode = false,
                note = "may train on notes",
                recommendedForSummaries = true,
            ),
        )
        store().setCachedModels(AiProvider.OPEN_ROUTER, models, 1_700_000_000_123L)

        val cached = store().cachedModels(AiProvider.OPEN_ROUTER)
        assertNotNull(cached)
        assertEquals(1_700_000_000_123L, cached!!.fetchedAtEpochMillis)
        assertEquals(models, cached.models)
        assertEquals("openrouter/free", cached.models[1].id)
        assertTrue(cached.models[1].free)
        assertFalse(cached.models[1].jsonMode)
        assertEquals("may train on notes", cached.models[1].note)
        assertTrue(cached.models[1].recommendedForSummaries)
    }

    @Test
    fun accountsDoNotSeeEachOthersCache() {
        val optionA = AiModelOption("a-model")
        val optionB = AiModelOption("b-model")
        store(accountA).setCachedModels(AiProvider.OPENAI, listOf(optionA), 100L)
        store(accountB).setCachedModels(AiProvider.OPENAI, listOf(optionB), 200L)

        val fromA = store(accountA).cachedModels(AiProvider.OPENAI)
        val fromB = store(accountB).cachedModels(AiProvider.OPENAI)
        assertEquals(listOf(optionA), fromA?.models)
        assertEquals(100L, fromA?.fetchedAtEpochMillis)
        assertEquals(listOf(optionB), fromB?.models)
        assertEquals(200L, fromB?.fetchedAtEpochMillis)
    }

    @Test
    fun providersDoNotSeeEachOthersCache() {
        val openAi = AiModelOption("gpt-6-luna")
        val gemini = AiModelOption("gemini-3.8-flash", recommendedForSummaries = true)
        val store = store()
        store.setCachedModels(AiProvider.OPENAI, listOf(openAi), 11L)
        store.setCachedModels(AiProvider.GEMINI, listOf(gemini), 22L)

        assertEquals(listOf(openAi), store.cachedModels(AiProvider.OPENAI)?.models)
        assertEquals(11L, store.cachedModels(AiProvider.OPENAI)?.fetchedAtEpochMillis)
        assertEquals(listOf(gemini), store.cachedModels(AiProvider.GEMINI)?.models)
        assertEquals(22L, store.cachedModels(AiProvider.GEMINI)?.fetchedAtEpochMillis)
        assertNull(store.cachedModels(AiProvider.ANTHROPIC))
    }

    @Test
    fun corruptValueReturnsNullAndIsRemoved() {
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val openAiKey = "account-a_models_OPENAI"
        val geminiKey = "account-a_models_GEMINI"
        prefs.edit()
            .putString(openAiKey, "{not-json")
            .putString(geminiKey, "[]") // valid JSON, wrong shape
            .commit()

        assertNull(store().cachedModels(AiProvider.OPENAI))
        assertFalse(prefs.contains(openAiKey))
        assertNull(store().cachedModels(AiProvider.GEMINI))
        assertFalse(prefs.contains(geminiKey))
    }

    @Test
    fun clearRemovesOnlyThatProviderForTheActiveAccount() {
        val storeA = store(accountA)
        val storeB = store(accountB)
        for (store in listOf(storeA, storeB)) {
            store.setCachedModels(AiProvider.OPENAI, listOf(AiModelOption("openai")), 1L)
            store.setCachedModels(AiProvider.GEMINI, listOf(AiModelOption("gemini")), 2L)
        }

        storeA.clearCachedModels(AiProvider.OPENAI)

        assertNull(storeA.cachedModels(AiProvider.OPENAI))
        assertNotNull(storeA.cachedModels(AiProvider.GEMINI))
        assertNotNull(storeB.cachedModels(AiProvider.OPENAI))
        assertNotNull(storeB.cachedModels(AiProvider.GEMINI))

        // Clearing a key that is not there is a no-op.
        storeA.clearCachedModels(AiProvider.XAI)
        assertNull(storeA.cachedModels(AiProvider.XAI))
    }

    @Test
    fun newInstanceWithSamePrefsReadsTheCache() {
        val models = listOf(AiModelOption("persisted", free = true, note = "kept"))
        store().setCachedModels(AiProvider.OPENAI, models, 99L)

        // A new store instance is what a process restart would build.
        val restarted = store()
        assertEquals(models, restarted.cachedModels(AiProvider.OPENAI)?.models)
        assertEquals(99L, restarted.cachedModels(AiProvider.OPENAI)?.fetchedAtEpochMillis)
    }

    @Test
    fun noActiveAccountIsANoop() {
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val store = store(noAccount)

        store.setCachedModels(AiProvider.OPENAI, listOf(AiModelOption("m")), 5L)
        store.clearCachedModels(AiProvider.OPENAI)

        assertNull(store.cachedModels(AiProvider.OPENAI))
        assertTrue(prefs.all.keys.none { it.contains("models_") })
    }
}
