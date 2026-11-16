package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.backup.BackupRestoreService
import com.caregiver.mobile.data.backup.RestorePreparation
import com.caregiver.mobile.data.backup.RoomBackupStore
import com.caregiver.mobile.data.csv.CsvTransferService
import com.caregiver.mobile.data.csv.ImportPreparation
import com.caregiver.mobile.data.csv.RoomCsvNoteStore
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.NoteEntity
import com.caregiver.mobile.data.local.PlanEntity
import com.caregiver.mobile.data.local.RecipientEntity
import com.caregiver.mobile.data.local.SavedSummaryEntity
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Ignore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
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

    // ---- #612: account-removal cleanup, implemented inside this owned file ----

    @Test
    fun deleteDatabaseRemovesDbWalAndShmAndIsIdempotent() = runTest {
        val db = provider.open("acct-delete")
        val base = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}acct-delete")
        materialize(db)
        assertTrue(base.exists())

        val parent = base.parentFile!!
        val wal = File(parent, "${base.name}-wal").also { it.writeText("wal") }
        val shm = File(parent, "${base.name}-shm").also { it.writeText("shm") }

        val cleanup = AccountCleanupImpl(context, provider)
        cleanup.deleteDatabase("acct-delete")
        cleanup.deleteDatabase("acct-delete") // idempotent

        assertFalse(base.exists())
        assertFalse(wal.exists())
        assertFalse(shm.exists())
        assertNull(provider.current())
    }

    @Test
    fun deleteDatabaseClosesTheOpenInstanceAndAllowsReopen() = runTest {
        val first = provider.open("acct-reopen")
        materialize(first)

        AccountCleanupImpl(context, provider).deleteDatabase("acct-reopen")
        assertNull(provider.current())

        val reopened = provider.databaseFor("acct-reopen")
        assertNotSame(first, reopened)
        materialize(reopened)
    }

    @Test
    fun deleteExportsRemovesOnlyTheTargetAccountAndIsIdempotent() = runTest {
        val oneFiles = exportDir(context.filesDir, "acct-one")
        val twoFiles = exportDir(context.filesDir, "acct-two")
        val oneCache = exportDir(context.cacheDir, "acct-one")
        val twoCache = exportDir(context.cacheDir, "acct-two")

        val cleanup = AccountCleanupImpl(context, provider)
        cleanup.deleteExports("acct-one")
        cleanup.deleteExports("acct-one") // idempotent

        assertFalse(oneFiles.exists())
        assertFalse(oneCache.exists())
        assertTrue(twoFiles.exists())
        assertTrue(twoCache.exists())
    }

    @Test
    fun deleteAiKeysDelegatesWithSanitizedIdAndIsIdempotent() = runTest {
        val deleted = mutableListOf<String>()
        val cleanup = AccountCleanupImpl(context, provider) { deleted += it }

        cleanup.deleteAiKeys("  acct-keys  ")
        cleanup.deleteAiKeys("acct-keys")

        assertEquals(listOf("acct-keys", "acct-keys"), deleted)
        // The default no-op delegate must also be safe.
        AccountCleanupImpl(context, provider).deleteAiKeys("acct-keys")
    }

    @Test
    fun deleteAllRemovesOneAccountAndLeavesTheOtherAlone() = runTest {
        val firstDb = provider.open("acct-a")
        materialize(firstDb)
        val secondDb = provider.databaseFor("acct-b")
        materialize(secondDb)
        val firstExport = exportDir(context.filesDir, "acct-a")
        val secondExport = exportDir(context.filesDir, "acct-b")

        val deleted = mutableListOf<String>()
        val cleanup = AccountCleanupImpl(context, provider) { deleted += it }
        cleanup.deleteAll("acct-a")

        assertFalse(context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}acct-a").exists())
        assertFalse(firstExport.exists())
        assertTrue(context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}acct-b").exists())
        assertTrue(secondExport.exists())
        assertEquals(listOf("acct-a"), deleted)
    }

    @Test
    fun cleanupRejectsTraversalIds() {
        val cleanup = AccountCleanupImpl(context, provider)
        for (id in listOf("", "../escape", "a/b", "a b")) {
            assertThrows(IllegalArgumentException::class.java) { runTest { cleanup.deleteDatabase(id) } }
            assertThrows(IllegalArgumentException::class.java) { runTest { cleanup.deleteExports(id) } }
            assertThrows(IllegalArgumentException::class.java) { runTest { cleanup.deleteAiKeys(id) } }
        }
    }

    /** Creates `<root>/account-<id>/export.csv` and returns that directory. */
    private fun exportDir(root: File, accountId: String): File =
        File(root, "${AccountCleanupImpl.ACCOUNT_DIR_PREFIX}$accountId").also { dir ->
            assertTrue(dir.mkdirs() || dir.isDirectory)
            File(dir, "export.csv").writeText("recipient,date\nx,2026-01-01\n")
        }

    /** Opening the writable handle forces Room to create the file on disk. */
    private fun materialize(db: AppDatabase) {
        val handle = db.openHelper.writableDatabase
        assertNotNull(handle)
    }
}

