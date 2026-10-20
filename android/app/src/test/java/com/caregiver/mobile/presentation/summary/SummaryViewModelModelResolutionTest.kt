package com.caregiver.mobile.presentation.summary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.test.TestApiKeyCipher
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.LlmClient
import com.caregiver.mobile.ai.LlmClientFactory
import com.caregiver.mobile.ai.LlmRequest
import com.caregiver.mobile.ai.LlmResult
import com.caregiver.mobile.ai.LlmStatus
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeSummaryApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.presentation.summary.SummaryState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelModelResolutionTest {
    private lateinit var file: File
    private lateinit var settings: SettingsStore
    private lateinit var summaries: FakeSummaryApi
    private lateinit var repository: AuthRepository
    private lateinit var store: AiKeyStore
    private lateinit var factory: RecordingFactory

    @Before
    fun setUp() {
        file = File.createTempFile("summary-model-test", ".preferences_pb")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { file },
        )
        summaries = FakeSummaryApi()
        val apis = FakeBackendApis(FakeAuthApi()).also { it.summaryApi = summaries }
        repository = AuthRepository(apis, settings, TokenHolder())
        store = AiKeyStore(ApplicationProvider.getApplicationContext(), TestApiKeyCipher())
        store.setConsentGiven()
        store.setProvider(AiProvider.OPENAI)
        factory = RecordingFactory()
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun apis() = FakeBackendApis(FakeAuthApi()).also { it.summaryApi = summaries }

    private fun summary() = SummaryDto(
        id = "s1", recipientId = "r1", periodDays = 7, text = "Reviewed notes.",
        redFlags = emptyList(), evidence = emptyList(), uncertainties = emptyList(),
    )

    @Test
    fun blankSavedModelUsesProviderDefault() = runTest {
        store.setApiKey("test-key")
        store.setModel("")
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", apis(), repository, this, aiKeyStore = store, llmClientFactory = factory)
        vm.state.first { it !is SummaryState.Loading }
        assertEquals(AiProvider.OPENAI.defaultModel, factory.lastModel)
    }

    @Test
    fun customSavedModelIsUsed() = runTest {
        store.setApiKey("test-key")
        store.setModel("custom-model")
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", apis(), repository, this, aiKeyStore = store, llmClientFactory = factory)
        vm.state.first { it !is SummaryState.Loading }
        assertEquals("custom-model", factory.lastModel)
    }

    @Test
    fun planSuggestionUsesProviderDefaultForBlankModel() = runTest {
        // suggestPlan() only runs from the Content state, and with an API key
        // present load() always lands on AiUnavailable: the AI branch's notes
        // fetch throws (no handler wired) and it never falls back to the
        // backend summarize. So build the VM without a key — the backend
        // fallback produces Content — and insert the key only afterwards.
        // That makes the recorded model prove the SUGGEST path's own
        // provider-default resolution (load() never even creates a client
        // without a key). The suggest coroutine below then throws on its own
        // notes fetch too — expected and harmless; the model was already
        // recorded by createClient before that point.
        store.setModel("")
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", apis(), repository, this, aiKeyStore = store, llmClientFactory = factory)
        vm.state.first { it is SummaryState.Content }
        store.setApiKey("test-key")
        vm.suggestPlan()
        advanceUntilIdle()
        assertEquals(AiProvider.OPENAI.defaultModel, factory.lastModel)
    }

    private class RecordingFactory : LlmClientFactory() {
        var lastModel: String? = null
        override fun createClient(baseUrl: String, model: String, apiKey: String): LlmClient {
            lastModel = model
            return FakeLlmClient()
        }
        override fun createAnthropicClient(baseUrl: String, model: String, apiKey: String): LlmClient {
            lastModel = model
            return FakeLlmClient()
        }
        override fun createGeminiClient(baseUrl: String, model: String, apiKey: String): LlmClient {
            lastModel = model
            return FakeLlmClient()
        }
    }

    private class FakeLlmClient : LlmClient {
        override suspend fun complete(request: LlmRequest): LlmResult =
            LlmResult(LlmStatus.OK, """{"observations":[],"uncertainties":[]}""")
    }
}
