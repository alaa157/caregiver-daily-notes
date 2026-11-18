package com.caregiver.mobile.data.accounts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
        failOpenFor: String? = null,
        failCleanupOn: String? = null,
    ) {
        val activeIds = mutableListOf<String>()
        val openedIds = mutableListOf<String>()
        val closedIds = mutableListOf<String>()
        /** Exact call order across every collaborator, for the switch ordering test. */
        val events = mutableListOf<String>()
        var cancelWorkCalls = 0
        var dummyHashCalls = 0
        var rehashWrites = 0
        var failRehashWrite = false

        val cleanup = RecordingCleanup(events, failCleanupOn)

        init {
            dao.onUpdateLastUsed = { id, _ -> events += "lastUsed:$id" }
            dao.onDeleteById = { id -> events += "delete:$id" }
        }

        val manager = AccountManager(
            dao = dao,
            hasher = hasher,
            hashDispatcher = dispatcher,
            clock = clock,
            sessionStore = sessionStore,
            accountsChanged = accountsChanged,
            cleanup = cleanup,
            openAccountDatabase = { id ->
                events += "open:$id"
                if (id == failOpenFor) throw IllegalStateException("cannot open account database")
                openedIds += id
            },
            closeAccountDatabase = { id ->
                events += "close:$id"
                closedIds += id
            },
            cancelAccountWork = {
                events += "cancel"
                cancelWorkCalls++
            },
            onActiveAccountChanged = { id ->
                events += "set:$id"
                if (id != null) activeIds += id
            },
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
        var onUpdateLastUsed: ((String, Long) -> Unit)? = null
        var onDeleteById: ((String) -> Unit)? = null

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
            onUpdateLastUsed?.invoke(id, lastUsedAt)
            return 1
        }

        override suspend fun deleteById(id: String): Int {
            val removed = rows.remove(id) != null
            if (removed) onDeleteById?.invoke(id)
            return if (removed) 1 else 0
        }
    }

    /** Records cleanup order and can fail one hook without resurrecting the row. */
    private class RecordingCleanup(
        private val events: MutableList<String>,
        private val failOn: String? = null,
    ) : AccountCleanup {
        val calls = mutableListOf<String>()

        private fun record(step: String) {
            calls += step
            events += "cleanup:$step"
            if (step == failOn) throw IllegalStateException("$step cleanup failed")
        }

        override suspend fun deleteDatabase(accountId: String) = record("db")
        override suspend fun deleteAiKeys(accountId: String) = record("keys")
        override suspend fun deleteExports(accountId: String) = record("exports")
    }

    /** Fake database provider that records open/close call order (#606). */
    private class RecordingDatabaseProvider(private val events: MutableList<String>) {
        suspend fun open(accountId: String) {
            events += "open:$accountId"
        }

        suspend fun close(accountId: String) {
            events += "close:$accountId"
        }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchAccountRunsTheExactOperationOrder() = runTest {
        val file = File.createTempFile("switch-order-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val old = seedAccount(h, 7, id = "acc-old", email = "old@example.com")
            val target = seedAccount(h, 7, id = "acc-new", email = "new@example.com")
            store.setActiveAccountId(old.id)
            h.events.clear()

            val result = h.manager.switchAccount(target.id)

            assertTrue(result.isSuccess)
            assertEquals(target.id, store.activeAccountId.first())
            assertEquals(
                listOf("cancel", "close:acc-old", "set:acc-new", "open:acc-new", "lastUsed:acc-new"),
                h.events,
            )
            assertEquals(listOf("acc-old"), h.closedIds)
            assertEquals(listOf("acc-new"), h.openedIds)
            assertEquals(FIXED_NOW, h.dao.rows[target.id]!!.lastUsedAt)
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchingToTheCurrentAccountIsANoOp() = runTest {
        val file = File.createTempFile("switch-noop-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val current = seedAccount(h, 7)
            store.setActiveAccountId(current.id)
            h.events.clear()

            assertTrue(h.manager.switchAccount(current.id).isSuccess)

            assertTrue(h.events.isEmpty())
            assertEquals(current.id, store.activeAccountId.first())
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchingToAMissingIdLeavesTheCurrentAccountAlone() = runTest {
        val file = File.createTempFile("switch-missing-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val current = seedAccount(h, 7)
            store.setActiveAccountId(current.id)
            h.events.clear()

            assertAccountError(AccountError.AccountNotFound, h.manager.switchAccount("missing").exceptionOrNull())

            assertTrue(h.events.isEmpty())
            assertEquals(current.id, store.activeAccountId.first())
            assertEquals(FIXED_NOW, h.dao.rows[current.id]!!.lastUsedAt)
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun openFailureRollsBackToThePreviousAccount() = runTest {
        val file = File.createTempFile("switch-rollback-test", ".preferences_pb")
        try {
            var now = FIXED_NOW
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store, clock = { now }, failOpenFor = "acc-new")
            val old = seedAccount(h, 7, id = "acc-old", email = "old@example.com")
            val target = seedAccount(h, 7, id = "acc-new", email = "new@example.com")
            store.setActiveAccountId(old.id)
            h.events.clear()
            now = FIXED_NOW + 1_000

            val result = h.manager.switchAccount(target.id)

            assertTrue(result.isFailure)
            assertFalse(result.exceptionOrNull() is AccountException) // original error, not a typed account failure
            assertEquals(old.id, store.activeAccountId.first()) // stored id reverted
            assertEquals(
                listOf("cancel", "close:acc-old", "set:acc-new", "open:acc-new", "set:acc-old", "open:acc-old"),
                h.events,
            )
            assertFalse(h.events.any { it.startsWith("lastUsed:") }) // no DB write on the failed path
            assertEquals(FIXED_NOW, h.dao.rows[target.id]!!.lastUsedAt)
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun removingTheActiveAccountIsRefusedAndDeletesNothing() = runTest {
        val file = File.createTempFile("remove-active-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val active = seedAccount(h, 7, id = "acc-active")
            store.setActiveAccountId(active.id)

            assertAccountError(AccountError.CannotRemoveActive, h.manager.removeAccount(active.id).exceptionOrNull())

            assertTrue(h.events.isEmpty())
            assertTrue(h.cleanup.calls.isEmpty())
            assertNotNull(h.dao.rows[active.id])
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun removingAMissingAccountReturnsAccountNotFound() = runTest {
        val file = File.createTempFile("remove-missing-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val active = seedAccount(h, 7, id = "acc-active")
            store.setActiveAccountId(active.id)

            assertAccountError(AccountError.AccountNotFound, h.manager.removeAccount("missing").exceptionOrNull())

            assertTrue(h.events.isEmpty())
            assertTrue(h.cleanup.calls.isEmpty())
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun removalDeletesTheRowBeforeEveryCleanupHook() = runTest {
        val file = File.createTempFile("remove-order-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val active = seedAccount(h, 7, id = "acc-active")
            val target = seedAccount(h, 7, id = "acc-target", email = "target@example.com")
            store.setActiveAccountId(active.id)
            h.events.clear()

            assertTrue(h.manager.removeAccount(target.id).isSuccess)

            assertEquals(
                listOf("delete:acc-target", "cleanup:db", "cleanup:keys", "cleanup:exports"),
                h.events,
            )
            assertEquals(listOf("db", "keys", "exports"), h.cleanup.calls)
            assertNull(h.dao.rows[target.id])
            assertNotNull(h.dao.rows[active.id])
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aFailingCleanupHookDoesNotResurrectTheAccount() = runTest {
        val file = File.createTempFile("remove-hookfail-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store, failCleanupOn = "keys")
            val active = seedAccount(h, 7, id = "acc-active")
            val target = seedAccount(h, 7, id = "acc-target", email = "target@example.com")
            store.setActiveAccountId(active.id)

            val result = h.manager.removeAccount(target.id)

            assertTrue(result.isFailure)
            assertFalse(result.exceptionOrNull() is AccountException) // original hook error
            assertNull(h.dao.rows[target.id]) // still deleted — never resurrected
            assertNotNull(h.dao.rows[active.id])
            assertEquals(listOf("db", "keys", "exports"), h.cleanup.calls) // all hooks attempted once
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun removingOneAccountLeavesOtherAccountsUntouched() = runTest {
        val file = File.createTempFile("remove-isolation-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val active = seedAccount(h, 7, id = "acc-active")
            val target = seedAccount(h, 7, id = "acc-target", email = "target@example.com")
            val other = seedAccount(h, 7, id = "acc-other", email = "other@example.com")
            store.setActiveAccountId(active.id)

            assertTrue(h.manager.removeAccount(target.id).isSuccess)

            assertNull(h.dao.rows[target.id])
            assertNotNull(h.dao.rows[active.id])
            assertNotNull(h.dao.rows[other.id])
            assertEquals(listOf("db", "keys", "exports"), h.cleanup.calls)
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aFakeDatabaseProviderRecordsTheSwapOrder() = runTest {
        val file = File.createTempFile("provider-order-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val providerEvents = mutableListOf<String>()
            val provider = RecordingDatabaseProvider(providerEvents)
            val dao = FakeAccountDao()
            val manager = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = store,
                closeAccountDatabase = { provider.close(it) },
                openAccountDatabase = { provider.open(it) },
                cancelAccountWork = { providerEvents += "cancel" },
            )
            val old = AccountEntity("old", "old@example.com", "Old", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            val next = AccountEntity("next", "next@example.com", "Next", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            dao.rows[old.id] = old
            dao.rows[next.id] = next
            store.setActiveAccountId(old.id)
            providerEvents.clear()

            assertTrue(manager.switchAccount(next.id).isSuccess)

            assertEquals(
                listOf("cancel", "close:old", "open:next"),
                providerEvents,
            )
            assertEquals(next.id, store.activeAccountId.first())
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchingWhileAnAiCallIsRunningCancelsItAndDropsTheResult() = runTest {
        val file = File.createTempFile("ai-switch-test", ".preferences_pb")
        val aiScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val gate = CompletableDeferred<Unit>()
        var resultLanded = false
        val aiCall = aiScope.launch { gate.await(); resultLanded = true }
        try {
            val store = newSessionStore(file)
            val dao = FakeAccountDao()
            val manager = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = store,
                cancelAccountWork = { aiScope.cancel() },
            )
            dao.rows["old"] = AccountEntity("old", "old@example.com", "Old", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            dao.rows["next"] = AccountEntity("next", "next@example.com", "Next", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            store.setActiveAccountId("old")

            assertTrue(manager.switchAccount("next").isSuccess)
            assertTrue(aiCall.isCancelled)

            gate.complete(Unit) // a late AI result must not land
            assertFalse(resultLanded)
        } finally {
            aiScope.cancel()
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchingWhileADatabaseQueryIsInFlightCannotCrossWrite() = runTest {
        val file = File.createTempFile("query-switch-test", ".preferences_pb")
        val queryScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val gate = CompletableDeferred<Unit>()
        var queryResult: List<AccountEntity>? = null
        try {
            val store = newSessionStore(file)
            val dao = FakeAccountDao()
            val query = queryScope.launch { gate.await(); queryResult = dao.listAll() }
            var now = FIXED_NOW
            val manager = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = store,
                clock = { now },
                cancelAccountWork = { queryScope.cancel() },
            )
            dao.rows["old"] = AccountEntity("old", "old@example.com", "Old", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            dao.rows["next"] = AccountEntity("next", "next@example.com", "Next", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            store.setActiveAccountId("old")
            now = FIXED_NOW + 1_000

            assertTrue(manager.switchAccount("next").isSuccess)
            assertTrue(query.isCancelled)
            gate.complete(Unit)
            assertNull(queryResult) // cancelled query never completes against the new database
            assertEquals(FIXED_NOW, dao.rows["old"]!!.lastUsedAt)
            assertEquals(FIXED_NOW + 1_000, dao.rows["next"]!!.lastUsedAt)
        } finally {
            queryScope.cancel()
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun removingANonActiveAccountLeavesTheOtherAccountAlone() = runTest {
        val file = File.createTempFile("remove-nonactive-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val active = seedAccount(h, 7, id = "acc-active")
            val target = seedAccount(h, 7, id = "acc-target", email = "target@example.com")
            val other = seedAccount(h, 7, id = "acc-other", email = "other@example.com")
            store.setActiveAccountId(active.id)

            assertTrue(h.manager.removeAccount(target.id).isSuccess)

            assertEquals(setOf("acc-active", "acc-other"), h.dao.listAll().map { it.id }.toSet())
            assertNotNull(h.dao.rows[other.id])
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchingToARemovedAccountReturnsAccountNotFound() = runTest {
        val file = File.createTempFile("switch-removed-test", ".preferences_pb")
        try {
            val store = newSessionStore(file)
            val h = Harness(sessionStore = store)
            val active = seedAccount(h, 7, id = "acc-active")
            val target = seedAccount(h, 7, id = "acc-target", email = "target@example.com")
            store.setActiveAccountId(active.id)
            assertTrue(h.manager.removeAccount(target.id).isSuccess)

            assertAccountError(AccountError.AccountNotFound, h.manager.switchAccount(target.id).exceptionOrNull())
            assertEquals(active.id, store.activeAccountId.first())
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun activeAccountRestoresAfterProcessDeath() = runTest {
        val file = File.createTempFile("death-active-test", ".preferences_pb")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file }
            val dao = FakeAccountDao()
            val first = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = SessionStore(dataStore),
            )
            assertTrue(first.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
            val id = dao.listAll().single().id

            // Process death: recreate the store and manager over the same
            // persisted DataStore and the same accounts data.
            val recreated = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = SessionStore(dataStore),
            )
            recreated.restoreActiveAccount()

            assertEquals(id, recreated.activeAccountId.first())
            assertEquals(id, recreated.activeAccount.first()!!.id)
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aRemovedAccountDoesNotReappearAfterRestart() = runTest {
        val file = File.createTempFile("death-removed-test", ".preferences_pb")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file }
            val dao = FakeAccountDao()
            val first = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = SessionStore(dataStore),
            )
            assertTrue(first.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
            val activeId = dao.findByEmail("amina@example.com")!!.id
            val removed = seedAccount(Harness(dao = dao), 10, id = "gone", email = "gone@example.com")
            assertTrue(first.removeAccount(removed.id).isSuccess)

            val recreated = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = SessionStore(dataStore),
            )
            recreated.restoreActiveAccount()

            assertEquals(setOf(activeId), recreated.listAccounts().first().map { it.id }.toSet())
            assertFalse(recreated.listAccounts().first().any { it.id == removed.id })
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aHalfCompletedSwitchDoesNotPointAtAClosedDatabaseAfterRestart() = runTest {
        val file = File.createTempFile("death-switch-test", ".preferences_pb")
        val events = mutableListOf<String>()
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file }
            val dao = FakeAccountDao()
            dao.rows["old"] = AccountEntity("old", "old@example.com", "Old", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            dao.rows["next"] = AccountEntity("next", "next@example.com", "Next", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            val store = SessionStore(dataStore)
            store.setActiveAccountId("old")

            val failing = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = store,
                closeAccountDatabase = { events += "close:$it" },
                openAccountDatabase = { id ->
                    events += "open:$id"
                    if (id == "next") throw IllegalStateException("cannot open new database")
                },
            )
            assertTrue(failing.switchAccount("next").isFailure)

            // Restart: the rollback must have left the stored id on the old,
            // still-open account, so a fresh open targets the right database.
            val recreated = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = SessionStore(dataStore),
            )
            recreated.restoreActiveAccount()

            assertEquals("old", recreated.activeAccountId.first())
            assertEquals("old", recreated.activeAccount.first()!!.id)
            assertEquals("open:old", events.last()) // previous account reopened on rollback
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restartWithAStaleActiveIdDoesNotCrash() = runTest {
        val file = File.createTempFile("death-stale-test", ".preferences_pb")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file }
            val store = SessionStore(dataStore)
            store.setActiveAccountId("ghost-account") // no such row anywhere

            val recreated = AccountManager(
                dao = FakeAccountDao(),
                hasher = PasswordHasher(iterations = 10),
                sessionStore = SessionStore(dataStore),
            )
            recreated.restoreActiveAccount() // must not throw

            assertNull(recreated.activeAccountId.first())
            assertNull(recreated.activeAccount.first())
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun swapRunsCancelJoinClosePersistOpenInOrder() = runTest {
        val file = File.createTempFile("swap-order-test", ".preferences_pb")
        try {
            val events = mutableListOf<String>()
            val dao = FakeAccountDao()
            dao.rows["old"] = AccountEntity("old", "old@example.com", "Old", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            dao.rows["next"] = AccountEntity("next", "next@example.com", "Next", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            val store = newSessionStore(file)
            store.setActiveAccountId("old")
            val manager = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = store,
                cancelAccountWork = { events += "cancel" },
                awaitAccountWork = { events += "join" },
                closeAccountDatabase = { events += "close:$it" },
                openAccountDatabase = { events += "open:$it" },
                onActiveAccountChanged = { id -> events += "set:$id" },
            )

            assertTrue(manager.switchAccount("next").isSuccess)

            assertEquals(
                listOf("cancel", "join", "close:old", "set:next", "open:next"),
                events.take(5),
            )
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun writesDuringTheSwapWindowAreRejected() = runTest {
        val file = File.createTempFile("swap-gate-test", ".preferences_pb")
        try {
            val dao = FakeAccountDao()
            dao.rows["old"] = AccountEntity("old", "old@example.com", "Old", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            dao.rows["next"] = AccountEntity("next", "next@example.com", "Next", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            val store = newSessionStore(file)
            store.setActiveAccountId("old")
            lateinit var manager: AccountManager
            var sawSwapping = false
            var writeRejected = false
            manager = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = store,
                openAccountDatabase = { _ ->
                    sawSwapping = manager.isSwapping
                    try {
                        manager.assertWritable()
                    } catch (_: IllegalStateException) {
                        writeRejected = true
                    }
                },
            )

            assertTrue(manager.switchAccount("next").isSuccess)
            assertTrue(sawSwapping)
            assertTrue(writeRejected)
            assertFalse(manager.isSwapping)
            manager.assertWritable() // writes are allowed again once the swap completed
        } finally {
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aCancellationTimeoutIsReportedAndTheSwapStillCompletes() = runTest {
        val file = File.createTempFile("swap-timeout-test", ".preferences_pb")
        val never = CompletableDeferred<Unit>()
        try {
            var timedOut = false
            val dao = FakeAccountDao()
            dao.rows["old"] = AccountEntity("old", "old@example.com", "Old", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            dao.rows["next"] = AccountEntity("next", "next@example.com", "Next", "h", "s", 10, FIXED_NOW, FIXED_NOW)
            val store = newSessionStore(file)
            store.setActiveAccountId("old")
            val manager = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = store,
                awaitAccountWork = { never.await() },
                onCancellationTimeout = { timedOut = true },
                swapTimeoutMs = 10L,
            )

            assertTrue(manager.switchAccount("next").isSuccess)
            assertTrue(timedOut)
            assertEquals("next", store.activeAccountId.first())
        } finally {
            never.cancel()
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun activeAccountFollowsTheNewActiveIdWithoutStaleValues() = runTest {
        val file = File.createTempFile("swap-emission-test", ".preferences_pb")
        try {
            val dao = FakeAccountDao()
            val store = newSessionStore(file)
            val manager = AccountManager(
                dao = dao,
                hasher = PasswordHasher(iterations = 10),
                sessionStore = store,
            )
            val collected = async { manager.activeAccount.take(3).toList() }
            assertTrue(manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
            val idA = dao.listAll().single().id
            assertTrue(manager.signUp("Nour", "nour@example.com", "goodpass1").isSuccess)
            val idB = dao.listAll().single { it.email == "nour@example.com" }.id

            val emissions = collected.await()
            assertEquals(listOf(null, idA, idB), emissions.map { it?.id })
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
    private fun seedAccount(
        h: Harness,
        iterations: Int,
        id: String = "acc-seed",
        email: String = "amina@example.com",
    ): AccountEntity {
        val hashed = PasswordHasher(iterations = iterations).hash("goodpass1")
        val entity = AccountEntity(
            id = id,
            email = email,
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

/**
 * #606: the manager against a real in-memory accounts database, proving the
 * fake-DAO tests agree with Room's unique index and one-shot reads.
 *
 * Distinct database class name so it cannot collide with AccountDaoTest's
 * test-only database in the same package.
 */
@Database(entities = [AccountEntity::class], version = 1, exportSchema = false)
abstract class ManagerRoomAccountsDb : RoomDatabase() {
    abstract fun accountDao(): AccountDao
}

@RunWith(RobolectricTestRunner::class)
class AccountManagerRoomTest {
    private lateinit var db: ManagerRoomAccountsDb
    private lateinit var dao: AccountDao

    @org.junit.Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ManagerRoomAccountsDb::class.java,
        ).allowMainThreadQueries().build()
        dao = db.accountDao()
    }

    @org.junit.After
    fun tearDown() {
        db.close()
    }

    @Test
    fun roomSignUpPersistsAndSignInVerifies() = runTest {
        val manager = AccountManager(dao = dao, hasher = PasswordHasher(iterations = 100))

        assertTrue(manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)

        val row = dao.findByEmail("amina@example.com")!!
        assertTrue(PasswordHasher(iterations = 100).verify("goodpass1", row.passwordHash, row.salt, row.kdfIterations))
        assertTrue(manager.signIn("amina@example.com", "goodpass1").isSuccess)
        assertFalse(manager.signIn("amina@example.com", "wrongpass1").isSuccess)
    }

    @Test
    fun roomUniqueIndexRejectsADuplicateEmail() = runTest {
        val manager = AccountManager(dao = dao, hasher = PasswordHasher(iterations = 100))
        assertTrue(manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)

        val second = manager.signUp("Other", "amina@example.com", "goodpass1")
        assertTrue(second.exceptionOrNull() is AccountException)
        assertEquals(AccountError.EmailTaken, (second.exceptionOrNull() as AccountException).error)
        assertEquals(1, dao.listAll().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun removingTheActiveAccountKeepsTheDatabaseFileIntact() = runTest {
        val dbPath = File.createTempFile("manager-remove-active", ".db")
        dbPath.delete() // let Room create the file fresh
        val sessionFile = File.createTempFile("manager-session", ".preferences_pb")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fileDb = Room.databaseBuilder(context, ManagerRoomAccountsDb::class.java, dbPath.absolutePath)
            .allowMainThreadQueries()
            .build()
        try {
            val store = SessionStore(
                PreferenceDataStoreFactory.create(
                    scope = TestScope(UnconfinedTestDispatcher()),
                ) { sessionFile },
            )
            val manager = AccountManager(
                dao = fileDb.accountDao(),
                hasher = PasswordHasher(iterations = 100),
                sessionStore = store,
            )
            assertTrue(manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
            val id = fileDb.accountDao().listAll().single().id
            store.setActiveAccountId(id)

            val result = manager.removeAccount(id)

            assertTrue(result.exceptionOrNull() is AccountException)
            assertEquals(AccountError.CannotRemoveActive, (result.exceptionOrNull() as AccountException).error)
            assertTrue(dbPath.exists())
            assertEquals(1, fileDb.accountDao().listAll().size)
        } finally {
            fileDb.close()
            dbPath.delete()
            sessionFile.delete()
        }
    }

    @Test
    fun roomBackedFlowsSeeInsertsAndDeletes() = runTest {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val manager = AccountManager(
            dao = dao,
            hasher = PasswordHasher(iterations = 100),
            accountsChanged = changes,
        )
        val collected = async { manager.listAccounts().take(3).toList() }

        assertTrue(manager.signUp("Amina", "amina@example.com", "goodpass1").isSuccess)
        val id = dao.listAll().single().id
        dao.deleteById(id)
        changes.emit(Unit)

        val emissions = collected.await()
        assertEquals(3, emissions.size)
        assertEquals(0, emissions[0].size)
        assertEquals(1, emissions[1].size)
        assertEquals(0, emissions[2].size)
    }
}
