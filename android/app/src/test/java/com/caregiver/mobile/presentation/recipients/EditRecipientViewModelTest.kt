package com.caregiver.mobile.presentation.recipients

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.RecipientApi
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.UpdateRecipientRequest
import com.caregiver.mobile.data.local.ArchivedRecipientException
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.local.RecipientDeletionCounts
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
class EditRecipientViewModelTest {
    private val recipient = RecipientDto("r1", "Aisha", true)
    private lateinit var file: File
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("edit-recipient-vm-test", ".preferences_pb")
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

    private fun viewModel(
        scope: TestScope,
        recipientApi: TestRecipientApi = TestRecipientApi(recipient),
        admin: TestRecipientAdmin = TestRecipientAdmin(),
    ): EditRecipientViewModel {
        val apis = FakeBackendApis(FakeAuthApi()).also { it.recipientApi = recipientApi }
        return EditRecipientViewModel("r1", apis, repository, admin, scope)
    }

    @Test
    fun unchangedNameSetsSavedIdWithoutLoadingDuplicateListOrRenaming() = runTest {
        val recipientApi = TestRecipientApi(recipient)
        val admin = TestRecipientAdmin()
        val vm = viewModel(this, recipientApi, admin)
        vm.state.first { it.loaded }
        vm.onName("  Aisha  ")

        vm.save()

        assertEquals("r1", vm.state.value.savedId)
        assertEquals(0, recipientApi.listCalls)
        assertTrue(admin.renameCalls.isEmpty())
    }

    @Test
    fun duplicateWarnsForAnotherPersonAndSaveAnywayContinues() = runTest {
        val other = RecipientDto("r2", "New Name", true, archivedAt = 123L)
        val recipientApi = TestRecipientApi(recipient).also {
            it.listHandler = { listOf(recipient, other) }
        }
        val admin = TestRecipientAdmin()
        val vm = viewModel(this, recipientApi, admin)
        vm.state.first { it.loaded }
        vm.onName("New Name")

        vm.save()
        vm.state.first { it.duplicate != null }

        assertEquals(DuplicateMatch("r2", "New Name", archived = true), vm.state.value.duplicate)
        assertTrue(admin.renameCalls.isEmpty())

        vm.saveAnyway()
        vm.state.first { it.savedId != null }

        assertNull(vm.state.value.duplicate)
        assertEquals(listOf("New Name"), admin.renameCalls.map { it.second })
    }

    @Test
    fun duplicateCheckExcludesThePersonBeingEdited() = runTest {
        val recipientApi = TestRecipientApi(recipient).also {
            it.listHandler = { listOf(recipient.copy(name = "New Name")) }
        }
        val admin = TestRecipientAdmin()
        val vm = viewModel(this, recipientApi, admin)
        vm.state.first { it.loaded }
        vm.onName("  New   Name ")

        vm.save()
        vm.state.first { it.savedId != null }

        assertNull(vm.state.value.duplicate)
        assertEquals(listOf("New Name"), admin.renameCalls.map { it.second })
    }

    @Test
    fun tooLongNameIsRejectedWithoutCheckingOrRenaming() = runTest {
        val recipientApi = TestRecipientApi(recipient)
        val admin = TestRecipientAdmin()
        val vm = viewModel(this, recipientApi, admin)
        vm.state.first { it.loaded }
        vm.onName("n".repeat(121))

        vm.save()

        assertEquals(NameError.TooLong, vm.state.value.nameError)
        assertEquals(0, recipientApi.listCalls)
        assertTrue(admin.renameCalls.isEmpty())
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun busyGuardAllowsOnlyOneRenameWhileFirstCallIsSuspended() = runTest {
        val gate = CompletableDeferred<RecipientDto>()
        val admin = TestRecipientAdmin().also {
            it.renameHandler = { _, _ -> gate.await() }
        }
        val recipientApi = TestRecipientApi(recipient).also {
            it.listHandler = { emptyList() }
        }
        val vm = viewModel(this, recipientApi, admin)
        vm.state.first { it.loaded }
        vm.onName("New Name")

        vm.save()
        vm.save()
        runCurrent()

        assertTrue(vm.state.value.busy)
        assertEquals(1, admin.renameCalls.size)

        gate.complete(recipient.copy(name = "New Name"))
        vm.state.first { it.savedId != null }
        assertEquals(1, admin.renameCalls.size)
    }

    @Test
    fun archivedRecipientExceptionMarksStateArchived() = runTest {
        val admin = TestRecipientAdmin().also {
            it.renameHandler = { _, _ -> throw ArchivedRecipientException("r1") }
        }
        val recipientApi = TestRecipientApi(recipient).also { it.listHandler = { emptyList() } }
        val vm = viewModel(this, recipientApi, admin)
        vm.state.first { it.loaded }
        vm.onName("New Name")

        vm.save()
        vm.state.first { it.archived }

        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.archived)
    }

    @Test
    fun missingRecipientExceptionMarksStateMissing() = runTest {
        val admin = TestRecipientAdmin().also {
            it.renameHandler = { _, _ -> throw NoSuchElementException("recipient") }
        }
        val recipientApi = TestRecipientApi(recipient).also { it.listHandler = { emptyList() } }
        val vm = viewModel(this, recipientApi, admin)
        vm.state.first { it.loaded }
        vm.onName("New Name")

        vm.save()
        vm.state.first { it.missing }

        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.missing)
    }

    @Test
    fun failingRenameSetsFailed() = runTest {
        val admin = TestRecipientAdmin().also {
            it.renameHandler = { _, _ -> throw IOException("save failed") }
        }
        val recipientApi = TestRecipientApi(recipient).also { it.listHandler = { emptyList() } }
        val vm = viewModel(this, recipientApi, admin)
        vm.state.first { it.loaded }
        vm.onName("New Name")

        vm.save()
        vm.state.first { it.failed }

        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.failed)
    }
}

private class TestRecipientApi(initial: RecipientDto) : RecipientApi {
    var getHandler: suspend (String) -> RecipientDto = { initial }
    var listHandler: suspend () -> List<RecipientDto> = { emptyList() }
    var listCalls = 0

    override suspend fun list(): List<RecipientDto> {
        listCalls++
        return listHandler()
    }

    override suspend fun create(body: CreateRecipientRequest): RecipientDto = error("unused")

    override suspend fun get(id: String): RecipientDto = getHandler(id)

    override suspend fun update(id: String, body: UpdateRecipientRequest): RecipientDto =
        error("unused")
}

private class TestRecipientAdmin : RecipientAdmin {
    val renameCalls = mutableListOf<Pair<String, String>>()
    var renameHandler: suspend (String, String) -> RecipientDto = { id, name ->
        RecipientDto(id, name, true)
    }

    override suspend fun rename(id: String, name: String): RecipientDto {
        renameCalls += id to name
        return renameHandler(id, name)
    }

    override suspend fun updateProfile(
        id: String,
        name: String,
        relationship: String?,
    ): RecipientDto = error("unused")

    override suspend fun updateCareDetails(
        id: String,
        details: com.caregiver.mobile.data.recipient.CareDetails,
    ): RecipientDto = error("unused")

    override suspend fun archive(id: String): RecipientDto = error("unused")

    override suspend fun unarchive(id: String): RecipientDto = error("unused")

    override suspend fun deleteArchived(id: String): Unit = error("unused")

    override suspend fun counts(id: String): RecipientDeletionCounts = error("unused")
}
