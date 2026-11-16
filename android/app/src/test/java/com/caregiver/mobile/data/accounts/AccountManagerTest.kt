package com.caregiver.mobile.data.accounts

import androidx.sqlite.SQLiteConstraintException
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    ) {
        val activeIds = mutableListOf<String>()
        val openedIds = mutableListOf<String>()
        val manager = AccountManager(
            dao = dao,
            hasher = hasher,
            hashDispatcher = dispatcher,
            clock = clock,
            setActiveAccount = { activeIds += it },
            openAccountDatabase = { openedIds += it },
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

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            executor.execute {
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
    fun managerSourceHasNoLoggingOfSecrets() {
        val source = locateManagerSource().readText()
        assertFalse("no android.util.Log calls", source.contains("Log."))
        assertFalse("no println", source.contains("println("))
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
