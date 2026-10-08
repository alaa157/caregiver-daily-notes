package com.caregiver.mobile.presentation.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.FakeSummaryApi
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.SignalDto
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Home dashboard logic on the JVM: greeting, today progress over active
 * recipients only, status chips, safety flags, and failure mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var file: File
    private lateinit var settings: SettingsStore
    private lateinit var recipients: FakeRecipientApi
    private lateinit var notes: FakeNoteApi
    private lateinit var signals: FakeSummaryApi
    private lateinit var repository: AuthRepository

    private val today = LocalDate.of(2026, 10, 8)
    private val morning = Clock.fixed(Instant.parse("2026-10-08T08:00:00Z"), ZoneOffset.UTC)
    private val evening = Clock.fixed(Instant.parse("2026-10-08T20:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        file = File.createTempFile("home-vm-test", ".preferences_pb")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        recipients = FakeRecipientApi()
        notes = FakeNoteApi()
        signals = FakeSummaryApi()
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
            it.noteApi = notes
            it.summaryApi = signals
        }
        repository = AuthRepository(apis, settings, TokenHolder())
        recipients.listHandler = { emptyList() }
        notes.historyHandler = { _, _, _ -> emptyList() }
        signals.signalsHandler = { _, _, _ -> emptyList() }
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun vm(clock: Clock = morning, scope: TestScope) =
        HomeViewModel(repository, settings, clock, scope)

    private suspend fun content(vm: HomeViewModel): HomeContent {
        val state = vm.state.first { it is HomeState.Content || it is HomeState.Error }
        assertTrue("expected Content, was $state", state is HomeState.Content)
        return (state as HomeState.Content).content
    }

    @Test
    fun morningGreetingUsesCapitalizedEmailName() = runTest {
        settings.setEmail("ahmed@example.com")

        val c = content(vm(scope = this))

        assertEquals(Greeting(morning = true, name = "Ahmed"), c.greeting)
    }

    @Test
    fun eveningGreeting() = runTest {
        settings.setEmail("mona@example.com")

        val c = content(vm(clock = evening, scope = this))

        assertEquals(Greeting(morning = false, name = "Mona"), c.greeting)
    }

    @Test
    fun progressCountsActiveRecipientsWithNoteTodayOnly() = runTest {
        recipients.listHandler = {
            listOf(
                RecipientDto("a", "A", true),
                RecipientDto("b", "B", true),
                RecipientDto("c", "C", false),
            )
        }
        notes.historyHandler = { id, _, _ ->
            if (id == "a") listOf(note("n1", "a", today.toString())) else emptyList()
        }

        val c = content(vm(scope = this))

        assertEquals(1, c.done)
        assertEquals(2, c.total)
    }

    @Test
    fun chipsCoverDonePainFallAndNoNote() = runTest {
        recipients.listHandler = {
            listOf(
                RecipientDto("done", "D", true),
                RecipientDto("pain", "P", true),
                RecipientDto("fall", "F", true),
                RecipientDto("empty", "E", true),
            )
        }
        notes.historyHandler = { id, _, _ ->
            when (id) {
                "done" -> listOf(note("n1", id, today.toString(), pain = 1))
                "pain" -> listOf(note("n2", id, "2026-10-01", pain = 8))
                "fall" -> listOf(note("n3", id, "2026-10-02", pain = 2))
                else -> emptyList()
            }
        }
        signals.signalsHandler = { id, _, _ ->
            if (id == "fall") {
                listOf(signal("n3", id, "2026-10-02", fall = true))
            } else {
                emptyList()
            }
        }

        val cards = content(vm(scope = this)).cards.associateBy { it.recipientId }

        assertEquals(listOf(RecipientChip.DoneToday), cards["done"]!!.chips)
        assertEquals(listOf(RecipientChip.HighPain), cards["pain"]!!.chips)
        assertEquals(listOf(RecipientChip.FallFlag), cards["fall"]!!.chips)
        assertEquals(listOf(RecipientChip.NoNote), cards["empty"]!!.chips)
    }

    @Test
    fun fallSignalsBecomeSafetyFlags() = runTest {
        recipients.listHandler = { listOf(RecipientDto("f", "Ferial", true)) }
        notes.historyHandler = { _, _, _ -> listOf(note("n9", "f", "2026-10-07")) }
        signals.signalsHandler = { _, _, _ ->
            listOf(signal("n9", "f", "2026-10-07", fall = true))
        }

        val c = content(vm(scope = this))

        assertEquals(listOf(SafetyFlag("Ferial", "2026-10-07")), c.flags)
    }

    @Test
    fun lastNoteIsLatestByDate() = runTest {
        recipients.listHandler = { listOf(RecipientDto("a", "A", true)) }
        notes.historyHandler = { _, _, _ ->
            listOf(
                note("old", "a", "2026-10-01", mood = "bad"),
                note("new", "a", "2026-10-06", mood = "good", appetite = "ok", sleep = "fine"),
            )
        }

        val card = content(vm(scope = this)).cards.single()

        assertEquals(LastNote("2026-10-06", "good", "ok", "fine"), card.lastNote)
    }

    @Test
    fun networkFailureSurfacesError() = runTest {
        recipients.listHandler = { throw IOException("down") }

        val state = vm(scope = this).state.first { it is HomeState.Error }

        assertTrue(state is HomeState.Error)
    }

    @Test
    fun logoutIsSwallowedWithoutCrashing() = runTest {
        recipients.listHandler = { throw LoggedOutException() }

        val state = vm(scope = this).state.first { it !is HomeState.Loading }

        assertTrue(state is HomeState.Idle)
    }

    @Test
    fun blankEmailGivesNamelessGreeting() = runTest {
        val c = content(vm(scope = this))

        assertEquals(Greeting(morning = true, name = null), c.greeting)
    }

    private fun note(
        id: String,
        recipientId: String,
        date: String,
        pain: Int = 0,
        mood: String = "good",
        appetite: String = "good",
        sleep: String = "ok",
    ) = NoteDto(
        id = id, recipientId = recipientId, date = date, mood = mood,
        appetite = appetite, sleep = sleep, mobility = "walks",
        medicationTaken = "taken", pain = pain, fall = false, text = "t",
    )

    private fun signal(id: String, recipientId: String, date: String, fall: Boolean) = SignalDto(
        noteId = id, recipientId = recipientId, date = date, fallReported = fall,
        pain = null, medication = "taken", medicationUnverified = false,
        poorAppetite = false, text = "t",
    )
}
