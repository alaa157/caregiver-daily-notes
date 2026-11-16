package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.PlanEntity
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
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
        listOf("", "-wal", "-shm").forEach { suffix ->
            val p = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}owner-1$suffix")
            if (p.isDirectory) p.deleteRecursively() else if (p.exists()) p.delete()
            File(legacyFile.path + suffix).delete()
            File(legacyFile.path + suffix + LegacyMigration.BACKUP_SUFFIX).delete()
        }
        context.getSharedPreferences("legacy_migration", Context.MODE_PRIVATE)
            .edit().clear().apply()
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

    // ---- #625: move the legacy database into the owner account ----

    @Test
    fun migrationRenamesDbAndSidecarsThenNoOps() = runTest {
        // A real Room file at the legacy name, so "readable under the owner" is meaningful.
        val legacyDb = AppDatabase.create(context)
        legacyDb.openHelper.writableDatabase.use { }
        legacyDb.close()
        assertTrue(legacyFile.exists())

        val wal = File(legacyFile.path + "-wal").also { it.writeText("wal") }
        val shm = File(legacyFile.path + "-shm").also { it.writeText("shm") }

        val migration = LegacyMigration(context)
        assertTrue(migration.migrate("owner-1"))
        assertTrue(migration.isCompleted())

        val target = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}owner-1")
        assertFalse(legacyFile.exists())
        assertFalse(wal.exists())
        assertFalse(shm.exists())
        assertTrue(target.exists())
        assertTrue(File(target.path + "-wal").exists())
        assertTrue(File(target.path + "-shm").exists())

        // The owner account can open the moved database.
        val provider = PerAccountDatabaseProvider(context)
        try {
            val moved = provider.databaseFor("owner-1")
            moved.openHelper.writableDatabase.use { db ->
                val cursor = db.query("SELECT COUNT(*) FROM notes")
                assertNotNull(cursor)
                cursor.close()
            }
        } finally {
            provider.closeAll()
        }

        // Second launch is a no-op and leaves the files alone.
        assertFalse(migration.migrate("owner-1"))
        assertTrue(target.exists())
    }

    @Test
    fun failedRenameRollsBackAndLeavesLegacyFileIntact() = runTest {
        createLegacyFile()
        val original = legacyFile.readText()
        // A directory at the target forces renameTo to fail.
        val target = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}owner-1")
        assertTrue(target.mkdirs() || target.isDirectory)

        val migration = LegacyMigration(context)
        assertThrows(IllegalStateException::class.java) { runTest { migration.migrate("owner-1") } }

        assertTrue(legacyFile.exists())
        assertEquals(original, legacyFile.readText())
        assertFalse(migration.isCompleted())
    }

    @Test
    fun migrationDelegatesSettingsBeforeTouchingFiles() = runTest {
        createLegacyFile()
        val calls = mutableListOf<String>()
        val migration = LegacyMigration(
            context,
            migrateAiKey = { calls += "key:$it" },
            migrateOnboarding = { calls += "onboarding:$it" },
        )

        assertTrue(migration.migrate("owner-1"))
        assertEquals(listOf("key:owner-1", "onboarding:owner-1"), calls)
    }

    @Test
    fun noLegacyFileIsANoOp() = runTest {
        assertFalse(legacyFile.exists())
        val migration = LegacyMigration(context)

        assertFalse(migration.migrate("owner-1"))
        assertFalse(migration.isCompleted())
    }

    // ---- #626: backup / rollback safety net ----

    @Test
    fun backupSurvivesSuccessAndIsDeletedOnNextLaunch() = runTest {
        createLegacyFile()
        val migration = LegacyMigration(context)

        assertTrue(migration.migrate("owner-1"))
        val backup = File(legacyFile.path + LegacyMigration.BACKUP_SUFFIX)
        assertTrue(backup.exists())
        assertFalse(legacyFile.exists())

        // "Next launch": the integrator calls this once at startup.
        assertTrue(migration.cleanupBackupIfDue())
        assertFalse(backup.exists())
        assertFalse(migration.cleanupBackupIfDue())
    }

    @Test
    fun failedMoveRestoresOriginalByteForByteAndRemovesBackup() = runTest {
        createLegacyFile()
        val payload = "sensitive-but-safe-fixture-bytes"
        legacyFile.writeText(payload)
        val original = legacyFile.readBytes()
        val target = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}owner-1")
        assertTrue(target.mkdirs() || target.isDirectory)

        val migration = LegacyMigration(context)
        assertThrows(IllegalStateException::class.java) { runTest { migration.migrate("owner-1") } }

        assertTrue(legacyFile.exists())
        assertArrayEquals(original, legacyFile.readBytes())
        assertFalse(File(legacyFile.path + LegacyMigration.BACKUP_SUFFIX).exists())
        assertFalse(migration.isCompleted())
    }

    @Test
    fun failedBackupCopyAbortsBeforeTouchingTheOriginal() = runTest {
        createLegacyFile()
        legacyFile.writeText("do not lose this")
        val original = legacyFile.readBytes()
        val migration = LegacyMigration(context, backupCopy = { _, _ ->
            throw java.io.IOException("simulated no space")
        })

        assertThrows(IllegalStateException::class.java) { runTest { migration.migrate("owner-1") } }

        assertArrayEquals(original, legacyFile.readBytes())
        assertFalse(context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}owner-1").exists())
        assertFalse(File(legacyFile.path + LegacyMigration.BACKUP_SUFFIX).exists())
        assertFalse(migration.isCompleted())
    }

    // ---- #627: end-to-end migration scenarios with a generated legacy fixture ----

    @Test
    fun successfulMigrationMakesLegacyDataReadableUnderOwner() = runTest {
        createLegacyFixture("plan-1", "legacy plan body")

        assertTrue(LegacyMigration(context).migrate("owner-1"))

        val plan = readOwnerPlan("plan-1")
        assertNotNull(plan)
        assertEquals("legacy plan body", plan!!.versionsJson)
        assertEquals("legacy-recipient", plan.recipientId)
        assertEquals(42L, plan.updatedAt)
        assertFalse(legacyFile.exists())
    }

    @Test
    fun interruptedMigrationRecoversOnNextLaunch() = runTest {
        createLegacyFixture("plan-2", "survives interruption")
        // Simulate a process kill after the main rename: backup exists, main
        // file is already at the owner's name, in-progress flag still set.
        File(legacyFile.path + LegacyMigration.BACKUP_SUFFIX)
            .also { legacyFile.copyTo(it, overwrite = true) }
        val target = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}owner-1")
        assertTrue(legacyFile.renameTo(target))
        context.getSharedPreferences("legacy_migration", Context.MODE_PRIVATE)
            .edit().putBoolean(LegacyMigration.MIGRATION_IN_PROGRESS, true).apply()
        assertFalse(legacyFile.exists())
        assertTrue(target.exists())

        assertTrue(LegacyMigration(context).migrate("owner-1"))

        val plan = readOwnerPlan("plan-2")
        assertNotNull(plan)
        assertEquals("survives interruption", plan!!.versionsJson)
        assertFalse(legacyFile.exists())
    }

    @Test
    fun secondMigrationRunIsANoOp() = runTest {
        createLegacyFixture("plan-3", "kept once")
        val migration = LegacyMigration(context)

        assertTrue(migration.migrate("owner-1"))
        assertFalse(migration.migrate("owner-1"))

        assertEquals("kept once", readOwnerPlan("plan-3")!!.versionsJson)
    }

    @Test
    fun freshInstallSkipsMigrationEntirely() = runTest {
        val migration = LegacyMigration(context)

        assertFalse(migration.migrate("owner-1"))
        assertFalse(migration.isCompleted())
        assertFalse(context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}owner-1").exists())
        assertEquals(LegacyState.None, detector().detect())
    }

    @Test
    fun detectionReportsTheRightStateForEachScenario() = runTest {
        assertEquals(LegacyState.None, detector().detect())

        createLegacyFile()
        assertEquals(LegacyState.NeedsClaim, detector().detect())

        seedAccount()
        assertEquals(LegacyState.AlreadyMigrated, detector().detect())
    }

    /** Writes a real Room file at the legacy name with one plan row. */
    private suspend fun createLegacyFixture(planId: String, body: String) {
        val db = AppDatabase.create(context)
        try {
            db.planDao().insert(
                PlanEntity(
                    id = planId,
                    recipientId = "legacy-recipient",
                    versionsJson = body,
                    updatedAt = 42L,
                ),
            )
        } finally {
            db.close()
        }
    }

    private suspend fun readOwnerPlan(planId: String): PlanEntity? {
        val provider = PerAccountDatabaseProvider(context)
        return try {
            provider.databaseFor("owner-1").planDao().byId(planId)
        } finally {
            provider.closeAll()
        }
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
