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
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
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
import retrofit2.HttpException

/**
 * Home dashboard logic on the JVM: greeting, today progress over active
 * recipients only, status chips, flag preservation, and failure mapping.
 * Loading is one bounded window request grouped in memory — the tests pin
 * the queried range and the absence of per-recipient fan-out.
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
    private val weekStart = "2026-10-02"
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

    private suspend fun settledContent(vm: HomeViewModel): HomeContent {
        val state = vm.state.first {
            (it is HomeState.Content && !it.refreshing) || it is HomeState.Error
        }
        assertTrue("expected settled Content, was $state", state is HomeState.Content)
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
    fun loadUsesSingleBoundedWindowRequest() = runTest {
        recipients.listHandler = { listOf(RecipientDto("a", "A", true)) }

        content(vm(scope = this))

        assertEquals(listOf(Triple(null, weekStart, today.toString())), notes.historyCalls)
        assertEquals(0, signals.signalsCalls)
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
        notes.historyHandler = { _, _, _ -> listOf(note("n1", "a", today.toString())) }

        val c = content(vm(scope = this))

        assertEquals(1, c.done)
        assertEquals(2, c.total)
    }

    @Test
    fun chipsCoverDoneMissedPainFallAndNoNote() = runTest {
        recipients.listHandler = {
            listOf(
                RecipientDto("done", "D", true),
                RecipientDto("missed", "M", true),
                RecipientDto("pain", "P", true),
                RecipientDto("fall", "F", true),
                RecipientDto("empty", "E", true),
            )
        }
        notes.historyHandler = { _, _, _ ->
            listOf(
                note("n1", "done", today.toString(), pain = 1),
                note("n2", "missed", "2026-10-06", pain = 1),
                note("n3", "pain", "2026-10-06", pain = 8),
                note("n4", "fall", "2026-10-07", pain = 2, fall = true),
            )
        }

        val cards = content(vm(scope = this)).cards.associateBy { it.recipientId }

        assertEquals(listOf(RecipientChip.DoneToday), cards["done"]!!.chips)
        assertEquals(listOf(RecipientChip.MissedToday), cards["missed"]!!.chips)
        assertEquals(
            listOf(RecipientChip.MissedToday, RecipientChip.HighPain),
            cards["pain"]!!.chips,
        )
        assertEquals(
            listOf(RecipientChip.MissedToday, RecipientChip.FallFlag),
            cards["fall"]!!.chips,
        )
        assertEquals(listOf(RecipientChip.NoNote), cards["empty"]!!.chips)
    }

    @Test
    fun fallNotesBecomeSafetyFlags() = runTest {
        recipients.listHandler = { listOf(RecipientDto("f", "Ferial", true)) }
        notes.historyHandler = { _, _, _ ->
            listOf(note("n9", "f", "2026-10-07", fall = true))
        }

        val c = content(vm(scope = this))

        assertEquals(listOf(SafetyFlag("Ferial", "2026-10-07")), c.flags)
    }

    @Test
    fun lastNoteIsLatestByDate() = runTest {
        recipients.listHandler = { listOf(RecipientDto("a", "A", true)) }
        notes.historyHandler = { _, _, _ ->
            listOf(
                note("old", "a", "2026-10-03", mood = "bad"),
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
        assertNull((state as HomeState.Error).lastContent)
    }

    @Test
    fun serverErrorSurfacesError() = runTest {
        recipients.listHandler = { throw FakeAuthApi.httpError(500, "SERVER_ERROR") }

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
    fun refreshKeepsFlagsVisibleOnFailure() = runTest {
        recipients.listHandler = { listOf(RecipientDto("f", "F", true)) }
        notes.historyHandler = { _, _, _ ->
            listOf(note("n1", "f", today.toString(), fall = true))
        }
        val viewModel = vm(scope = this)
        val before = content(viewModel)
        assertEquals(1, before.flags.size)

        notes.historyHandler = { _, _, _ -> throw IOException("down") }
        viewModel.refresh()
        val state = viewModel.state.first {
            it is HomeState.Error || (it is HomeState.Content && !it.refreshing)
        }

        assertTrue(state is HomeState.Error)
        assertEquals(before.flags, (state as HomeState.Error).lastContent?.flags)
    }

    @Test
    fun successfulRefreshClearsFlagsWhenBackendReportsNone() = runTest {
        recipients.listHandler = { listOf(RecipientDto("f", "F", true)) }
        notes.historyHandler = { _, _, _ ->
            listOf(note("n1", "f", today.toString(), fall = true))
        }
        val viewModel = vm(scope = this)
        assertEquals(1, content(viewModel).flags.size)

        notes.historyHandler = { _, _, _ ->
            listOf(note("n1", "f", today.toString()))
        }
        viewModel.refresh()
        val after = settledContent(viewModel)

        assertTrue(after.flags.isEmpty())
    }

    @Test
    fun logoutDuringRefreshKeepsContent() = runTest {
        recipients.listHandler = { listOf(RecipientDto("a", "A", true)) }
        val viewModel = vm(scope = this)
        content(viewModel)

        recipients.listHandler = { throw LoggedOutException() }
        viewModel.refresh()
        // Root nav flips to login; the stale content must not be wiped here.
        val state = viewModel.state.first { it !is HomeState.Loading }

        assertTrue(state is HomeState.Content)
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
        fall: Boolean = false,
    ) = NoteDto(
        id = id, recipientId = recipientId, date = date, mood = mood,
        appetite = appetite, sleep = sleep, mobility = "walks",
        medicationTaken = "taken", pain = pain, fall = fall, text = "t",
    )
}
