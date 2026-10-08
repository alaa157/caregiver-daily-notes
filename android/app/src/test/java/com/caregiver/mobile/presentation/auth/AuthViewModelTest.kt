package com.caregiver.mobile.presentation.auth

import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.core.network.TokenHolder
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
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
 * All auth logic is verified here on the JVM (no emulator on this box):
 * field validation, email normalization, result mapping, and the signed-in
 * event. The composables only render this state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var file: File
    private lateinit var fakeAuth: FakeAuthApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("auth-vm-test", ".preferences_pb")
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        fakeAuth = FakeAuthApi()
        repository = AuthRepository(FakeBackendApis(fakeAuth), settings, TokenHolder())
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun blankEmailBlocksSubmitWithRequiredError() = runTest {
        val vm = AuthViewModel(AuthMode.Login, repository, this)
        vm.onEmail("")
        vm.onPassword("pw")

        vm.submit()

        assertEquals(EmailError.Required, vm.state.value.emailError)
        assertNull(fakeAuth.lastLogin)
    }

    @Test
    fun invalidEmailBlocksSubmit() = runTest {
        val vm = AuthViewModel(AuthMode.Login, repository, this)
        vm.onEmail("not-an-email")
        vm.onPassword("pw")

        vm.submit()

        assertEquals(EmailError.Invalid, vm.state.value.emailError)
        assertNull(fakeAuth.lastLogin)
    }

    @Test
    fun blankPasswordBlocksSubmit() = runTest {
        val vm = AuthViewModel(AuthMode.Login, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("")

        vm.submit()

        assertEquals(PasswordError.Required, vm.state.value.passwordError)
        assertNull(fakeAuth.lastLogin)
    }

    @Test
    fun oversizedPasswordBlockedInBytesNotChars() = runTest {
        fakeAuth.loginHandler = { com.caregiver.mobile.data.api.AuthResponse("tok") }
        val vm = AuthViewModel(AuthMode.Login, repository, this)
        vm.onEmail("a@b.c")
        // 24 Arabic chars = 48 bytes: legal. 73 ASCII chars = 73 bytes: illegal.
        vm.onPassword("أ".repeat(24))
        vm.submit()
        assertNull(vm.state.value.passwordError)

        vm.onPassword("a".repeat(73))
        vm.submit()
        assertEquals(PasswordError.TooLong, vm.state.value.passwordError)
        assertNull(fakeAuth.lastLogin)
    }

    @Test
    fun validSubmitSignsInWithNormalizedEmail() = runTest {
        fakeAuth.loginHandler = { com.caregiver.mobile.data.api.AuthResponse("tok") }
        val vm = AuthViewModel(AuthMode.Login, repository, this)
        vm.onEmail("  ALI@Example.COM ")
        vm.onPassword("pw")

        vm.submit()
        vm.state.first { it.signedIn }

        assertEquals("ali@example.com", fakeAuth.lastLogin?.email)
        assertTrue(vm.state.value.signedIn)
    }

    @Test
    fun invalidCredentialsSurfaceFormError() = runTest {
        fakeAuth.loginHandler = { throw FakeAuthApi.httpError(401, "INVALID_CREDENTIALS") }
        val vm = AuthViewModel(AuthMode.Login, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("wrong")

        vm.submit()
        vm.state.first { it.formError != null }

        assertEquals(FormError.InvalidCredentials, vm.state.value.formError)
    }

    @Test
    fun registerMismatchBlocksSubmit() = runTest {
        val vm = AuthViewModel(AuthMode.Register, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("pw")
        vm.onConfirm("other")

        vm.submit()

        assertEquals(ConfirmError.Mismatch, vm.state.value.confirmError)
    }

    @Test
    fun registerDuplicateSurfacesServerMessage() = runTest {
        fakeAuth.registerHandler = {
            throw FakeAuthApi.httpError(422, "DUPLICATE_EMAIL", "Email is already registered.")
        }
        val vm = AuthViewModel(AuthMode.Register, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("pw")
        vm.onConfirm("pw")

        vm.submit()
        vm.state.first { it.formError != null }

        val error = vm.state.value.formError
        assertTrue(error is FormError.Rejected)
        assertEquals("Email is already registered.", (error as FormError.Rejected).message)
    }

    @Test
    fun networkFailureSurfacesUnreachable() = runTest {
        fakeAuth.loginHandler = { throw java.io.IOException("down") }
        val vm = AuthViewModel(AuthMode.Login, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("pw")

        vm.submit()
        vm.state.first { it.formError != null }

        assertEquals(FormError.Unreachable, vm.state.value.formError)
    }
}
