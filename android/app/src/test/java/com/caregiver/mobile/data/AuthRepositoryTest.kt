package com.caregiver.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.LoginRequest
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Sign-in/out behavior with a fake backend: email normalization, token
 * persistence, 401 mapping, and the 401-on-authed-call logout rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private lateinit var file: File
    private lateinit var settings: SettingsStore
    private lateinit var tokens: TokenHolder
    private lateinit var fakeAuth: FakeAuthApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("auth-repo-test", ".preferences_pb")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
        tokens = TokenHolder()
        fakeAuth = FakeAuthApi()
        repository = AuthRepository(FakeBackendApis(fakeAuth), settings, tokens)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun signInNormalizesEmailAndStoresToken() = runTest {
        fakeAuth.loginHandler = { AuthResponse("tok") }

        val result = repository.signIn("  ALI@Example.COM ", "pw")

        assertEquals(SignInResult.SignedIn, result)
        assertEquals(LoginRequest("ali@example.com", "pw"), fakeAuth.lastLogin)
        assertEquals("tok", settings.token.first())
        assertEquals("tok", tokens.token)
        assertEquals("ali@example.com", settings.email.first())
    }

    @Test
    fun signIn401MapsToInvalidCredentialsAndStoresNothing() = runTest {
        fakeAuth.loginHandler = { throw FakeAuthApi.httpError(401, "INVALID_CREDENTIALS") }

        val result = repository.signIn("a@b.c", "wrong")

        assertEquals(SignInResult.InvalidCredentials, result)
        assertNull(settings.token.first())
        assertNull(tokens.token)
    }

    @Test
    fun signUpDuplicateMapsToRejectedWithServerCode() = runTest {
        fakeAuth.registerHandler = { throw FakeAuthApi.httpError(422, "DUPLICATE_EMAIL", "Email is already registered.") }

        val result = repository.signUp("a@b.c", "pw")

        assertTrue(result is SignInResult.Rejected)
        assertEquals("DUPLICATE_EMAIL", (result as SignInResult.Rejected).code)
        assertEquals("Email is already registered.", result.message)
        assertNull(settings.token.first())
    }

    @Test
    fun networkFailureMapsToUnreachable() = runTest {
        fakeAuth.loginHandler = { throw IOException("no route") }

        assertEquals(SignInResult.Unreachable, repository.signIn("a@b.c", "pw"))
    }

    @Test
    fun unexpectedFailureMapsToRejectedInsteadOfCrashing() = runTest {
        fakeAuth.loginHandler = { throw IllegalStateException("bad body") }

        val result = repository.signIn("a@b.c", "pw")

        assertTrue(result is SignInResult.Rejected)
        assertNull((result as SignInResult.Rejected).code)
        assertNull(settings.token.first())
    }

    @Test
    fun authed401ClearsTokenAndThrowsLoggedOut() = runTest {
        settings.setToken("stale")
        tokens.token = "stale"
        fakeAuth.loginHandler = { throw FakeAuthApi.httpError(401, "UNAUTHORIZED") }

        try {
            repository.authorized { auth().login(LoginRequest("a@b.c", "p")) }
            fail("expected LoggedOutException")
        } catch (e: LoggedOutException) {
            assertNull(settings.token.first())
            assertNull(tokens.token)
        }
    }

    @Test
    fun signOutClearsEverywhere() = runTest {
        settings.setToken("tok")
        tokens.token = "tok"
        settings.setEmail("a@b.c")

        repository.signOut()

        assertNull(settings.token.first())
        assertNull(tokens.token)
        assertNull(settings.email.first())
    }
}