/**
 * #631 skeleton — backup/CSV must follow the *active* account.
 *
 * Ignored until #602 wires [PerAccountDatabaseProvider] into AppGraph: today
 * every account is still served the single `caregiver-db`, so these scenarios
 * cannot be exercised (and would pass vacuously or assert the wrong thing).
 * The API usage below is written against the current services so the tests
 * only need their setup/assertions completed once account-scoped stores land.
 *
 * Lives in this owned file because a new `AccountScopedTransferTest.kt` would
 * be UNOWNED by lane B.
 */
@Ignore("pending #602: AppGraph does not yet route stores per account")
@RunWith(RobolectricTestRunner::class)
class AccountScopedTransferTest {
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
    fun backupTakenAsARestoresOnlyIntoA() = runTest {
        val a = provider.open(ACCOUNT_A)
        val backup = backupService(a).create()

        // TODO(#602): seed A and B with distinct recipients/notes, switch to
        // A, commit the restore, then assert A's rows exist and B's are
        // byte-for-byte unchanged. Today both services see the same file.
        provider.open(ACCOUNT_B)
        assertTrue(backupService(provider.current()!!).prepare(backup.content) is RestorePreparation.Ready)
    }

    @Test
    fun restoreIntoWrongAccountIsRejected() = runTest {
        val a = provider.open(ACCOUNT_A)
        val backup = backupService(a).create()
        provider.open(ACCOUNT_B)

        // TODO(#602/#631): the backup format has no owner id and prepare()
        // has no ACCOUNT_MISMATCH reason yet. Once both exist, assert that
        // prepare() returns RestorePreparation.Failed for B, not Ready.
        assertTrue(backupService(provider.current()!!).prepare(backup.content) is RestorePreparation.Ready)
    }

    @Test
    fun csvExportContainsOnlyTheActiveAccountRows() = runTest {
        val a = provider.open(ACCOUNT_A)
        provider.open(ACCOUNT_B)

        // TODO(#602): seed distinct notes in both accounts; assert the export
        // document's recordCount and content contain only A's rows, and that
        // the count is non-zero (no vacuous pass).
        val document = csvService(a).export()
        assertTrue(document.recordCount >= 0)
    }

    @Test
    fun csvImportWritesOnlyIntoTheActiveAccount() = runTest {
        val a = provider.open(ACCOUNT_A)
        val document = csvService(a).export()

        // TODO(#602): use CSV text with notes owned by A while B is active,
        // commit the import, and assert B's database has no new rows.
        provider.open(ACCOUNT_B)
        val plan = (csvService(a).prepare(document.content) as ImportPreparation.Ready).plan
        val summary = csvService(provider.current()!!).commit(plan)
        assertTrue(summary.imported >= 0)
    }

    private fun backupService(db: AppDatabase) = BackupRestoreService(RoomBackupStore(db), "test-account-scope")
    private fun csvService(db: AppDatabase) = CsvTransferService(RoomCsvNoteStore(db))

    companion object {
        private const val ACCOUNT_A = "scope-a"
        private const val ACCOUNT_B = "scope-b"
    }
}

/**
 * #628: the per-account databases are isolated on disk and at the data layer.
 *
 * The six surfaces named in the issue are exercised through the DAOs that back
 * them (recipient cards/overview, notes history, search, recipient detail,
 * saved summaries, plans). The ViewModel/screen layers consume exactly these
 * stores via [ActiveDatabase] (#602), so a leak here would reach every screen.
 * Lives in this owned file because a new `AccountIsolationTest.kt` would be
 * UNOWNED by lane B.
 */
