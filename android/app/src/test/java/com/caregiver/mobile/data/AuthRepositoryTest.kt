package com.caregiver.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.api.AuthApi
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.NoteApi
import com.caregiver.mobile.data.api.PlanApi
import com.caregiver.mobile.data.api.RecipientApi
import com.caregiver.mobile.data.api.RegisterRequest
import com.caregiver.mobile.data.api.SummaryApi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

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
    }

    @Test
    fun signIn401MapsToInvalidCredentialsAndStoresNothing() = runTest {
        fakeAuth.loginHandler = { throw httpError(401, "INVALID_CREDENTIALS") }

        val result = repository.signIn("a@b.c", "wrong")

        assertEquals(SignInResult.InvalidCredentials, result)
        assertNull(settings.token.first())
        assertNull(tokens.token)
    }

    @Test
    fun signUpDuplicateMapsToRejectedWithServerMessage() = runTest {
        fakeAuth.registerHandler = { throw httpError(422, "DUPLICATE_EMAIL", "Email is already registered.") }

        val result = repository.signUp("a@b.c", "pw")

        assertTrue(result is SignInResult.Rejected)
        assertEquals("Email is already registered.", (result as SignInResult.Rejected).message)
        assertNull(settings.token.first())
    }

    @Test
    fun networkFailureMapsToUnreachable() = runTest {
        fakeAuth.loginHandler = { throw IOException("no route") }

        assertEquals(SignInResult.Unreachable, repository.signIn("a@b.c", "pw"))
    }

    @Test
    fun authed401ClearsTokenAndThrowsLoggedOut() = runTest {
        settings.setToken("stale")
        tokens.token = "stale"
        fakeAuth.loginHandler = { throw httpError(401, "UNAUTHORIZED") }

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

        repository.signOut()

        assertNull(settings.token.first())
        assertNull(tokens.token)
    }

    private fun httpError(code: Int, errorCode: String, message: String = errorCode): HttpException =
        HttpException(
            Response.error<String>(
                code,
                """{"code":"$errorCode","message":"$message"}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )

    private class FakeAuthApi : AuthApi {
        var lastLogin: LoginRequest? = null
        var loginHandler: suspend (LoginRequest) -> AuthResponse = { error("no handler") }
        var registerHandler: suspend (RegisterRequest) -> AuthResponse = { error("no handler") }

        override suspend fun login(body: LoginRequest): AuthResponse {
            lastLogin = body
            return loginHandler(body)
        }

        override suspend fun register(body: RegisterRequest): AuthResponse = registerHandler(body)
    }

    private class FakeBackendApis(private val authApi: AuthApi) : BackendApis {
        override suspend fun auth(): AuthApi = authApi
        override suspend fun recipients(): RecipientApi = error("unused")
        override suspend fun notes(): NoteApi = error("unused")
        override suspend fun summaries(): SummaryApi = error("unused")
        override suspend fun plans(): PlanApi = error("unused")
    }
}
