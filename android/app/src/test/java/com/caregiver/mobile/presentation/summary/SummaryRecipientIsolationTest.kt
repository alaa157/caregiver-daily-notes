package com.caregiver.mobile.presentation.summary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
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
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.FakeSummaryApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SignalDto
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end isolation check for the summary path the app actually runs:
 * ViewModel fetch -> [com.caregiver.mobile.ai.SummaryAiService] -> AI client
 * -> rendered evidence.
 *
 * The fake backend deliberately ignores the recipient filter and returns
 * notes/signals for two people. The ViewModel must still ask for "r1", the
 * service must scrub "r2" before the prompt, and a model reply that cites
 * "r2" must never survive into the rendered evidence ("Important notes").
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryRecipientIsolationTest {

    private lateinit var file: File
    private lateinit var settings: SettingsStore
    private lateinit var repository: AuthRepository
    private lateinit var store: AiKeyStore

    private val today: String = LocalDate.now().toString()

    private val r1Note = NoteDto(
        id = "n1",
        recipientId = "r1",
        date = today,
        mood = "ok",
        appetite = "good",
        sleep = "good",
        mobility = "ok",
        medicationTaken = "taken",
        pain = 1,
        fall = false,
        text = "Patient walked today",
    )

    private val r2Note = NoteDto(
        id = "n2",
        recipientId = "r2",
        date = today,
        mood = "ok",
        appetite = "good",
        sleep = "good",
        mobility = "ok",
        medicationTaken = "taken",
        pain = 1,
        fall = false,
        text = "Other recipient secret",
    )

    private val r1Signal = SignalDto(
        noteId = "n1",
        recipientId = "r1",
        date = today,
        fallReported = false,
        pain = 1,
        medication = "taken",
        medicationUnverified = false,
        poorAppetite = false,
        text = "Patient walked today",
    )

    private val r2Signal = SignalDto(
        noteId = "n2",
        recipientId = "r2",
        date = today,
        fallReported = true,
        pain = 9,
        medication = "missed",
        medicationUnverified = true,
        poorAppetite = true,
        text = "Other recipient secret",
    )

    @Before
    fun setUp() {
        file = File.createTempFile("summary-isolation-test", ".preferences_pb")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { file },
        )
        repository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, TokenHolder())
        store = AiKeyStore(ApplicationProvider.getApplicationContext(), TestApiKeyCipher())
        store.setConsentGiven()
        store.setProvider(AiProvider.OPENAI)
        store.setApiKey("test-key")
        store.setModel("test-model")
        store.setSystemPrompt("")
        store.setSummarySystemPrompt("")
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun summaryForOneRecipientNeverSendsOrShowsAnotherRecipientsNotes() = runTest {
        // The store exposes fakes under their API types, so handlers are
        // configured on concrete fakes first and the store then points at
        // them (same pattern the rest of the suite uses with .also).
        // Simulate the worst case: the transport hands back both people's
        // records even though the caller filtered by recipient.
        val noteFake = FakeNoteApi().also {
            it.historyHandler = { _, _, _ -> listOf(r1Note, r2Note) }
        }
        val summaryFake = FakeSummaryApi().also {
            it.signalsHandler = { _, _, _ -> listOf(r1Signal, r2Signal) }
            it.summarizeHandler = { error("backend summarize must not run when a key is set") }
        }
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.noteApi = noteFake
            it.summaryApi = summaryFake
        }

        val client = RecordingClient()
        val factory = FixedFactory(client)
        val vm = SummaryViewModel(
            "r1",
            apis,
            repository,
            this,
            aiKeyStore = store,
            llmClientFactory = factory,
        )
        val state = vm.state.first { it is SummaryState.Content || it is SummaryState.AiUnavailable }

        // The fetch is scoped to the selected recipient...
        assertEquals("r1", noteFake.lastHistory?.first)

        // ...the other person's note never reaches the model...
        val request = requireNotNull(client.lastRequest)
        assertTrue(request.dataBlock.contains("Patient walked today"))
        assertFalse(request.dataBlock.contains("Other recipient secret"))

        // ...and a model reply that cites the other person is rejected before
        // it can appear under "Important notes".
        val content = state as SummaryState.Content
        assertEquals(listOf("n1"), content.summary.evidence.map { it.noteId })
        assertTrue(content.summary.evidence.none { it.quote.contains("secret") })
        assertTrue(content.summary.uncertainties.any { it.topic == "invalid_evidence" })
    }

    @Test
    fun aiFailureKeepsTheChosenRecipientsFlags() = runTest {
        val noteFake = FakeNoteApi().also {
            it.historyHandler = { _, _, _ -> listOf(r1Note, r2Note) }
        }
        val summaryFake = FakeSummaryApi().also {
            it.signalsHandler = { _, _, _ ->
                listOf(r1Signal.copy(fallReported = true), r2Signal)
            }
        }
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.noteApi = noteFake
            it.summaryApi = summaryFake
        }
        val vm = SummaryViewModel(
            "r1",
            apis,
            repository,
            this,
            aiKeyStore = store,
            llmClientFactory = FixedFactory(ThrowingClient()),
        )
        val state = vm.state.first { it is SummaryState.AiUnavailable } as SummaryState.AiUnavailable

        // The provider throw happens after the deterministic signals were
        // evaluated, so r1's fall must still reach the banner; r2's flags
        // must not. The null `last` proves the flag came from signals, not
        // from a previous summary.
        assertEquals(null, state.last)
        assertEquals(listOf("FALL_REPORTED"), state.redFlags)
    }

    @Test
    fun aiFailureFlagsAreScopedToTheChosenRecipient() = runTest {
        val noteFake = FakeNoteApi().also {
            it.historyHandler = { _, _, _ -> listOf(r1Note, r2Note) }
        }
        val summaryFake = FakeSummaryApi().also {
            it.signalsHandler = { _, _, _ -> listOf(r1Signal, r2Signal) }
        }
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.noteApi = noteFake
            it.summaryApi = summaryFake
        }
        val vm = SummaryViewModel(
            "r1",
            apis,
            repository,
            this,
            aiKeyStore = store,
            llmClientFactory = FixedFactory(ThrowingClient()),
        )
        val state = vm.state.first { it is SummaryState.AiUnavailable } as SummaryState.AiUnavailable
        // r2 has a fall; the AiUnavailable banner must not inherit it.
        assertFalse(state.redFlags.contains("FALL_REPORTED"))
        assertFalse(state.redFlags.contains("HIGH_PAIN"))
    }

    @Test
    fun providerReasonCodeReachesTheUiForRecoveryCopy() = runTest {
        val noteFake = FakeNoteApi().also {
            it.historyHandler = { _, _, _ -> listOf(r1Note) }
        }
        val summaryFake = FakeSummaryApi().also {
            it.signalsHandler = { _, _, _ -> listOf(r1Signal) }
        }
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.noteApi = noteFake
            it.summaryApi = summaryFake
        }
        val vm = SummaryViewModel(
            "r1",
            apis,
            repository,
            this,
            aiKeyStore = store,
            llmClientFactory = FixedFactory(FallbackClient("invalid_key")),
        )

        val state = vm.state.first { it is SummaryState.AiUnavailable } as SummaryState.AiUnavailable

        assertEquals("invalid_key", state.reasonCode)
    }

    private class FixedFactory(private val client: LlmClient) : LlmClientFactory() {
        override fun createClient(baseUrl: String, model: String, apiKey: String): LlmClient = client

        override fun createAnthropicClient(baseUrl: String, model: String, apiKey: String): LlmClient = client

        override fun createGeminiClient(baseUrl: String, model: String, apiKey: String): LlmClient = client
    }

    private class ThrowingClient : LlmClient {
        override suspend fun complete(request: LlmRequest): LlmResult =
            throw java.io.IOException("provider unreachable")
    }

    private class FallbackClient(private val code: String) : LlmClient {
        override suspend fun complete(request: LlmRequest): LlmResult =
            LlmResult(LlmStatus.FALLBACK, "", code)
    }

    private class RecordingClient : LlmClient {
        var lastRequest: LlmRequest? = null

        override suspend fun complete(request: LlmRequest): LlmResult {
            lastRequest = request
            return LlmResult(
                LlmStatus.OK,
                """{"observations":[""" +
                    """{"text":"walked","noteId":"n1","quote":"walked"},""" +
                    """{"text":"secret","noteId":"n2","quote":"secret"}""" +
                    """],"uncertainties":[]}""",
            )
        }
    }
}
