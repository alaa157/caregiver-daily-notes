package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * #583: the accounts database is its own `accounts.db` file — never the
 * per-account `caregiver-db` — and round-trips a row through the DAO.
 */
@RunWith(RobolectricTestRunner::class)
class AccountsDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: AccountsDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AccountsDatabase.create(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createUsesItsOwnDatabaseFile() {
        val accountsFile = context.getDatabasePath("accounts.db")
        val noteFile = context.getDatabasePath("caregiver-db")

        // Forcing a DAO call materializes the file.
        runTest {
            db.accountDao().insert(
                AccountEntity(
                    id = "a1",
                    email = "one@example.com",
                    displayName = "One",
                    passwordHash = "h",
                    salt = "s",
                    kdfIterations = 600_000,
                    createdAt = 1L,
                    lastUsedAt = 1L,
                ),
            )
        }

        assertTrue(accountsFile.exists())
        assertFalse(noteFile.exists())
    }

    @Test
    fun daoRoundTripsAnAccount() = runTest {
        val dao = db.accountDao()
        dao.insert(
            AccountEntity(
                id = "a1",
                email = "round@example.com",
                displayName = "Round Trip",
                passwordHash = "hash",
                salt = "salt",
                kdfIterations = 600_000,
                createdAt = 10L,
                lastUsedAt = 20L,
            ),
        )

        val loaded = dao.findById("a1")
        assertNotNull(loaded)
        assertEquals("round@example.com", loaded!!.email)
        assertEquals("Round Trip", loaded.displayName)
        assertEquals(600_000, loaded.kdfIterations)
        assertEquals(10L, loaded.createdAt)
        assertEquals(20L, loaded.lastUsedAt)
    }
}
