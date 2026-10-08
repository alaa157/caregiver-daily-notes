package com.caregiver.mobile.presentation.plans

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakePlanApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.PlanDto
import com.caregiver.mobile.data.api.PlanVersionDto
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plan proposal logic (board 12): newest-first versions, the three
 * transitions, and localized illegal-transition mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanDetailViewModelTest {

    private lateinit var file: File
    private lateinit var plans: FakePlanApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("plan-detail-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        plans = FakePlanApi()
        val apis = FakeBackendApis(FakeAuthApi()).also { it.planApi = plans }
        repository = AuthRepository(apis, settings, TokenHolder())
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun apis() = FakeBackendApis(FakeAuthApi()).also { it.planApi = plans }

    private fun version(n: Int, status: String) =
        PlanVersionDto(n, status, listOf("walk"), "2026-10-07T10:00:00", "$status via preview.")

    private fun suggested() = listOf(PlanDto("p1", "r1", listOf(version(1, "Suggested"))))

    private suspend fun content(vm: PlanDetailViewModel): PlanDetail {
        val state = vm.state.first { it is PlanDetailState.Content || it is PlanDetailState.Error }
        assertTrue("expected Content, was $state", state is PlanDetailState.Content)
        return (state as PlanDetailState.Content).detail
    }

    @Test
    fun versionsNewestFirst() = runTest {
        plans.listHandler = {
            listOf(PlanDto("p1", "r1", listOf(version(1, "Suggested"), version(3, "Accepted"), version(2, "Suggested"))))
        }
        val vm = PlanDetailViewModel("p1", apis(), repository, this)

        val detail = content(vm)

        assertEquals(listOf(3, 2, 1), detail.versions.map { it.version })
    }

    @Test
    fun unknownPlanSurfacesError() = runTest {
        plans.listHandler = { emptyList() }
        val vm = PlanDetailViewModel("nope", apis(), repository, this)

        val state = vm.state.first { it is PlanDetailState.Error }

        assertTrue(state is PlanDetailState.Error)
    }

    @Test
    fun acceptTransitionsAndReloads() = runTest {
        var status = "Suggested"
        plans.listHandler = { listOf(PlanDto("p1", "r1", listOf(version(1, status)))) }
        plans.acceptHandler = {
            status = "Accepted"
            PlanDto("p1", "r1", listOf(version(1, status)))
        }
        val vm = PlanDetailViewModel("p1", apis(), repository, this)
        content(vm)

        vm.transition(PlanAction.Accept)
        val reloaded = vm.state.first {
            it is PlanDetailState.Content && it.detail.versions.single().status == "Accepted"
        } as PlanDetailState.Content

        assertEquals("p1" to "accept", plans.lastTransition)
        assertEquals("Accepted", reloaded.detail.versions.single().status)
        assertFalse(vm.busy.value)
    }

    @Test
    fun illegalTransitionSurfacesLocalizedErrorAndClearsBusy() = runTest {
        plans.listHandler = { suggested() }
        plans.acceptHandler = {
            throw FakeAuthApi.httpError(422, "VALIDATION_ERROR", "Illegal transition from Accepted.")
        }
        val vm = PlanDetailViewModel("p1", apis(), repository, this)
        content(vm)

        vm.transition(PlanAction.Accept)
        vm.actionError.first { it != null }

        assertEquals(PlanActionError.IllegalTransition, vm.actionError.value)
        assertFalse(vm.busy.value)
    }

    @Test
    fun networkFailureSurfacesFailedError() = runTest {
        plans.listHandler = { suggested() }
        plans.dismissHandler = { throw IOException("down") }
        val vm = PlanDetailViewModel("p1", apis(), repository, this)
        content(vm)

        vm.transition(PlanAction.Dismiss)
        vm.actionError.first { it != null }

        assertEquals(PlanActionError.Failed, vm.actionError.value)
        assertFalse(vm.busy.value)
    }

    @Test
    fun unauthorizedTransitionLeavesLoadingWithoutCrashing() = runTest {
        plans.listHandler = { suggested() }
        plans.archiveHandler = { throw com.caregiver.mobile.data.LoggedOutException() }
        val vm = PlanDetailViewModel("p1", apis(), repository, this)
        content(vm)

        vm.transition(PlanAction.Archive)
        vm.busy.first { !it }

        assertNull(vm.actionError.value)
        assertFalse(vm.busy.value)
    }
}
