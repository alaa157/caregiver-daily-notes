package com.caregiver.mobile.presentation.auth

import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.SignInResult
import com.caregiver.mobile.data.code
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import com.caregiver.mobile.data.accounts.PasswordProblem
import com.caregiver.mobile.core.network.TokenHolder
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun malformedAddressesBlocked() = runTest {
        listOf("x@", "@example.com", "plain", "a @b.c", "a@b..c", "a@.b.c").forEach { bad ->
            val vm = AuthViewModel(AuthMode.Login, repository, this)
            vm.onEmail(bad)
            vm.onPassword("pw")

            vm.submit()

            assertEquals("expected Invalid for $bad", EmailError.Invalid, vm.state.value.emailError)
        }
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
    fun registerDuplicateSurfacesLocalizedDuplicate() = runTest {
        fakeAuth.registerHandler = {
            throw FakeAuthApi.httpError(422, "DUPLICATE_EMAIL", "Email is already registered.")
        }
        val vm = AuthViewModel(AuthMode.Register, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("pw")

        vm.submit()
        vm.state.first { it.formError != null }

        assertEquals(FormError.DuplicateEmail, vm.state.value.formError)
    }

    @Test
    fun unknownServerCodeSurfacesGeneric() = runTest {
        fakeAuth.registerHandler = {
            throw FakeAuthApi.httpError(422, "SOME_FUTURE_CODE", "Some future English message.")
        }
        val vm = AuthViewModel(AuthMode.Register, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("pw")

        vm.submit()
        vm.state.first { it.formError != null }

        assertEquals(FormError.Generic, vm.state.value.formError)
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

    @Test
    fun localRegisterRequiresDisplayName() = runTest {
        var calls = 0
        val vm = AuthViewModel(AuthMode.Register, repository, this, registerAccount = { _, _, _ ->
            calls++
            Result.success(account())
        })
        vm.onEmail("a@b.c")
        vm.onPassword("Password1")

        vm.submit()

        assertEquals(NameError.Required, vm.state.value.nameError)
        assertEquals(0, calls)
    }

    @Test
    fun localRegisterRejectsBlankAndTooLongNames() = runTest {
        val vm = AuthViewModel(AuthMode.Register, repository, this, registerAccount = { _, _, _ ->
            Result.success(account())
        })
        vm.onEmail("a@b.c")
        vm.onPassword("Password1")

        vm.onDisplayName("   ")
        vm.submit()
        assertEquals(NameError.Required, vm.state.value.nameError)

        vm.onDisplayName("n".repeat(81))
        vm.submit()
        assertEquals(NameError.TooLong, vm.state.value.nameError)
    }

    @Test
    fun localRegisterPassesTrimmedNameAndSkipsServerRegister() = runTest {
        var serverRegisterCalls = 0
        fakeAuth.registerHandler = {
            serverRegisterCalls++
            com.caregiver.mobile.data.api.AuthResponse("server-token")
        }
        var received: Triple<String, String, String>? = null
        val vm = AuthViewModel(AuthMode.Register, repository, this, registerAccount = { name, email, password ->
            received = Triple(name, email, password)
            Result.success(account(displayName = name))
        })
        vm.onDisplayName("  Aisha  ")
        vm.onEmail("a@b.c")
        vm.onPassword("Password1")

        vm.submit()
        vm.state.first { it.signedIn }

        assertEquals(Triple("Aisha", "a@b.c", "Password1"), received)
        assertEquals(0, serverRegisterCalls)
        assertTrue(vm.state.value.signedIn)
    }

    @Test
    fun localRegisterEmailTakenMapsToDuplicateEmail() = runTest {
        val vm = AuthViewModel(AuthMode.Register, repository, this, registerAccount = { _, _, _ ->
            Result.failure(AccountException(AccountError.EmailTaken))
        })
        vm.onDisplayName("Aisha")
        vm.onEmail("a@b.c")
        vm.onPassword("Password1")

        vm.submit()
        vm.state.first { it.formError != null }

        assertEquals(FormError.DuplicateEmail, vm.state.value.formError)
        assertTrue(!vm.state.value.signedIn)
    }

    @Test
    fun localRegisterWeakPasswordSurfacesPolicyErrorWithoutFormError() = runTest {
        val vm = AuthViewModel(AuthMode.Register, repository, this, registerAccount = { _, _, _ ->
            Result.failure(AccountException(AccountError.WeakPassword(PasswordProblem.TooShort)))
        })
        vm.onDisplayName("Aisha")
        vm.onEmail("a@b.c")
        vm.onPassword("short")

        vm.submit()
        vm.state.first { it.passwordError != null }

        assertEquals(PasswordError.Policy(PasswordProblem.TooShort), vm.state.value.passwordError)
        assertNull(vm.state.value.formError)
    }

    @Test
    fun localRegisterMapsEveryPasswordPolicyProblem() = runTest {
        listOf(
            PasswordProblem.TooShort,
            PasswordProblem.TooLong,
            PasswordProblem.MissingLetter,
            PasswordProblem.MissingDigit,
            PasswordProblem.OnlyWhitespace,
        ).forEach { problem ->
            val vm = AuthViewModel(AuthMode.Register, repository, this, registerAccount = { _, _, _ ->
                Result.failure(AccountException(AccountError.WeakPassword(problem)))
            })
            vm.onDisplayName("Aisha")
            vm.onEmail("a@b.c")
            vm.onPassword("Password1")

            vm.submit()
            vm.state.first { it.passwordError != null }

            assertEquals("expected $problem", PasswordError.Policy(problem), vm.state.value.passwordError)
            assertNull("no form error for $problem", vm.state.value.formError)
        }
    }

    @Test
    fun repositoryEmailTakenMapsToDuplicateEmail() = runTest {
        fakeAuth.registerHandler = {
            throw FakeAuthApi.httpError(422, "EMAIL_TAKEN", "Email is already registered.")
        }
        val vm = AuthViewModel(AuthMode.Register, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("Password1")

        vm.submit()
        vm.state.first { it.formError != null }

        assertEquals(FormError.DuplicateEmail, vm.state.value.formError)
        assertNull(vm.state.value.emailError)
        assertNull(vm.state.value.passwordError)
    }

    @Test
    fun repositoryWeakPasswordCodeMapsBackToTypedPolicyError() = runTest {
        listOf(
            PasswordProblem.TooShort,
            PasswordProblem.TooLong,
            PasswordProblem.MissingLetter,
            PasswordProblem.MissingDigit,
            PasswordProblem.OnlyWhitespace,
        ).forEach { problem ->
            // The producer puts problem.code() in Rejected's message field.
            fakeAuth.registerHandler = {
                throw FakeAuthApi.httpError(422, "WEAK_PASSWORD", problem.code())
            }
            val vm = AuthViewModel(AuthMode.Register, repository, this)
            vm.onEmail("a@b.c")
            vm.onPassword("Password1")

            vm.submit()
            vm.state.first { it.passwordError != null }

            assertEquals("problem $problem", PasswordError.Policy(problem), vm.state.value.passwordError)
            assertNull("no form error for $problem", vm.state.value.formError)
        }
    }

    @Test
    fun repositoryInvalidEmailCodeMapsToEmailError() = runTest {
        // The address is shape-valid so the client check lets it through and
        // the rejection code is the only signal, as on the local path.
        fakeAuth.registerHandler = {
            throw FakeAuthApi.httpError(422, "INVALID_EMAIL", "Enter a valid email address.")
        }
        val vm = AuthViewModel(AuthMode.Register, repository, this)
        vm.onEmail("a@b.c")
        vm.onPassword("Password1")

        vm.submit()
        vm.state.first { it.emailError != null }

        assertEquals(EmailError.Invalid, vm.state.value.emailError)
        assertNull(vm.state.value.formError)
    }

    @Test
    fun repositoryUnmappedCodesFallBackToGeneric() = runTest {
        listOf("ACCOUNT_NOT_FOUND", "CANNOT_REMOVE_ACTIVE", "SOME_FUTURE_CODE").forEach { code ->
            fakeAuth.registerHandler = {
                throw FakeAuthApi.httpError(422, code, "A human-readable message that is never rendered.")
            }
            val vm = AuthViewModel(AuthMode.Register, repository, this)
            vm.onEmail("a@b.c")
            vm.onPassword("Password1")

            vm.submit()
            vm.state.first { it.formError != null }

            assertEquals("code $code", FormError.Generic, vm.state.value.formError)
            assertNull("no email error for $code", vm.state.value.emailError)
            assertNull("no password error for $code", vm.state.value.passwordError)
        }
    }

    // ── #880: one-tap offline demo ─────────────────────────────────────────

    @Test
    fun demoEntryEntersDemoWithoutTouchingCredentialsOrSignIn() = runTest {
        var demoCalls = 0
        val vm = AuthViewModel(AuthMode.Login, repository, this, enterDemoSession = {
            demoCalls++
            SignInResult.SignedIn
        })
        // A stale form must be ignored — the demo path never submits it.
        vm.onEmail("someone@example.com")
        vm.onPassword("whatever1")

        vm.demoSignIn()
        advanceUntilIdle()

        assertEquals(1, demoCalls)
        assertTrue(vm.state.value.signedIn)
        assertNull(vm.state.value.formError)
        assertNull(fakeAuth.lastLogin)
        assertEquals("", vm.state.value.email)
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun demoEntryFailureIsGenericNeverInvalidCredentials() = runTest {
        val vm = AuthViewModel(AuthMode.Login, repository, this, enterDemoSession = {
            SignInResult.Rejected(null, "Offline demo unavailable.")
        })

        vm.demoSignIn()
        advanceUntilIdle()

        assertFalse(vm.state.value.signedIn)
        assertEquals(FormError.Generic, vm.state.value.formError)
        assertNull(fakeAuth.lastLogin)
    }

    private fun account(displayName: String = "Aisha"): Account = Account(
        id = "account-1",
        email = "a@b.c",
        displayName = displayName,
        createdAt = 1L,
        lastUsedAt = 1L,
    )
}
