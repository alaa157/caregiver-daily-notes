package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertThrows
import org.robolectric.RobolectricTestRunner

/**
 * #601: one note database file per account, one cached instance per id, with
 * a single "current" account. The legacy `caregiver-db` stays reachable because
 * [AppDatabase.create] is deliberately left untouched.
 */
@RunWith(RobolectricTestRunner::class)
class PerAccountDatabaseProviderTest {
    private lateinit var context: Context
    private lateinit var provider: PerAccountDatabaseProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = PerAccountDatabaseProvider(context)
    }

    @After
    fun tearDown() {
        provider.closeAll()
    }

    @Test
    fun twoAccountsGetTwoFilesAndTwoInstances() {
        val first = provider.databaseFor("1111-aaaa")
        val second = provider.databaseFor("2222-bbbb")
        assertNotSame(first, second)

        materialize(first)
        materialize(second)

        val firstFile = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}1111-aaaa")
        val secondFile = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}2222-bbbb")
        assertTrue(firstFile.exists())
        assertTrue(secondFile.exists())
        assertFalse(firstFile.path == secondFile.path)
    }

    @Test
    fun sameIdReturnsTheCachedInstance() {
        val first = provider.databaseFor("acct-cached")
        val second = provider.databaseFor("acct-cached")
        assertSame(first, second)
    }

    @Test
    fun closeClosesCurrentAndAllowsReopen() {
        val first = provider.open("acct-close")
        materialize(first)

        provider.close()
        assertNull(provider.current())

        val reopened = provider.databaseFor("acct-close")
        assertNotSame(first, reopened)
        materialize(reopened)
        assertNotNull(context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}acct-close"))
    }

    @Test
    fun currentTracksOpenAndClose() {
        assertNull(provider.current())

        val opened = provider.open("acct-current")
        assertSame(opened, provider.current())

        provider.close()
        assertNull(provider.current())
    }

    @Test
    fun closeAllClearsEveryAccount() {
        val first = provider.open("acct-one")
        val second = provider.databaseFor("acct-two")
        assertNotNull(first)
        assertNotNull(second)

        provider.closeAll()
        assertNull(provider.current())
        assertNotSame(second, provider.databaseFor("acct-two"))
        assertNotSame(first, provider.databaseFor("acct-one"))
    }

    @Test
    fun traversalAndBlankIdsAreRejected() {
        val invalid = listOf("", "   ", "a/b", "../escape", "..", "a b", "a\\b", "a.b")
        invalid.forEach { id ->
            assertThrows(IllegalArgumentException::class.java) {
                provider.databaseFor(id)
            }
            assertThrows(IllegalArgumentException::class.java) {
                provider.open(id)
            }
        }
    }

    @Test
    fun sanitizeTrimsAndAcceptsUuidLikeIds() {
        assertEquals("abc-123", PerAccountDatabaseProvider.sanitize("  abc-123  "))
        assertEquals("a", PerAccountDatabaseProvider.sanitize("a"))
    }

    @Test
    fun legacyDatabaseNameIsStillReachable() {
        val legacy = AppDatabase.create(context)
        try {
            materialize(legacy)
            assertTrue(context.getDatabasePath(PerAccountDatabaseProvider.LEGACY_DATABASE_NAME).exists())
        } finally {
            legacy.close()
        }
    }

    /** Opening the writable handle forces Room to create the file on disk. */
    private fun materialize(db: AppDatabase) {
        val handle = db.openHelper.writableDatabase
        assertNotNull(handle)
    }
}
