package com.caregiver.mobile.ai.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.ai.AiModelCatalog
import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.ApiKeyCipher
import com.caregiver.mobile.ai.ApiKeyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val NOW = 1_700_000_000_000L

/** The store never needs the real keystore here, only a readable key. */
private class NoopCipher : ApiKeyCipher {
    override fun encrypt(plain: String, accountId: String): String? = plain
    override fun decrypt(stored: String, accountId: String): ApiKeyState =
        ApiKeyState.Readable(stored)

    override fun delete(accountId: String) = Unit
}

private class FakeSource(private val result: () -> ModelListResult) : ModelListSource {
    var calls = 0
    var onFetch: (() -> Unit)? = null

    override suspend fun fetch(baseUrl: String, apiKey: String): ModelListResult {
        calls++
        onFetch?.invoke()
        return result()
    }
}

/** The blank-base-url guard is defensive; the real store resolves blanks to a default. */
private class BlankBaseUrlStore(
    context: Context,
    cipher: ApiKeyCipher,
    account: MutableStateFlow<String?>,
) : AiKeyStore(context, cipher, account) {
    override fun baseUrlOrDefault(provider: AiProvider): String = ""
}

@RunWith(RobolectricTestRunner::class)
class ModelCatalogRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val account = MutableStateFlow<String?>("account-a")
    private var now = NOW
    private lateinit var store: AiKeyStore

    @Before
    fun setUp() {
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AiKeyStore(context, NoopCipher(), account)
        assertTrue(store.setApiKey("sk-test", AiProvider.OPENAI))
    }

    private fun repo(source: ModelListSource) =
        ModelCatalogRepository(store, mapOf(AiProvider.OPENAI to source)) { now }

    private fun noNetworkSource() = FakeSource {
        throw AssertionError("the source must not be called")
    }

    @Test
    fun `no key returns bundled without calling the source`() = runBlocking {
        store.clearApiKey(AiProvider.OPENAI)
        val source = noNetworkSource()

        val snapshot = repo(source).models(AiProvider.OPENAI)

        assertEquals(ModelSource.BUNDLED, snapshot.source)
        assertEquals(AiModelCatalog.options(AiProvider.OPENAI), snapshot.models)
        assertNull(snapshot.fetchedAtEpochMillis)
        assertEquals(0, source.calls)
    }

    @Test
    fun `blank base url returns bundled without calling the source`() = runBlocking {
        val blankStore = BlankBaseUrlStore(context, NoopCipher(), account)
        assertTrue(blankStore.setApiKey("sk-test", AiProvider.OPENAI))
        val source = noNetworkSource()

        val snapshot = ModelCatalogRepository(
            blankStore,
            mapOf(AiProvider.OPENAI to source),
        ) { now }.models(AiProvider.OPENAI)

        assertEquals(ModelSource.BUNDLED, snapshot.source)
        assertEquals(AiModelCatalog.options(AiProvider.OPENAI), snapshot.models)
        assertEquals(0, source.calls)
    }

    @Test
    fun `fresh cache returns cached without calling the source`() = runBlocking {
        val cached = listOf(AiModelOption("cached-one"))
        store.setCachedModels(AiProvider.OPENAI, cached, now - 1_000L)
        val source = noNetworkSource()

        val snapshot = repo(source).models(AiProvider.OPENAI)

        assertEquals(ModelSource.CACHED, snapshot.source)
        assertEquals(cached, snapshot.models)
        assertEquals(now - 1_000L, snapshot.fetchedAtEpochMillis)
        assertEquals(0, source.calls)
    }

    @Test
    fun `stale cache fetches returns live and rewrites the cache`() = runBlocking {
        store.setCachedModels(
            AiProvider.OPENAI,
            listOf(AiModelOption("old")),
            now - MODEL_CACHE_TTL_MILLIS - 1,
        )
        val fresh = listOf(AiModelOption("fresh"))
        val source = FakeSource { ModelListResult.Ok(fresh) }

        val snapshot = repo(source).models(AiProvider.OPENAI)

        assertEquals(ModelSource.LIVE, snapshot.source)
        assertEquals(fresh, snapshot.models)
        assertEquals(now, snapshot.fetchedAtEpochMillis)
        assertEquals(1, source.calls)
        assertEquals(fresh, store.cachedModels(AiProvider.OPENAI)?.models)
        assertEquals(now, store.cachedModels(AiProvider.OPENAI)?.fetchedAtEpochMillis)
    }

    @Test
    fun `cache exactly at the ttl is refreshed`() = runBlocking {
        store.setCachedModels(
            AiProvider.OPENAI,
            listOf(AiModelOption("old")),
            now - MODEL_CACHE_TTL_MILLIS,
        )
        val source = FakeSource { ModelListResult.Ok(listOf(AiModelOption("new"))) }

        assertEquals(ModelSource.LIVE, repo(source).models(AiProvider.OPENAI).source)
        assertEquals(1, source.calls)
    }

    @Test
    fun `force refresh fetches even when the cache is fresh`() = runBlocking {
        store.setCachedModels(AiProvider.OPENAI, listOf(AiModelOption("old")), now)
        val refreshed = listOf(AiModelOption("new"))
        val source = FakeSource { ModelListResult.Ok(refreshed) }

        val snapshot = repo(source).models(AiProvider.OPENAI, forceRefresh = true)

        assertEquals(ModelSource.LIVE, snapshot.source)
        assertEquals(refreshed, snapshot.models)
        assertEquals(1, source.calls)
    }

    @Test
    fun `failed refresh with a cache keeps returning that cache`() = runBlocking {
        val cached = listOf(AiModelOption("kept"))
        store.setCachedModels(
            AiProvider.OPENAI,
            cached,
            now - MODEL_CACHE_TTL_MILLIS - 1,
        )
        val source = FakeSource { ModelListResult.Failed("network") }

        val snapshot = repo(source).models(AiProvider.OPENAI)

        assertEquals(ModelSource.CACHED, snapshot.source)
        assertEquals(cached, snapshot.models)
        assertNotNull(store.cachedModels(AiProvider.OPENAI))
    }

    @Test
    fun `failed refresh without a cache returns bundled`() = runBlocking {
        val source = FakeSource { ModelListResult.Failed("network") }

        val snapshot = repo(source).models(AiProvider.OPENAI)

        assertEquals(ModelSource.BUNDLED, snapshot.source)
        assertEquals(AiModelCatalog.options(AiProvider.OPENAI), snapshot.models)
    }

    @Test
    fun `only ids flagged in the bundled list become recommended`() = runBlocking {
        val returned = listOf(
            AiModelOption("gpt-6.2-sol"), // flagged in the bundled catalog
            AiModelOption("gpt-6.2-astra"), // bundled but not flagged
            AiModelOption("unknown-model", recommendedForSummaries = true), // guessed upstream
        )
        val source = FakeSource { ModelListResult.Ok(returned) }

        val snapshot = repo(source).models(AiProvider.OPENAI)
        val byId = snapshot.models.associateBy { it.id }

        assertTrue(byId.getValue("gpt-6.2-sol").recommendedForSummaries)
        assertFalse(byId.getValue("gpt-6.2-astra").recommendedForSummaries)
        assertFalse(byId.getValue("unknown-model").recommendedForSummaries)
        // Enrichment touches only that one flag.
        assertTrue(byId.getValue("unknown-model").jsonMode)
        assertTrue(snapshot.models.all { it.note == null })
        // The enriched list is what gets cached.
        assertEquals(snapshot.models, store.cachedModels(AiProvider.OPENAI)?.models)
    }

    @Test
    fun `account switch during fetch returns bundled and writes nothing`() = runBlocking {
        val source = FakeSource { ModelListResult.Ok(listOf(AiModelOption("live"))) }
        source.onFetch = { account.value = "account-b" }

        val snapshot = repo(source).models(AiProvider.OPENAI)

        assertEquals(ModelSource.BUNDLED, snapshot.source)
        // The new account's cache is untouched; neither is the old one's.
        val storeB = AiKeyStore(context, NoopCipher(), MutableStateFlow("account-b"))
        assertNull(storeB.cachedModels(AiProvider.OPENAI))
        assertNull(store.cachedModels(AiProvider.OPENAI))
    }

    @Test
    fun `a missing source returns bundled`() = runBlocking {
        val repository = ModelCatalogRepository(store, emptyMap()) { now }

        val snapshot = repository.models(AiProvider.OPENAI)

        assertEquals(ModelSource.BUNDLED, snapshot.source)
    }

    @Test
    fun `default sources cover all five providers`() {
        val sources = defaultModelSources()

        assertEquals(5, sources.size)
        AiProvider.values().forEach { assertTrue(sources.containsKey(it)) }
    }
}
