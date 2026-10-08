package com.caregiver.mobile.presentation.summary

import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeSummaryApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.api.SummaryRequest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
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
 * Summary (boards 10-11): 7/14/30-day periods, flag/evidence/uncertainty
 * passthrough, and the AI-unavailable path with notes-safe reassurance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

    private lateinit var file: File
    private lateinit var summaries: FakeSummaryApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("summary-vm-test", ".preferences_pb")
        val settings = SettingsStore(
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

    private fun apis() = FakeBackendApis(FakeAuthApi()).also { it.summaryApi = summaries }

    private fun summary() = SummaryDto(
        id = "s1", recipientId = "r1", periodDays = 7, text = "Reviewed 3 day(s) of notes.",
        redFlags = listOf("FALL_REPORTED", "MEDICATION_UNCLEAR"),
        evidence = listOf(SummaryDto.EvidenceDto("n1", "fell near the bathroom")),
        uncertainties = listOf(SummaryDto.UncertaintyDto("medication", "unsure on 2026-10-06")),
    )

    private suspend fun content(vm: SummaryViewModel): SummaryDto {
        val state = vm.state.first { it is SummaryState.Content || it !is SummaryState.Loading }
        assertTrue("expected Content, was $state", state is SummaryState.Content)
        return (state as SummaryState.Content).summary
    }

    @Test
    fun defaultPeriodIsSevenDays() = runTest {
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", apis(), repository, this)

        content(vm)

        assertEquals(SummaryRequest("r1", 7), summaries.lastSummarize?.let {
            SummaryRequest(it.recipientId, it.periodDays)
        })
        assertEquals(7, vm.period.value)
    }

    @Test
    fun periodSwitchReloadsWithNewPeriod() = runTest {
        summaries.summarizeHandler = { summary().copy(periodDays = it.periodDays) }
        val vm = SummaryViewModel("r1", apis(), repository, this)
        content(vm)

        vm.setPeriod(30)
        vm.state.first {
            it is SummaryState.Content && it.summary.periodDays == 30
        }

        assertEquals(30, summaries.lastSummarize?.periodDays)
    }

    @Test
    fun flagsEvidenceAndUncertaintiesPassThrough() = runTest {
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", apis(), repository, this)

        val loaded = content(vm)

        assertEquals(listOf("FALL_REPORTED", "MEDICATION_UNCLEAR"), loaded.redFlags)
        assertEquals("n1", loaded.evidence.single().noteId)
        assertEquals("fell near the bathroom", loaded.evidence.single().quote)
        assertEquals("medication", loaded.uncertainties.single().topic)
    }

    @Test
    fun networkFailureSurfacesAiUnavailable() = runTest {
        summaries.summarizeHandler = { throw IOException("down") }
        val vm = SummaryViewModel("r1", apis(), repository, this)

        val state = vm.state.first { it is SummaryState.AiUnavailable }

        assertTrue(state is SummaryState.AiUnavailable)
        assertEquals(null, (state as SummaryState.AiUnavailable).last)
    }

    @Test
    fun httpFailureCarriesCodeNeverRawMessage() = runTest {
        summaries.summarizeHandler = {
            throw FakeAuthApi.httpError(422, "VALIDATION_ERROR", "No notes in range.")
        }
        val vm = SummaryViewModel("r1", apis(), repository, this)

        val state = vm.state.first { it is SummaryState.Rejected }

        assertEquals("VALIDATION_ERROR", (state as SummaryState.Rejected).code)
    }

    @Test
    fun refreshKeepsFlagsVisibleOnFailure() = runTest {
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", apis(), repository, this)
        val before = content(vm)
        assertEquals(2, before.redFlags.size)

        summaries.summarizeHandler = { throw IOException("down") }
        vm.refresh()
        val state = vm.state.first { it is SummaryState.AiUnavailable }

        assertEquals(before.redFlags, (state as SummaryState.AiUnavailable).last?.redFlags)
    }

    @Test
    fun failedRefreshKeepsFlagsOnRejected() = runTest {
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", apis(), repository, this)
        content(vm)

        summaries.summarizeHandler = {
            throw FakeAuthApi.httpError(500, "SERVER_ERROR")
        }
        vm.refresh()
        val state = vm.state.first { it is SummaryState.Rejected }

        assertEquals(
            listOf("FALL_REPORTED", "MEDICATION_UNCLEAR"),
            (state as SummaryState.Rejected).last?.redFlags,
        )
    }

    @Test
    fun successfulRefreshClearsFlagsWhenNoneReported() = runTest {
        summaries.summarizeHandler = { summary() }
        val vm = SummaryViewModel("r1", apis(), repository, this)
        content(vm)

        summaries.summarizeHandler = { summary().copy(redFlags = emptyList()) }
        vm.refresh()
        val state = vm.state.first {
            it is SummaryState.Content && !it.refreshing
        } as SummaryState.Content

        assertTrue(state.summary.redFlags.isEmpty())
    }
}
