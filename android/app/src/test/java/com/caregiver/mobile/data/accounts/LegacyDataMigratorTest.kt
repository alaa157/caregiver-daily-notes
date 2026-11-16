package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * #623: detection of an unmigrated legacy install. Detection is read-only —
 * in particular it must not create `accounts.db` just to discover it is empty.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyDataMigratorTest {
    private lateinit var context: Context
    private lateinit var accountsDb: AccountsDatabase
    private lateinit var activeAccountId: MutableStateFlow<String?>
    private lateinit var legacyFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        accountsDb = AccountsDatabase.create(context)
        activeAccountId = MutableStateFlow(null)
        legacyFile = context.getDatabasePath(PerAccountDatabaseProvider.LEGACY_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        accountsDb.close()
        context.deleteDatabase(LegacyInstallDetector.ACCOUNTS_DB_NAME)
        if (legacyFile.exists()) legacyFile.delete()
    }

    @Test
    fun freshInstallReturnsNoneAndCreatesNoFiles() = runTest {
        assertFalse(legacyFile.exists())
        assertFalse(accountsFile().exists())

        assertEquals(LegacyState.None, detector().detect())

        assertFalse(legacyFile.exists())
        assertFalse(accountsFile().exists())
    }

    @Test
    fun legacyInstallWithNoAccountsReturnsNeedsClaim() = runTest {
        createLegacyFile()

        assertEquals(LegacyState.NeedsClaim, detector().detect())
    }

    @Test
    fun accountsAlreadyPresentReturnsAlreadyMigrated() = runTest {
        seedAccount()
        assertFalse(legacyFile.exists())

        assertEquals(LegacyState.AlreadyMigrated, detector().detect())
    }

    @Test
    fun activeAccountReturnsAlreadyMigrated() = runTest {
        activeAccountId.value = "acct-active"

        assertEquals(LegacyState.AlreadyMigrated, detector().detect())
    }

    @Test
    fun legacyFilePlusAccountsIsAlreadyMigrated() = runTest {
        createLegacyFile()
        seedAccount()

        assertEquals(LegacyState.AlreadyMigrated, detector().detect())
    }

    @Test
    fun demoModeNeverReportsNeedsClaim() = runTest {
        createLegacyFile()

        assertEquals(LegacyState.None, detector(demoMode = true).detect())
    }

    @Test
    fun detectionDoesNotModifyTheLegacyFile() = runTest {
        createLegacyFile()
        val before = legacyFile.lastModified()

        detector().detect()
        detector().detect()

        assertTrue(legacyFile.exists())
        assertEquals(before, legacyFile.lastModified())
    }

    private fun detector(demoMode: Boolean = false) = LegacyInstallDetector(
        context = context,
        accountDao = accountsDb.accountDao(),
        activeAccountId = activeAccountId,
        demoMode = demoMode,
    )

    private fun accountsFile(): File = context.getDatabasePath(LegacyInstallDetector.ACCOUNTS_DB_NAME)

    private fun createLegacyFile(): File = legacyFile.also {
        it.parentFile?.mkdirs()
        it.writeText("legacy marker")
    }

    private suspend fun seedAccount() {
        accountsDb.accountDao().insert(
            AccountEntity(
                id = "acct-existing",
                email = "owner@example.com",
                displayName = "Owner",
                passwordHash = "hash",
                salt = "salt",
                kdfIterations = 1,
                createdAt = 1L,
                lastUsedAt = 1L,
            ),
        )
    }
}
