package com.caregiver.mobile.presentation.plans

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakePlanApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.AppendVersionRequest
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plan edit logic (board 13): item editing plus a single version append.
 * The backend generates the version reason itself, so the client sends only
 * the edited items with the edited-and-accepted status.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanEditViewModelTest {

    private lateinit var file: File
    private lateinit var plans: FakePlanApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("plan-edit-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        plans = FakePlanApi()
        val apis = FakeBackendApis(FakeAuthApi()).also { it.planApi = plans }
        repository = AuthRepository(apis, settings, TokenHolder())
        plans.listHandler = {
            listOf(
                PlanDto(
                    "p1", "r1",
                    listOf(
                        PlanVersionDto(1, "Suggested", listOf("walk", "read"), "2026-10-07T10:00:00", "r"),
                    ),
                ),
            )
        }
        plans.appendHandler = { _, _ ->
            PlanDto("p1", "r1", listOf(PlanVersionDto(2, "Edited-and-Accepted", listOf("walk"), "t", "r")))
        }
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun apis() = FakeBackendApis(FakeAuthApi()).also { it.planApi = plans }

    @Test
    fun loadsLatestItems() = runTest {
        val vm = PlanEditViewModel("p1", apis(), repository, this)

        val lines = vm.state.first { it is PlanEditState.Content }

        assertEquals(listOf("walk", "read"), (lines as PlanEditState.Content).lines)
    }

    @Test
    fun editAddRemoveLines() = runTest {
        val vm = PlanEditViewModel("p1", apis(), repository, this)
        vm.state.first { it is PlanEditState.Content }

        vm.updateLine(0, "stroll")
        vm.addLine()
        vm.updateLine(2, "tea")
        vm.removeLine(1)

        val state = vm.state.value as PlanEditState.Content
        assertEquals(listOf("stroll", "tea"), state.lines)
    }

    @Test
    fun saveAppendsEditedVersionAndSignalsSaved() = runTest {
        val vm = PlanEditViewModel("p1", apis(), repository, this)
        vm.state.first { it is PlanEditState.Content }

        vm.updateLine(0, "stroll")
        vm.save()
        vm.saved.first { it }

        assertEquals(
            "p1" to AppendVersionRequest("Edited-and-Accepted", listOf("stroll", "read")),
            plans.lastAppend,
        )
        assertEquals(true, vm.saved.value)
    }

    @Test
    fun blankLinesFilteredOnSave() = runTest {
        val vm = PlanEditViewModel("p1", apis(), repository, this)
        vm.state.first { it is PlanEditState.Content }

        vm.updateLine(1, "   ")
        vm.save()
        vm.saved.first { it }

        assertEquals(listOf("walk"), plans.lastAppend?.second?.items)
    }

    @Test
    fun saveFailureKeepsLinesClearsBusyAndFlagsError() = runTest {
        plans.appendHandler = { _, _ -> throw IOException("down") }
        val vm = PlanEditViewModel("p1", apis(), repository, this)
        vm.state.first { it is PlanEditState.Content }

        vm.updateLine(0, "stroll")
        vm.save()
        vm.saveFailed.first { it }

        assertEquals(listOf("stroll", "read"), (vm.state.value as PlanEditState.Content).lines)
        assertFalse(vm.busy.value)
        assertTrue(vm.saveFailed.value)
    }
}
