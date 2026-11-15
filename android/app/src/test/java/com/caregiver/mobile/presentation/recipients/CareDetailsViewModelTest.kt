package com.caregiver.mobile.presentation.recipients

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.FakeRecipientAdmin
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.RecipientApi
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.UpdateRecipientRequest
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.recipient.CareDetails
import com.caregiver.mobile.data.recipient.CareDetailsField
import com.caregiver.mobile.data.recipient.CareDetailsIssue
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CareDetailsViewModelTest {
    private lateinit var file: File
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("care-details-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        repository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, TokenHolder())
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private suspend fun viewModel(
        scope: TestScope,
        recipient: RecipientDto = RecipientDto("r1", "Test recipient", true),
        api: CareDetailsRecipientApi = CareDetailsRecipientApi(recipient),
        admin: TestCareDetailsAdmin = TestCareDetailsAdmin(recipient),
    ): CareDetailsViewModel {
        val apis = FakeBackendApis(FakeAuthApi()).also { it.recipientApi = api }
        return CareDetailsViewModel("r1", apis, repository, admin, scope).also { vm ->
            vm.state.first { it.loaded || it.missing }
        }
    }

    @Test
    fun validationErrorsAreReportedWithoutSaving() = runTest {
        val admin = TestCareDetailsAdmin(RecipientDto("r1", "Test recipient", true))
        val vm = viewModel(this, admin = admin)
        vm.onDateOfBirth("not-a-date")
        vm.onEmergencyContactPhone("not a phone")

        vm.save()

        assertEquals(
            CareDetailsIssue.InvalidDate,
            vm.state.value.errors[CareDetailsField.DateOfBirth],
        )
        assertEquals(
            CareDetailsIssue.InvalidPhone,
            vm.state.value.errors[CareDetailsField.EmergencyContactPhone],
        )
        assertTrue(admin.calls.isEmpty())
        assertFalse(vm.state.value.busy)

        vm.onDateOfBirth("1980-03-04")
        assertNull(vm.state.value.errors[CareDetailsField.DateOfBirth])
        assertEquals(
            CareDetailsIssue.InvalidPhone,
            vm.state.value.errors[CareDetailsField.EmergencyContactPhone],
        )
    }

    @Test
    fun loadPopulatesAllCareDetailsFields() = runTest {
        val recipient = RecipientDto(
            "r1",
            "Test recipient",
            true,
            dateOfBirth = "1980-03-04",
            allergies = "Peanuts",
            conditions = "Asthma",
            emergencyContactName = "Mona",
            emergencyContactPhone = "+1 555 555 5555",
        )
        val vm = viewModel(this, recipient = recipient)

        assertTrue(vm.state.value.loaded)
        assertEquals("1980-03-04", vm.state.value.dateOfBirth)
        assertEquals("Peanuts", vm.state.value.allergies)
        assertEquals("Asthma", vm.state.value.conditions)
        assertEquals("Mona", vm.state.value.emergencyContactName)
        assertEquals("+1 555 555 5555", vm.state.value.emergencyContactPhone)
    }

    @Test
    fun saveStoresNormalizedValues() = runTest {
        val admin = TestCareDetailsAdmin(RecipientDto("r1", "Test recipient", true))
        val vm = viewModel(this, admin = admin)
        vm.onDateOfBirth(" 1980-03-04 ")
        vm.onAllergies(" Peanuts ")
        vm.onConditions(" Asthma ")
        vm.onEmergencyContactName(" Mona ")
        vm.onEmergencyContactPhone(" +1 555 555 5555 ")

        vm.save()
        vm.state.first { it.saved }

        assertEquals(
            CareDetails(
                dateOfBirth = "1980-03-04",
                allergies = "Peanuts",
                conditions = "Asthma",
                emergencyContactName = "Mona",
                emergencyContactPhone = "+1 555 555 5555",
            ),
            admin.calls.single().second,
        )
    }

    @Test
    fun blankFieldIsClearedWhenSaved() = runTest {
        val initial = RecipientDto("r1", "Test recipient", true, allergies = "Peanuts")
        val admin = TestCareDetailsAdmin(initial)
        val vm = viewModel(this, recipient = initial, admin = admin)
        vm.onAllergies("   ")

        vm.save()
        vm.state.first { it.saved }

        assertNull(admin.calls.single().second.allergies)
        assertEquals("", vm.state.value.allergies)
    }

    @Test
    fun busyGuardAllowsOnlyOneSave() = runTest {
        val gate = CompletableDeferred<RecipientDto>()
        val admin = TestCareDetailsAdmin(RecipientDto("r1", "Test recipient", true)).also {
            it.updateHandler = { _, _ -> gate.await() }
        }
        val vm = viewModel(this, admin = admin)
        vm.onConditions("Asthma")

        vm.save()
        vm.save()
        runCurrent()

        assertTrue(vm.state.value.busy)
        assertEquals(1, admin.calls.size)

        gate.complete(RecipientDto("r1", "Test recipient", true))
        vm.state.first { it.saved }
        assertEquals(1, admin.calls.size)
    }

    @Test
    fun archivedRecipientDoesNotSave() = runTest {
        val archived = RecipientDto("r1", "Test recipient", true, archivedAt = 123L)
        val admin = TestCareDetailsAdmin(archived)
        val vm = viewModel(this, recipient = archived, admin = admin)

        vm.save()

        assertTrue(vm.state.value.archived)
        assertTrue(admin.calls.isEmpty())
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun failingSaveSetsFailureState() = runTest {
        val admin = TestCareDetailsAdmin(RecipientDto("r1", "Test recipient", true)).also {
            it.updateHandler = { _, _ -> throw IOException("save failed") }
        }
        val vm = viewModel(this, admin = admin)

        vm.save()
        vm.state.first { it.failed }

        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.busy)

        vm.onAllergies("fixed")
        assertFalse(vm.state.value.failed)
    }

    @Test
    fun failedLoadMarksRecipientMissing() = runTest {
        val api = CareDetailsRecipientApi(RecipientDto("r1", "Test recipient", true)).also {
            it.getHandler = { throw IOException("load failed") }
        }
        val vm = viewModel(this, api = api)

        assertTrue(vm.state.value.missing)
        assertFalse(vm.state.value.loaded)
    }
}

private class CareDetailsRecipientApi(initial: RecipientDto) : RecipientApi {
    var getHandler: suspend (String) -> RecipientDto = { initial }

    override suspend fun list(): List<RecipientDto> = emptyList()

    override suspend fun create(body: CreateRecipientRequest): RecipientDto = error("unused")

    override suspend fun get(id: String): RecipientDto = getHandler(id)

    override suspend fun update(id: String, body: UpdateRecipientRequest): RecipientDto = error("unused")
}

private class TestCareDetailsAdmin(initial: RecipientDto) :
    RecipientAdmin by FakeRecipientAdmin(listOf(initial)) {
    val calls = mutableListOf<Pair<String, CareDetails>>()
    var updateHandler: suspend (String, CareDetails) -> RecipientDto = { id, _ ->
        initial.copy(id = id)
    }

    override suspend fun updateCareDetails(id: String, details: CareDetails): RecipientDto {
        calls += id to details
        return updateHandler(id, details)
    }
}
