package com.caregiver.mobile.data.accounts

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Test-only database: the production [AccountsDatabase] is #583, not this issue. */
@Database(entities = [AccountEntity::class], version = 1, exportSchema = false)
abstract class TestAccountsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
}

/**
 * #582: account DAO round-trip against in-memory Room — insert, read by id
 * and email, lastUsed ordering, delete — plus the unique-email guard.
 */
@RunWith(RobolectricTestRunner::class)
class AccountDaoTest {
    private lateinit var db: TestAccountsDatabase
    private lateinit var dao: AccountDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TestAccountsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.accountDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun account(
        id: String,
        email: String,
        lastUsedAt: Long = 0L,
    ) = AccountEntity(
        id = id,
        email = email,
        displayName = "Caregiver $id",
        passwordHash = "hash-$id",
        salt = "salt-$id",
        kdfIterations = 600_000,
        createdAt = 1_000L,
        lastUsedAt = lastUsedAt,
    )

    @Test
    fun insertReadUpdateAndDelete() = runTest {
        dao.insert(account("a1", "one@example.com", lastUsedAt = 100L))
        dao.insert(account("a2", "two@example.com", lastUsedAt = 300L))
        dao.insert(account("a3", "three@example.com", lastUsedAt = 200L))

        val byId = dao.findById("a2")
        assertNotNull(byId)
        assertEquals("two@example.com", byId!!.email)
        assertEquals("Caregiver a2", byId.displayName)
        assertEquals(600_000, byId.kdfIterations)

        assertEquals("a1", dao.findByEmail("one@example.com")?.id)
        assertNull(dao.findByEmail("missing@example.com"))
        assertNull(dao.findById("missing"))

        // listAll is most-recently-used first.
        assertEquals(listOf("a2", "a3", "a1"), dao.listAll().map { it.id })

        assertEquals(1, dao.updateLastUsed("a1", 400L))
        assertEquals(400L, dao.findById("a1")!!.lastUsedAt)
        assertEquals(listOf("a1", "a2", "a3"), dao.listAll().map { it.id })

        assertEquals(1, dao.deleteById("a2"))
        assertNull(dao.findById("a2"))
        assertEquals(listOf("a1", "a3"), dao.listAll().map { it.id })
        assertEquals(0, dao.deleteById("a2"))
    }

    @Test
    fun duplicateEmailAbortsInsteadOfOverwriting() = runTest {
        dao.insert(account("a1", "dup@example.com"))
        try {
            dao.insert(account("a2", "dup@example.com"))
            throw AssertionError("expected a constraint violation")
        } catch (e: SQLiteConstraintException) {
            // expected: the unique index rejects the second row
        }
        // The original row is untouched.
        assertEquals("a1", dao.findByEmail("dup@example.com")?.id)
    }

    @Test
    fun uniqueIndexRejectsTwoSpellingsThatNormalizeToTheSameEmail() = runTest {
        // Callers normalize before insert (#584), so two differently-cased
        // inputs reach the DAO as the same canonical key. The unique index
        // must still reject the second row — this is the final guard against
        // a concurrent sign-up race.
        val first = EmailNormalizer.normalize("  A@B.COM ")
        val second = EmailNormalizer.normalize("a@b.com")
        assertNotNull(first)
        assertEquals(first, second)

        dao.insert(account("a1", first!!))
        try {
            dao.insert(account("a2", second!!))
            throw AssertionError("expected a constraint violation")
        } catch (e: SQLiteConstraintException) {
            // expected: the unique index rejects the second row
        }
        assertEquals("a1", dao.findByEmail(first)?.id)
    }
}
