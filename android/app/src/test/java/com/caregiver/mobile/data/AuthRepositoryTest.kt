package com.caregiver.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.data.accounts.AccountDao
import com.caregiver.mobile.data.accounts.AccountEntity
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import com.caregiver.mobile.data.accounts.AccountManager
import com.caregiver.mobile.data.accounts.PasswordHasher
import com.caregiver.mobile.data.accounts.PasswordProblem
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // ── #598: AccountError -> SignInResult mapping ─────────────────────────

    @Test
    fun everyAccountErrorVariantHasAnExplicitMapping() {
        assertEquals(SignInResult.InvalidCredentials, AccountError.InvalidCredentials.toSignInResult())
        assertEquals(SignInResult.Rejected("EMAIL_TAKEN", "Email is already registered."), AccountError.EmailTaken.toSignInResult())
        assertEquals(SignInResult.Rejected("WEAK_PASSWORD", "TOO_SHORT"), AccountError.WeakPassword(PasswordProblem.TooShort).toSignInResult())
        assertEquals(SignInResult.Rejected("WEAK_PASSWORD", "TOO_LONG"), AccountError.WeakPassword(PasswordProblem.TooLong).toSignInResult())
        assertEquals(SignInResult.Rejected("WEAK_PASSWORD", "MISSING_LETTER"), AccountError.WeakPassword(PasswordProblem.MissingLetter).toSignInResult())
        assertEquals(SignInResult.Rejected("WEAK_PASSWORD", "MISSING_DIGIT"), AccountError.WeakPassword(PasswordProblem.MissingDigit).toSignInResult())
        assertEquals(SignInResult.Rejected("WEAK_PASSWORD", "ONLY_WHITESPACE"), AccountError.WeakPassword(PasswordProblem.OnlyWhitespace).toSignInResult())
        assertEquals(SignInResult.Rejected("INVALID_EMAIL", "Enter a valid email address."), AccountError.InvalidEmail.toSignInResult())
        assertEquals(SignInResult.Rejected("ACCOUNT_NOT_FOUND", "Account not found."), AccountError.AccountNotFound.toSignInResult())
        assertEquals(SignInResult.Rejected("CANNOT_REMOVE_ACTIVE", "The active account cannot be removed."), AccountError.CannotRemoveActive.toSignInResult())
    }

    @Test
    fun signInResultTypeIsUnchanged() {
        // The four existing variants still construct with the original shapes.
        val variants = listOf<SignInResult>(
            SignInResult.SignedIn,
            SignInResult.InvalidCredentials,
            SignInResult.Rejected("CODE", "message"),
            SignInResult.Unreachable,
        )
        assertEquals(4, variants.size)
    }

    @Test
    fun serverModeTwoArgSignUpStillDelegatesWithoutAName() = runTest {
        var capturedEmail: String? = null
        var capturedPassword: String? = null
        fakeAuth.registerHandler = { request ->
            capturedEmail = request.email
            capturedPassword = request.password
            AuthResponse("tok")
        }

        val result = repository.signUp("  Ali@Example.COM ", "pw")

        assertEquals(SignInResult.SignedIn, result)
        assertEquals("ali@example.com", capturedEmail)
        assertEquals("pw", capturedPassword)
    }

    @Test
    fun localTokenIsAnOpaqueHandleNotACredential() {
        val token = AuthRepository.localToken("acc-123")

        assertEquals("local:acc-123", token)
        assertFalse(token.contains("goodpass1"))
        assertFalse(token.contains("hash"))
        assertFalse(token.contains("salt"))
    }

    @Test
    fun localModeRegisterSignInAndDuplicateAllMapCorrectly() = runTest {
        val dao = InMemoryAccountDao()
        val manager = AccountManager(dao = dao, hasher = PasswordHasher(iterations = 10))
        val localRepository = AuthRepository(FakeBackendApis(FakeAuthApi()), settings, tokens, accountManager = manager)

        assertEquals(SignInResult.SignedIn, localRepository.signUp("Amina", "amina@example.com", "goodpass1"))
        val accountId = dao.rows.values.single().id
        assertEquals(AuthRepository.localToken(accountId), settings.token.first())
        assertTrue(settings.token.first()!!.startsWith("local:"))

        tokens.token = null
        assertEquals(SignInResult.SignedIn, localRepository.signIn("  AMINA@EXAMPLE.COM ", "goodpass1"))
        assertNotNull(tokens.token)

        assertEquals(SignInResult.InvalidCredentials, localRepository.signIn("amina@example.com", "wrongpass1"))
        assertEquals(
            SignInResult.Rejected("EMAIL_TAKEN", "Email is already registered."),
            localRepository.signUp("Other", "amina@example.com", "goodpass1"),
        )
    }

    // ── #880: one-tap offline demo ─────────────────────────────────────────

    @Test
    fun enterDemoSeedsACredentialFreeLocalAccountWithoutSigningIn() = runTest {
        val dao = InMemoryAccountDao()
        val manager = AccountManager(dao = dao, hasher = PasswordHasher(iterations = 10))
        val demoRepository = AuthRepository(FakeBackendApis(fakeAuth), settings, tokens, accountManager = manager)

        val result = demoRepository.enterDemo()

        assertEquals(SignInResult.SignedIn, result)
        val seeded = dao.rows.values.single()
        assertEquals(AuthRepository.normalize(AuthRepository.DEMO_EMAIL), seeded.email)
        assertEquals(AuthRepository.DEMO_DISPLAY_NAME, seeded.displayName)
        // No credential form, and no ordinary sign-in with hard-coded values.
        assertNull(fakeAuth.lastLogin)
        assertEquals(AuthRepository.localToken(seeded.id), settings.token.first())
        assertEquals(AuthRepository.normalize(AuthRepository.DEMO_EMAIL), settings.email.first())
    }

    @Test
    fun enterDemoReusesAnExistingDemoAccountWithoutDuplicatingIt() = runTest {
        val dao = InMemoryAccountDao()
        val manager = AccountManager(dao = dao, hasher = PasswordHasher(iterations = 10))
        val demoRepository = AuthRepository(FakeBackendApis(fakeAuth), settings, tokens, accountManager = manager)
        assertEquals(SignInResult.SignedIn, demoRepository.enterDemo())
        val firstId = dao.rows.values.single().id
        settings.setToken("stale")
        tokens.token = "stale"

        val result = demoRepository.enterDemo()

        assertEquals(SignInResult.SignedIn, result)
        assertEquals(1, dao.rows.size)
        assertEquals(firstId, dao.rows.values.single().id)
        assertNull(fakeAuth.lastLogin)
        assertEquals(AuthRepository.localToken(firstId), settings.token.first())
    }

    @Test
    fun enterDemoWithoutALocalStoreNeverSignsInWithHardcodedCredentials() = runTest {
        // No accountManager: the demo cannot work in this graph, so it must
        // not fall back to signIn("demo@example.com", ...) and show an error.
        val result = repository.enterDemo()

        assertEquals(SignInResult.Unreachable, result)
        assertNull(fakeAuth.lastLogin)
        assertNull(settings.token.first())
    }

    /** Minimal in-memory DAO for the local-mode wiring test. */
    private class InMemoryAccountDao : AccountDao {
        val rows = linkedMapOf<String, AccountEntity>()

        override suspend fun insert(account: AccountEntity) {
            check(rows.values.none { it.email == account.email }) { "duplicate email" }
            rows[account.id] = account
        }

        override suspend fun findByEmail(email: String): AccountEntity? = rows.values.firstOrNull { it.email == email }
        override suspend fun findById(id: String): AccountEntity? = rows[id]
        override suspend fun listAll(): List<AccountEntity> = rows.values.toList()
        override suspend fun updateLastUsed(id: String, lastUsedAt: Long): Int {
            val row = rows[id] ?: return 0
            rows[id] = row.copy(lastUsedAt = lastUsedAt)
            return 1
        }
        override suspend fun deleteById(id: String): Int = if (rows.remove(id) != null) 1 else 0
    }
}