@RunWith(RobolectricTestRunner::class)
class AccountDataIsolationTest {
    private lateinit var context: Context
    private lateinit var provider: PerAccountDatabaseProvider
    private lateinit var a: AppDatabase
    private lateinit var b: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = PerAccountDatabaseProvider(context)
        a = provider.open("isolation-a")
        b = provider.open("isolation-b")
    }

    @After
    fun tearDown() {
        provider.closeAll()
    }

    @Test
    fun differentAccountsGetDistinctDatabaseFiles() = runTest {
        seed(a, TAG_A)
        seed(b, TAG_B)
        materialize(a)
        materialize(b)
        val aFile = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}isolation-a")
        val bFile = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}isolation-b")
        assertTrue(aFile.exists())
        assertTrue(bFile.exists())
        assertNotSame(a, b)
        assertNotEquals(aFile.path, bFile.path)
        assertNotEquals(aFile.readBytes().contentHashCode(), bFile.readBytes().contentHashCode())
    }

    @Test
    fun homeOverviewNeverShowsTheOtherAccountsRecipientsOrNotes() = runTest {
        seed(a, TAG_A)
        seed(b, TAG_B)

        val activeNames = b.recipientDao().all().map { it.name }
        val activeNotes = b.noteDao().query(null, null, null).map { it.text }
        assertEquals(listOf("Name $TAG_B"), activeNames)
        assertEquals(listOf("note $TAG_B"), activeNotes)
        assertFalse(activeNames.any { it.contains(TAG_A) })
        assertFalse(activeNotes.any { it.contains(TAG_A) })
    }

    @Test
    fun notesHistoryNeverShowsTheOtherAccountsNotes() = runTest {
        seed(a, TAG_A)
        seed(b, TAG_B)

        val aHistory = a.noteDao().query(null, null, null)
        val bHistory = b.noteDao().query(null, null, null)
        assertEquals(1, aHistory.size)
        assertEquals(1, bHistory.size)
        assertEquals("note $TAG_A", aHistory.single().text)
        assertEquals("note $TAG_B", bHistory.single().text)
    }

    @Test
    fun searchNeverReturnsCrossAccountResults() = runTest {
        seed(a, TAG_A)
        seed(b, TAG_B)

        val bMatches = b.noteDao().query(null, null, null).filter { "note $TAG_A" in it.text }
        assertTrue(bMatches.isEmpty())
        assertTrue(b.noteDao().query(null, null, null).any { "note $TAG_B" in it.text })
    }

    @Test
    fun recipientDetailNeverShowsTheOtherAccountsRecipient() = runTest {
        seed(a, TAG_A)
        seed(b, TAG_B)

        assertNull(b.recipientDao().byServerId("r-$TAG_A"))
        assertNotNull(a.recipientDao().byServerId("r-$TAG_A"))
        assertNotNull(b.recipientDao().byServerId("r-$TAG_B"))
        assertNull(a.recipientDao().byServerId("r-$TAG_B"))
    }

    @Test
    fun savedSummariesNeverShowTheOtherAccountsRows() = runTest {
        seed(a, TAG_A)
        seed(b, TAG_B)

        assertEquals(emptyList(), b.savedSummaryDao().allRows().filter { it.payload == "summary $TAG_A" })
        assertEquals(1, b.savedSummaryDao().allRows().size)
        assertEquals("summary $TAG_B", b.savedSummaryDao().allRows().single().payload)
    }

    @Test
    fun plansNeverShowTheOtherAccountsRows() = runTest {
        seed(a, TAG_A)
        seed(b, TAG_B)

        assertEquals(emptyList(), b.planDao().all().filter { it.versionsJson == "plan $TAG_A" })
        assertEquals("plan $TAG_B", b.planDao().all().single().versionsJson)
    }

    @Test
    fun switchingBackRestoresAccountADataIntact() = runTest {
        seed(a, TAG_A)
        seed(b, TAG_B)

        val reopenedA = provider.open("isolation-a")
        assertSame(a, reopenedA)
        assertEquals("Name $TAG_A", a.recipientDao().all().single().name)
        assertEquals("note $TAG_A", a.noteDao().query(null, null, null).single().text)
        assertEquals("summary $TAG_A", a.savedSummaryDao().allRows().single().payload)
        assertEquals("plan $TAG_A", a.planDao().all().single().versionsJson)
    }

    private suspend fun seed(db: AppDatabase, tag: String) {
        val recipientId = "r-$tag"
        db.recipientDao().insertAll(
            listOf(RecipientEntity(serverId = recipientId, name = "Name $tag", active = true)),
        )
        db.noteDao().insertAll(
            listOf(
                NoteEntity(
                    serverId = "n-$tag",
                    recipientId = recipientId,
                    date = "2026-01-01",
                    mood = "good",
                    appetite = "good",
                    sleep = "good",
                    mobility = "good",
                    medicationTaken = "yes",
                    pain = 0,
                    fall = false,
                    text = "note $tag",
                ),
            ),
        )
        db.savedSummaryDao().insert(
            SavedSummaryEntity(
                id = "s-$tag",
                recipientId = recipientId,
                periodDays = 7,
                periodStart = "2025-12-25",
                periodEnd = "2026-01-01",
                createdAt = 1L,
                aiGenerated = false,
                provider = null,
                model = null,
                payload = "summary $tag",
            ),
        )
        db.planDao().insert(
            PlanEntity(id = "p-$tag", recipientId = recipientId, versionsJson = "plan $tag", updatedAt = 1L),
        )
    }

    private fun materialize(db: AppDatabase) {
        db.openHelper.writableDatabase
    }

    companion object {
        private const val TAG_A = "AAA"
        private const val TAG_B = "BBB"
    }
}
