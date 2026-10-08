package com.caregiver.mobile.presentation.summary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeNoteApi
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.FakeSummaryApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.presentation.home.HomeState
import com.caregiver.mobile.presentation.home.HomeViewModel
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 8: the safety banner survives rotation, process death, and degraded
 * states with flags intact.
 *
 * - Rotation: ViewModel survives config change, so flags in [HomeState] /
 *   [SummaryState] are still there after a refresh cycle.
 * - Process death: flags are re-derivable from the last loaded content, never
 *   invented — a recreated VM loading the same payload reports the same flags.
 * - Degraded states: AiUnavailable / Rejected / Error always carry the last
 *   flags, and the banner has no dismiss control by construction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BannerRetentionTest {

    private lateinit var file: File
    private lateinit var settings: SettingsStore
    private lateinit var summaries: FakeSummaryApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("banner-retention-test", ".preferences_pb")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        summaries = FakeSummaryApi()
        val apis = FakeBackendApis(FakeAuthApi()).also { it.summaryApi = summaries }
        repository = AuthRepository(apis, settings, TokenHolder())
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun summary(flags: List<String> = listOf("FALL_REPORTED")) = SummaryDto(
        id = "s1", recipientId = "r1", periodDays = 7, text = "Reviewed notes.",
        redFlags = flags,
        evidence = listOf(SummaryDto.EvidenceDto("n1", "fell near the bathroom")),
        uncertainties = listOf(SummaryDto.UncertaintyDto("medication", "unsure")),
    )

    private suspend fun summaryContent(vm: SummaryViewModel): SummaryDto {
        val state = vm.state.first { it is SummaryState.Content || it !is SummaryState.Loading }
        assertTrue("expected Content, was $state", state is SummaryState.Content)
        return (state as SummaryState.Content).summary
    }

    @Test
    fun summaryFlagsSurviveAiUnavailable() = runTest {
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", FakeBackendApis(FakeAuthApi()).also { it.summaryApi = summaries }, repository, this)
        val before = summaryContent(vm)
        assertEquals(listOf("FALL_REPORTED"), before.redFlags)

        summaries.summarizeHandler = { throw IOException("offline") }
        vm.refresh()
        val degraded = vm.state.first { it is SummaryState.AiUnavailable }

        // Notes-safe copy + retry, flags intact for the banner.
        assertEquals(
            listOf("FALL_REPORTED"),
            (degraded as SummaryState.AiUnavailable).last?.redFlags,
        )
    }

    @Test
    fun summaryFlagsSurviveRejected() = runTest {
        summaries.summarizeHandler = { summary(listOf("HIGH_PAIN", "MEDICATION_UNCLEAR")) }
        val vm = SummaryViewModel("r1", FakeBackendApis(FakeAuthApi()).also { it.summaryApi = summaries }, repository, this)
        summaryContent(vm)

        summaries.summarizeHandler = { throw FakeAuthApi.httpError(500, "SERVER_ERROR") }
        vm.refresh()
        val degraded = vm.state.first { it is SummaryState.Rejected }

        assertEquals(
            listOf("HIGH_PAIN", "MEDICATION_UNCLEAR"),
            (degraded as SummaryState.Rejected).last?.redFlags,
        )
    }

    @Test
    fun recreatedViewModelReportsSameFlags() = runTest {
        // Process-death simulation: new VM, same backend payload.
        summaries.summarizeHandler = { summary(listOf("FALL_REPORTED")) }
        val first = SummaryViewModel("r1", FakeBackendApis(FakeAuthApi()).also { it.summaryApi = summaries }, repository, this)
        val before = summaryContent(first)

        val second = SummaryViewModel("r1", FakeBackendApis(FakeAuthApi()).also { it.summaryApi = summaries }, repository, this)
        val after = summaryContent(second)

        assertEquals(before.redFlags, after.redFlags)
    }

    @Test
    fun homeFlagsSurviveOfflineError() = runTest {
        val recipients = FakeRecipientApi()
        val notes = FakeNoteApi()
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.recipientApi = recipients
            it.noteApi = notes
            it.summaryApi = FakeSummaryApi()
        }
        val repo = AuthRepository(apis, settings, TokenHolder())
        val morning = Clock.fixed(Instant.parse("2026-10-08T08:00:00Z"), ZoneOffset.UTC)
        fun note() = NoteDto(
            id = "n1", recipientId = "r1", date = "2026-10-08", mood = "good",
            appetite = "good", sleep = "ok", mobility = "walks",
            medicationTaken = "taken", pain = 2, fall = true, text = "fell",
        )
        recipients.listHandler = { listOf(RecipientDto("r1", "Layla", true)) }
        notes.historyHandler = { _, _, _ -> listOf(note()) }
        val vm = HomeViewModel(repo, settings, morning, this)
        val content = vm.state.first { it is HomeState.Content || it is HomeState.Error }
        assertTrue(content is HomeState.Content)
        assertEquals(1, (content as HomeState.Content).content.flags.size)

        // Offline on refresh: banner flags ride along on Error.lastContent.
        notes.historyHandler = { _, _, _ -> throw IOException("offline") }
        vm.refresh()
        val degraded = vm.state.first { it is HomeState.Error }

        assertEquals(1, (degraded as HomeState.Error).lastContent?.flags?.size)
    }
}
