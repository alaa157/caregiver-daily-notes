package com.caregiver.mobile.data.accounts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.SQLiteConstraintException
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIXED_NOW = 1_700_000_000_000L
private const val TEST_ITERATIONS = 100
private const val HASH_THREAD = "account-hasher-test"

/** #590: the account-creation path, with a fake DAO so it stays JVM-only. */
class AccountManagerTest {

    private class Harness(
        val dao: FakeAccountDao = FakeAccountDao(),
        val hasher: PasswordHasher = PasswordHasher(iterations = TEST_ITERATIONS),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        val clock: () -> Long = { FIXED_NOW },
        currentIterations: Int = TEST_ITERATIONS,
        sessionStore: SessionStore? = null,
        accountsChanged: Flow<Unit> = emptyFlow(),
    ) {
        val activeIds = mutableListOf<String>()
        val openedIds = mutableListOf<String>()
        val closedIds = mutableListOf<String>()
        var cancelWorkCalls = 0
        var dummyHashCalls = 0
        var rehashWrites = 0
        var failRehashWrite = false
        val manager = AccountManager(
            dao = dao,
            hasher = hasher,
            hashDispatcher = dispatcher,
            clock = clock,
            sessionStore = sessionStore,
            accountsChanged = accountsChanged,
            openAccountDatabase = { openedIds += it },
            closeAccountDatabase = { closedIds += it },
            cancelAccountWork = { cancelWorkCalls++ },
            onActiveAccountChanged = { id -> if (id != null) activeIds += id },
            currentIterations = currentIterations,
            performDummyHash = { _: String -> dummyHashCalls++ },
            updateCredentials = { row, fresh ->
                if (failRehashWrite) throw IllegalStateException("simulated write failure")
                rehashWrites++
                dao.rows[row.id] = row.copy(
                    passwordHash = fresh.hash,
                    salt = fresh.salt,
                    kdfIterations = fresh.iterations,
                )
            },
        )
    }

    private class FakeAccountDao : AccountDao {
        val rows = linkedMapOf<String, AccountEntity>()
        /** Simulate the window between findByEmail and insert where a rival signs up. */
        var constraintOnInsert = false

        override suspend fun insert(account: AccountEntity) {
            if (constraintOnInsert) {
                constraintOnInsert = false
                throw SQLiteConstraintException("simulated concurrent duplicate")
            }
            if (rows.values.any { it.email == account.email }) {
                throw SQLiteConstraintException("UNIQUE constraint failed: accounts.email")
            }
            rows[account.id] = account
        }

        override suspend fun findByEmail(email: String): AccountEntity? =
            rows.values.firstOrNull { it.email == email }

        override suspend fun findById(id: String): AccountEntity? = rows[id]

        override suspend fun listAll(): List<AccountEntity> =
            rows.values.sortedByDescending { it.lastUsedAt }

        override suspend fun updateLastUsed(id: String, lastUsedAt: Long): Int {
            val row = rows[id] ?: return 0
            rows[id] = row.copy(lastUsedAt = lastUsedAt)
            return 1
        }

        override suspend fun deleteById(id: String): Int = if (rows.remove(id) != null) 1 else 0
    }

