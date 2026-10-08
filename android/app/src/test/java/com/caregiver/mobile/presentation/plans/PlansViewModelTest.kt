package com.caregiver.mobile.presentation.plans

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakePlanApi
import com.caregiver.mobile.data.FakeRecipientApi
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.PlanDto
import com.caregiver.mobile.data.api.PlanVersionDto
import com.caregiver.mobile.data.api.RecipientDto
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
 * Plans list logic (missing page): rows with recipient names, latest
 * status, and version counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {

    private lateinit var file: File
    private lateinit var plans: FakePlanApi
    private lateinit var recipients: FakeRecipientApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("plans-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        plans = FakePlanApi()
        recipients = FakeRecipientApi()
        val apis = FakeBackendApis(FakeAuthApi()).also {
            it.planApi = plans
            it.recipientApi = recipients
        }
        repository = AuthRepository(apis, settings, TokenHolder())
        recipients.listHandler = { emptyList() }
        plans.listHandler = { emptyList() }
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun apis() = FakeBackendApis(FakeAuthApi()).also {
        it.planApi = plans
        it.recipientApi = recipients
    }

    private fun version(n: Int, status: String, items: List<String> = listOf("walk")) =
        PlanVersionDto(n, status, items, "2026-10-07T10:00:00", "$status via preview.")

    @Test
    fun rowsShowNamesLatestStatusAndCounts() = runTest {
        recipients.listHandler = { listOf(RecipientDto("r1", "Aisha", true)) }
        plans.listHandler = {
            listOf(
                PlanDto("p1", "r1", listOf(version(2, "Accepted"), version(1, "Suggested"))),
                PlanDto("p2", "r1", listOf(version(1, "Suggested"))),
            )
        }
        val vm = PlansViewModel(apis(), repository, this)

        val state = vm.state.first { it is PlansState.Content }
        val rows = (state as PlansState.Content).plans

        assertEquals(
            listOf(
                PlanRow("p1", "r1", "Aisha", "Accepted", 2),
                PlanRow("p2", "r1", "Aisha", "Suggested", 1),
            ),
            rows,
        )
    }

    @Test
    fun unknownRecipientFallsBackToId() = runTest {
        plans.listHandler = {
            listOf(PlanDto("p1", "gone", listOf(version(1, "Suggested"))))
        }
        val vm = PlansViewModel(apis(), repository, this)

        val state = vm.state.first { it is PlansState.Content }
        val row = (state as PlansState.Content).plans.single()

        assertEquals("gone", row.recipientName)
    }

    @Test
    fun emptyListIsContentNotError() = runTest {
        val vm = PlansViewModel(apis(), repository, this)

        val state = vm.state.first { it is PlansState.Content }

        assertTrue((state as PlansState.Content).plans.isEmpty())
    }

    @Test
    fun networkFailureSurfacesError() = runTest {
        plans.listHandler = { throw IOException("down") }
        val vm = PlansViewModel(apis(), repository, this)

        val state = vm.state.first { it is PlansState.Error }

        assertTrue(state is PlansState.Error)
    }

    @Test
    fun serverErrorSurfacesError() = runTest {
        plans.listHandler = { throw FakeAuthApi.httpError(500, "SERVER_ERROR") }
        val vm = PlansViewModel(apis(), repository, this)

        val state = vm.state.first { it is PlansState.Error }

        assertTrue(state is PlansState.Error)
    }
}