    /** Runs the dispatched block on a named background thread so we can prove hashing left Main. */
    private class ThreadRecordingDispatcher : CoroutineDispatcher() {
        private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, HASH_THREAD) }

        @Volatile
        var executedOn: String? = null
            private set

        @Volatile
        var dispatches = 0
            private set

        fun reset() {
            executedOn = null
            dispatches = 0
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            executor.execute {
                dispatches++
                executedOn = Thread.currentThread().name
                block.run()
            }
        }

        fun shutdown() {
            executor.shutdownNow()
        }
    }

    @Test
    fun signUpCreatesOneRowAndReturnsACredentialFreeAccount() = runTest {
        val h = Harness()
        val result = h.manager.signUp("  Amina Ali  ", "  Amina@Example.COM ", "goodpass1")

        assertTrue(result.isSuccess)
        val account = result.getOrThrow()
        assertEquals("Amina Ali", account.displayName) // trimmed
        assertEquals("amina@example.com", account.email) // normalized
        assertEquals(FIXED_NOW, account.createdAt)
        assertEquals(FIXED_NOW, account.lastUsedAt)

        assertEquals(1, h.dao.rows.size)
        val row = h.dao.rows.values.single()
        assertEquals(account.id, row.id)
        assertEquals("amina@example.com", row.email)
        assertEquals("Amina Ali", row.displayName)
        assertEquals(TEST_ITERATIONS, row.kdfIterations)
        assertTrue(row.passwordHash.isNotBlank())
        assertTrue(row.salt.isNotBlank())
        // The returned domain object carries no credential material.
        assertFalse(account.toString().contains(row.passwordHash))
        assertFalse(account.toString().contains(row.salt))

        assertEquals(listOf(account.id), h.activeIds)
        assertEquals(listOf(account.id), h.openedIds)
    }

    @Test
    fun duplicateEmailReturnsEmailTakenRegardlessOfCaseOrSurroundingSpaces() = runTest {
        val h = Harness()
        assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)

        for (variant in listOf("amina@example.com", "AMINA@EXAMPLE.COM", "  amina@example.com  ")) {
            assertAccountError(AccountError.EmailTaken, h.manager.signUp("Other", variant, "goodpass1").exceptionOrNull())
        }
        assertEquals(1, h.dao.rows.size)
    }

    @Test
    fun weakPasswordReturnsTheSpecificProblem() = runTest {
        val h = Harness()
        val cases = listOf(
            "short" to PasswordProblem.TooShort,
            "a".repeat(129) + "1" to PasswordProblem.TooLong,
            "abcdefgh" to PasswordProblem.MissingDigit,
            "12345678" to PasswordProblem.MissingLetter,
            "        " to PasswordProblem.OnlyWhitespace,
        )
        for ((password, problem) in cases) {
            val result = h.manager.signUp("Amina", "a@b.com", password)
            assertAccountError(AccountError.WeakPassword(problem), result.exceptionOrNull())
        }
        assertTrue(h.dao.rows.isEmpty())
    }

    @Test
    fun invalidEmailReturnsInvalidEmail() = runTest {
        val h = Harness()
        for (bad in listOf("not-an-email", "@example.com", "a@", "a b@example.com")) {
            assertAccountError(AccountError.InvalidEmail, h.manager.signUp("Amina", bad, "goodpass1").exceptionOrNull())
        }
        assertTrue(h.dao.rows.isEmpty())
    }

    @Test
    fun blankOrTooLongDisplayNameIsRejected() = runTest {
        val h = Harness()
        for (bad in listOf("", "   ", "\t\n", "a".repeat(AccountManager.MAX_DISPLAY_NAME_LENGTH + 1))) {
            val failure = h.manager.signUp(bad, "a@b.com", "goodpass1").exceptionOrNull()
            assertTrue("expected InvalidDisplayNameException for [$bad] but got $failure", failure is InvalidDisplayNameException)
        }
        assertTrue(h.dao.rows.isEmpty())
        assertTrue(h.activeIds.isEmpty())
        assertTrue(h.openedIds.isEmpty())
    }

    @Test
    fun concurrentDuplicateInsertMapsToEmailTaken() = runTest {
        val h = Harness()
        // findByEmail sees nothing, then the unique index rejects the insert.
        h.dao.constraintOnInsert = true

        assertAccountError(AccountError.EmailTaken, h.manager.signUp("Amina", "a@b.com", "goodpass1").exceptionOrNull())
        assertTrue(h.dao.rows.isEmpty())
        assertTrue(h.activeIds.isEmpty())
        assertTrue(h.openedIds.isEmpty()) // no active/open on a failed sign-up
    }

    @Test
    fun hashingRunsOnTheInjectedDispatcher() = runTest {
        val recorder = ThreadRecordingDispatcher()
        try {
            val h = Harness(dispatcher = recorder)
            assertTrue(h.manager.signUp("Amina", "a@b.com", "goodpass1").isSuccess)
            assertEquals(HASH_THREAD, recorder.executedOn)
            assertNotEquals("main", recorder.executedOn)
        } finally {
            recorder.shutdown()
        }
    }

    @Test
    fun correctPasswordSignsInAndReturnsTheAccount() = runTest {
        val h = Harness()
        assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)

        val result = h.manager.signIn("  AMINA@EXAMPLE.COM ", "goodpass1")

        assertTrue(result.isSuccess)
        val account = result.getOrThrow()
        assertEquals("amina@example.com", account.email)
        assertEquals("Amina", account.displayName)
        assertEquals(listOf(account.id), h.activeIds)
        assertEquals(listOf(account.id), h.openedIds)
        assertEquals(0, h.dummyHashCalls) // a real address needs no dummy work
    }

    @Test
    fun wrongPasswordFailsWithoutTouchingTheRowOrSession() = runTest {
        val h = Harness()
        assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
        val before = h.dao.rows.values.single()

        assertAccountError(AccountError.InvalidCredentials, h.manager.signIn("amina@example.com", "wrongpass1").exceptionOrNull())

        assertEquals(before, h.dao.rows.values.single())
        assertTrue(h.activeIds.isEmpty())
        assertTrue(h.openedIds.isEmpty())
        assertEquals(0, h.rehashWrites) // never rehash on a failed verify
        assertEquals(0, h.dummyHashCalls)
    }

    @Test
    fun unknownEmailFailsAndPerformsOneDummyHash() = runTest {
        val h = Harness()

        assertAccountError(AccountError.InvalidCredentials, h.manager.signIn("nobody@example.com", "goodpass1").exceptionOrNull())

        assertEquals(1, h.dummyHashCalls)
        assertTrue(h.activeIds.isEmpty())
        assertTrue(h.openedIds.isEmpty())
    }

    @Test
    fun malformedEmailMapsToInvalidCredentialsAndPerformsOneDummyHashEach() = runTest {
        val h = Harness()

        for (bad in listOf("not-an-email", "@example.com", "a@", "a b@example.com")) {
            assertAccountError(AccountError.InvalidCredentials, h.manager.signIn(bad, "goodpass1").exceptionOrNull())
        }
        assertEquals(4, h.dummyHashCalls)
        assertTrue(h.dao.rows.isEmpty())
    }

    @Test
    fun unknownEmailAndWrongPasswordReturnAnIdenticalErrorValue() = runTest {
        val h = Harness()
        assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)

        val wrong = h.manager.signIn("amina@example.com", "wrongpass1").exceptionOrNull()
        val unknown = h.manager.signIn("nobody@example.com", "wrongpass1").exceptionOrNull()

        assertTrue(wrong is AccountException)
        assertEquals(AccountError.InvalidCredentials, (wrong as AccountException).error)
        assertEquals(wrong, unknown)
    }

    @Test
    fun lastUsedAtChangesOnSuccessOnly() = runTest {
        var now = FIXED_NOW
        val h = Harness(clock = { now })
        assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)

        now = FIXED_NOW + 1_000
        val success = h.manager.signIn("amina@example.com", "goodpass1")
        assertTrue(success.isSuccess)
        assertEquals(FIXED_NOW + 1_000, success.getOrThrow().lastUsedAt)
        assertEquals(FIXED_NOW + 1_000, h.dao.rows.values.single().lastUsedAt)

        val beforeFailure = h.dao.rows.values.single()
        now = FIXED_NOW + 2_000
        assertAccountError(AccountError.InvalidCredentials, h.manager.signIn("amina@example.com", "wrongpass1").exceptionOrNull())
        assertEquals(beforeFailure, h.dao.rows.values.single())
    }

    @Test
    fun verificationRunsOnTheInjectedDispatcher() = runTest {
        val recorder = ThreadRecordingDispatcher()
        try {
            val h = Harness(dispatcher = recorder)
            assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
            recorder.reset()

            assertTrue(h.manager.signIn("amina@example.com", "goodpass1").isSuccess)
            assertTrue(recorder.dispatches > 0)
            assertEquals(HASH_THREAD, recorder.executedOn)
            assertNotEquals("main", recorder.executedOn)
        } finally {
            recorder.shutdown()
        }
    }

    @Test
    fun unknownEmailDummyHashDoesRealWork() = runTest {
        // Uses the production default performDummyHash: one real PBKDF2 at a
        // high cost, so the call cannot return in microseconds.
        val manager = AccountManager(
            dao = FakeAccountDao(),
            hasher = PasswordHasher(iterations = 1),
            currentIterations = 200_000,
        )
        val started = System.nanoTime()
        val result = manager.signIn("nobody@example.com", "goodpass1")
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertAccountError(AccountError.InvalidCredentials, result.exceptionOrNull())
        assertTrue("dummy PBKDF2 should be measurable, took ${elapsedMs}ms", elapsedMs >= 10)
    }

    @Test
    fun rehashUpgradesAnOldRowAndThePasswordStillVerifies() = runTest {
        val h = Harness(currentIterations = 200)
        val seeded = seedAccount(h, iterations = TEST_ITERATIONS)

        assertTrue(h.manager.signIn("amina@example.com", "goodpass1").isSuccess)

        val updated = h.dao.rows[seeded.id]!!
        assertEquals(200, updated.kdfIterations)
        assertNotEquals(seeded.salt, updated.salt)
        assertNotEquals(seeded.passwordHash, updated.passwordHash)
        assertEquals(1, h.rehashWrites)
        assertTrue(PasswordHasher(iterations = 200).verify("goodpass1", updated.passwordHash, updated.salt, 200))
    }

    @Test
    fun noRehashWhenTheRowIsAlreadyAtTheCurrentCost() = runTest {
        val h = Harness()
        seedAccount(h, iterations = TEST_ITERATIONS)

        assertTrue(h.manager.signIn("amina@example.com", "goodpass1").isSuccess)
        assertEquals(0, h.rehashWrites)
    }

    @Test
    fun rehashWriteFailureDoesNotFailTheLogin() = runTest {
        val h = Harness(currentIterations = 200)
        val seeded = seedAccount(h, iterations = TEST_ITERATIONS)
        h.failRehashWrite = true

        assertTrue(h.manager.signIn("amina@example.com", "goodpass1").isSuccess)
        assertEquals(1, h.rehashWrites) // attempted
        assertEquals(seeded.passwordHash, h.dao.rows[seeded.id]!!.passwordHash) // old row kept
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun signOutClearsTheStoredIdClosesTheDatabaseAndEmitsNull() = runTest {
        val file = File.createTempFile("session-signout-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
            val id = h.dao.rows.values.single().id
            assertEquals(id, h.manager.activeAccountId.first())

            h.manager.signOut()

            assertNull(store.activeAccountId.first()) // id removed from DataStore
            assertNull(h.manager.activeAccountId.first()) // flow emits null
            assertEquals(listOf(id), h.openedIds)
            assertEquals(listOf(id), h.closedIds)
            assertEquals(1, h.cancelWorkCalls)
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun storedIdForAMissingAccountIsClearedOnRestore() = runTest {
        val file = File.createTempFile("session-stale-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            store.setActiveAccountId("ghost-account")
            val h = Harness(sessionStore = store) // no such row in the accounts table

            h.manager.restoreActiveAccount()

            assertNull(store.activeAccountId.first())
            assertNull(h.manager.activeAccountId.first())
            assertTrue(h.closedIds.isEmpty())
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restoreKeepsAnExistingAccountActive() = runTest {
        val file = File.createTempFile("session-restore-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val first = Harness(sessionStore = store)
            assertTrue(first.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
            val id = first.dao.rows.values.single().id

            // A fresh manager over the same store and rows (process restart).
            val restarted = Harness(dao = first.dao, sessionStore = store)
            restarted.manager.restoreActiveAccount()

            assertEquals(id, restarted.manager.activeAccountId.first())
            assertEquals(0, restarted.rehashWrites)
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun activeAccountEmitsTheAccountForTheStoredId() = runTest {
        val file = File.createTempFile("active-acc-test", ".preferences_pb")
        try {
            val h = Harness(sessionStore = newSessionStore(file))
            assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)

            val emitted = h.manager.activeAccount.first()
            assertEquals(h.dao.rows.values.single().id, emitted?.id)
            assertEquals("amina@example.com", emitted?.email)
            assertEquals("Amina", emitted?.displayName)
        } finally {
            file.delete()
        }
    }

    @Test
    fun activeAccountEmitsNullWhenNoIdIsStored() = runTest {
        assertNull(Harness().manager.activeAccount.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun activeAccountEmitsNullWhenTheStoredRowIsGone() = runTest {
        val file = File.createTempFile("active-gone-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            store.setActiveAccountId("ghost-account")
            val h = Harness(sessionStore = store)

            assertNull(h.manager.activeAccount.first())
        } finally {
            file.delete()
        }
    }

    @Test
    fun listAccountsIsOrderedByLastUsedAtDescending() = runTest {
        val h = Harness()
        fun row(id: String, name: String, used: Long) = AccountEntity(
            id, "$name@example.com", name, "hash-$id", "salt-$id", 1, used, used,
        )
        h.dao.rows["a"] = row("a", "Oldest", 100)
        h.dao.rows["b"] = row("b", "Newest", 300)
        h.dao.rows["c"] = row("c", "Middle", 200)

        assertEquals(
            listOf("Newest", "Middle", "Oldest"),
            h.manager.listAccounts().first().map { it.displayName },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun listAccountsReEmitsAfterInsertUpdateAndDelete() = runTest {
        val file = File.createTempFile("list-reemit-test", ".preferences_pb")
        val roomChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        try {
            var now = FIXED_NOW
            val h = Harness(clock = { now }, sessionStore = newSessionStore(file), accountsChanged = roomChanges)
            val collected = async { h.manager.listAccounts().take(4).toList() }

            assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess) // insert
            now = FIXED_NOW + 1_000
            assertTrue(h.manager.signIn("amina@example.com", "goodpass1").isSuccess) // lastUsedAt update
            val id = h.dao.rows.values.single().id
            h.dao.deleteById(id)
            roomChanges.emit(Unit) // simulates Room invalidating the table after an external delete

            val emissions = collected.await()
            assertEquals(4, emissions.size)
            assertEquals(0, emissions[0].size)
            assertEquals(1, emissions[1].size)
            assertEquals(FIXED_NOW, emissions[1][0].lastUsedAt)
            assertEquals(FIXED_NOW + 1_000, emissions[2][0].lastUsedAt)
            assertEquals(0, emissions[3].size)
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun activeAccountReEmitsAfterDelete() = runTest {
        val file = File.createTempFile("active-reemit-test", ".preferences_pb")
        val roomChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        try {
            val h = Harness(sessionStore = newSessionStore(file), accountsChanged = roomChanges)
            val collected = async { h.manager.activeAccount.take(3).toList() }

            assertTrue(h.manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
            val id = h.dao.rows.values.single().id
            h.dao.deleteById(id)
            roomChanges.emit(Unit)

            val emissions = collected.await()
            assertEquals(listOf(null, id, null), emissions.map { it?.id })
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun accountFlowsNeverExposeCredentialMaterial() = runTest {
        val file = File.createTempFile("flows-credentials-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val seeded = seedAccount(h, iterations = 7)
            store.setActiveAccountId(seeded.id)

            val listed = h.manager.listAccounts().first().single()
            val active = h.manager.activeAccount.first()!!
            // Account has only id/email/displayName/createdAt/lastUsedAt by
            // type; assert no credential value leaks through either flow.
            val printed = listed.toString() + active.toString()
            assertFalse(printed.contains(seeded.passwordHash))
            assertFalse(printed.contains(seeded.salt))
        } finally {
            file.delete()
        }
    }

    @Test
    fun managerSourceHasNoLoggingOfSecrets() {
        val source = locateManagerSource().readText()
        assertFalse("no android.util.Log calls", source.contains("Log."))
        assertFalse("no println", source.contains("println("))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun newSessionStore(file: File): SessionStore = SessionStore(
        PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
        ) { file },
    )

    /** Seeds a row directly at an arbitrary (usually old) iteration cost. */
    private fun seedAccount(h: Harness, iterations: Int): AccountEntity {
        val hashed = PasswordHasher(iterations = iterations).hash("goodpass1")
        val entity = AccountEntity(
            id = "acc-seed",
            email = "amina@example.com",
            displayName = "Amina",
            passwordHash = hashed.hash,
            salt = hashed.salt,
            kdfIterations = iterations,
            createdAt = FIXED_NOW,
            lastUsedAt = FIXED_NOW,
        )
        h.dao.rows[entity.id] = entity
        return entity
    }

    private fun assertAccountError(expected: AccountError, actual: Throwable?) {
        assertTrue("expected $expected but got $actual", actual is AccountException)
        assertEquals(expected, (actual as AccountException).error)
    }

    private fun locateManagerSource(): File {
        val relative = "src/main/java/com/caregiver/mobile/data/accounts/AccountManager.kt"
        var cursor: File? = File(".").absoluteFile
        while (cursor != null) {
            for (candidate in listOf(File(cursor, relative), File(cursor, "android/app/$relative"))) {
                if (candidate.isFile) return candidate
            }
            cursor = cursor.parentFile
        }
        throw AssertionError("could not locate $relative")
    }
}
