package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.backup.BackupData
import com.caregiver.mobile.data.backup.BackupFailureReason
import com.caregiver.mobile.data.backup.BackupReplaceResult
import com.caregiver.mobile.data.backup.BackupRestoreService
import com.caregiver.mobile.data.backup.BackupState
import com.caregiver.mobile.data.backup.BackupStore
import com.caregiver.mobile.data.backup.RestoreOutcome
import com.caregiver.mobile.data.backup.RestorePreparation
import com.caregiver.mobile.data.backup.RoomBackupStore
import com.caregiver.mobile.data.csv.CareNoteCsv
import com.caregiver.mobile.data.csv.CareNoteRecord
import com.caregiver.mobile.data.csv.CsvInsertResult
import com.caregiver.mobile.data.csv.CsvNoteStore
import com.caregiver.mobile.data.csv.CsvSnapshot
import com.caregiver.mobile.data.csv.CsvTransferService
import com.caregiver.mobile.data.csv.ImportPreparation
import com.caregiver.mobile.data.csv.PlannedNote
import com.caregiver.mobile.data.csv.RoomCsvNoteStore
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.NoteEntity
import com.caregiver.mobile.data.local.PlanEntity
import com.caregiver.mobile.data.local.RecipientEntity
import com.caregiver.mobile.data.local.SavedSummaryEntity
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
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
 * #631: backup and CSV transfer must follow the *active* account.
 *
 * Production wires [BackupRestoreService]/[CsvTransferService] through
 * `ActiveBackupStore`/`ActiveCsvNoteStore` (CaregiverApp), which resolve the
 * account's database on every call via `ActiveDatabase` →
 * [PerAccountDatabaseProvider.open]. The delegates below mirror that exactly:
 * the active id is read per call and [PerAccountDatabaseProvider.open]
 * resolves that account's file, so a store can never pin a stale database.
 * (`ActiveDatabase` itself is private to CaregiverApp, an unowned file.)
 *
 * Restore cross-account guard: the backup format carries no owner id — the
 * issue keeps the format unchanged — so the guard is the preview's
 * [BackupState] token. `prepare` captures the *active* account's state digest;
 * `commit` runs `replaceAll` on whichever account is active then, inside the
 * write transaction, and refuses with `Conflict` when the digest no longer
 * matches. A preview prepared under A therefore cannot write into B.
 *
 * CSV cross-account guard: [PlannedNote.recipientId] is the recipient's stable
 * server id resolved at plan time, and `RoomCsvNoteStore.insertAll`
 * re-checks `recipientDao().byServerId` inside the write transaction — a plan
 * prepared under A names recipients B does not have, so every row is rejected
 * and nothing is written.
 *
 * Lives in this owned file because a new `AccountScopedTransferTest.kt` would
 * be UNOWNED by lane B.
 */
@RunWith(RobolectricTestRunner::class)
class AccountScopedTransferTest {
    private lateinit var provider: PerAccountDatabaseProvider
    private var activeAccountId: String = ACCOUNT_A

    @Before
    fun setUp() {
        provider = PerAccountDatabaseProvider(ApplicationProvider.getApplicationContext())
        activeAccountId = ACCOUNT_A
    }

    @After
    fun tearDown() {
        provider.closeAll()
    }

    @Test
    fun backupTakenAsARestoresOnlyIntoA() = runTest {
        // A backs up, then records another note; B accumulates its own data.
        seedAccount(activate(ACCOUNT_A), SCOPE_A)
        val pkg = backupService().create()
        provider.current()!!.noteDao().insertAll(listOf(laterNote(SCOPE_A)))

        seedAccount(activate(ACCOUNT_B), SCOPE_B)

        // Back on A the file restores cleanly; B is never touched.
        activate(ACCOUNT_A)
        val preparation = backupService().prepare(pkg.content)
        assertTrue(preparation is RestorePreparation.Ready)
        val outcome = backupService().commit((preparation as RestorePreparation.Ready).preview)
        assertTrue(outcome is RestoreOutcome.Restored)

        // Replace semantics: A holds exactly the backed-up rows again —
        // the post-backup note is gone, and nothing from B arrived.
        val restoredA = RoomBackupStore(provider.current()!!).snapshot()
        assertEquals(listOf("r-$SCOPE_A"), restoredA.recipients.map { it.serverId })
        assertEquals(listOf("n-$SCOPE_A"), restoredA.notes.map { it.serverId })

        val untouchedB = RoomBackupStore(activate(ACCOUNT_B)).snapshot()
        assertEquals(listOf("r-$SCOPE_B"), untouchedB.recipients.map { it.serverId })
        assertEquals(listOf("n-$SCOPE_B"), untouchedB.notes.map { it.serverId })
    }

    @Test
    fun restoreIntoWrongAccountIsRejected() = runTest {
        // A preview is bound to the state it was built against. Prepared under
        // A, its token digests A's rows; committing after the switch resolves
        // B's database, whose state cannot match, so the write is refused
        // before a single row changes — in either account.
        seedAccount(activate(ACCOUNT_A), SCOPE_A)
        val pkg = backupService().create()
        val preparation = backupService().prepare(pkg.content)
        assertTrue(preparation is RestorePreparation.Ready)

        seedAccount(activate(ACCOUNT_B), SCOPE_B)
        val bBefore = RoomBackupStore(provider.current()!!).snapshot()

        val outcome = backupService().commit((preparation as RestorePreparation.Ready).preview)

        assertTrue(outcome is RestoreOutcome.Failed)
        assertEquals(
            BackupFailureReason.RESTORE_CONFLICT,
            (outcome as RestoreOutcome.Failed).failure.reason,
        )
        // B is byte-for-byte unchanged and A was not written either.
        assertEquals(bBefore, RoomBackupStore(provider.current()!!).snapshot())
        val untouchedA = RoomBackupStore(activate(ACCOUNT_A)).snapshot()
        assertEquals(listOf("r-$SCOPE_A"), untouchedA.recipients.map { it.serverId })
        assertEquals(listOf("n-$SCOPE_A"), untouchedA.notes.map { it.serverId })
    }

    @Test
    fun csvExportContainsOnlyTheActiveAccountRows() = runTest {
        seedAccount(activate(ACCOUNT_A), SCOPE_A)
        seedAccount(activate(ACCOUNT_B), SCOPE_B)

        activate(ACCOUNT_A)
        val aExport = csvService().export()
        assertEquals(1, aExport.recordCount)
        assertTrue(aExport.content.contains("r-$SCOPE_A"))
        assertTrue(aExport.content.contains("note $SCOPE_A"))
        assertFalse(aExport.content.contains(SCOPE_B))

        activate(ACCOUNT_B)
        val bExport = csvService().export()
        assertEquals(1, bExport.recordCount)
        assertTrue(bExport.content.contains("r-$SCOPE_B"))
        assertTrue(bExport.content.contains("note $SCOPE_B"))
        assertFalse(bExport.content.contains(SCOPE_A))
    }

    @Test
    fun csvImportWritesOnlyIntoTheActiveAccount() = runTest {
        seedAccount(activate(ACCOUNT_A), SCOPE_A)
        seedAccount(activate(ACCOUNT_B), SCOPE_B)

        // One CSV row for A's recipient on a date A does not have yet.
        val csv = CareNoteCsv.encode(
            listOf(
                CareNoteRecord(
                    date = "2026-02-02",
                    recipientId = "r-$SCOPE_A",
                    recipientName = "Name $SCOPE_A",
                    mood = "good",
                    appetite = "good",
                    sleep = "good",
                    mobility = "good",
                    medicationTaken = "taken",
                    pain = 3,
                    fall = false,
                    text = "imported $SCOPE_A",
                ),
            ),
        )

        // Prepared and committed while A is active: the row lands in A only.
        activate(ACCOUNT_A)
        val planA = (csvService().prepare(csv) as ImportPreparation.Ready).plan
        assertEquals(1, planA.valid.size)
        assertEquals(1, csvService().commit(planA).imported)
        assertEquals(2, provider.current()!!.noteDao().query(null, null, null).size)
        assertEquals(1, activate(ACCOUNT_B).noteDao().query(null, null, null).size)

        // The same kind of preview committed after a switch cannot write into
        // B: the plan names A's recipient id, which B does not have, so the
        // store rejects the row inside its transaction and nothing is
        // written in either account. (A second date keeps this row from
        // being a duplicate of the one just imported.)
        val csv2 = csv.replace("2026-02-02", "2026-02-03")
        activate(ACCOUNT_A)
        val planB = (csvService().prepare(csv2) as ImportPreparation.Ready).plan
        assertEquals(1, planB.valid.size)
        activate(ACCOUNT_B)
        val summaryB = csvService().commit(planB)
        assertEquals(0, summaryB.imported)
        assertEquals(1, summaryB.rejected)
        assertEquals(1, provider.current()!!.noteDao().query(null, null, null).size)
        assertEquals(2, activate(ACCOUNT_A).noteDao().query(null, null, null).size)
    }

    /** Switches the active account and returns its database, like a sign-in. */
    private fun activate(accountId: String): AppDatabase {
        val db = provider.open(accountId)
        activeAccountId = accountId
        return db
    }

    private fun backupService() = BackupRestoreService(
        AccountResolvingBackupStore(provider) { activeAccountId },
        "test-account-scope",
    )

    private fun csvService() = CsvTransferService(
        AccountResolvingCsvNoteStore(provider) { activeAccountId },
    )

    private suspend fun seedAccount(db: AppDatabase, tag: String) {
        db.recipientDao().insertAll(
            listOf(RecipientEntity(serverId = "r-$tag", name = "Name $tag", active = true)),
        )
        db.noteDao().insertAll(listOf(baseNote(tag)))
    }

    private fun baseNote(tag: String) = NoteEntity(
        serverId = "n-$tag",
        recipientId = "r-$tag",
        date = "2026-01-01",
        mood = "good",
        appetite = "good",
        sleep = "good",
        mobility = "good",
        medicationTaken = "taken",
        pain = 0,
        fall = false,
        text = "note $tag",
    )

    private fun laterNote(tag: String) = baseNote(tag).copy(
        serverId = "n-$tag-later",
        date = "2026-01-02",
        text = "later $tag",
    )

    /**
     * Mirrors production `ActiveBackupStore`: the account id is read on every
     * call and [PerAccountDatabaseProvider.open] resolves that account's file,
     * so the store can never pin a stale database across a switch.
     */
    private class AccountResolvingBackupStore(
        private val provider: PerAccountDatabaseProvider,
        private val activeAccountId: () -> String,
    ) : BackupStore {
        private fun store(): BackupStore = RoomBackupStore(provider.open(activeAccountId()))

        override suspend fun snapshot(): BackupData = store().snapshot()
        override suspend fun state(): BackupState = store().state()
        override suspend fun replaceAll(data: BackupData, expected: BackupState): BackupReplaceResult =
            store().replaceAll(data, expected)
    }

    /** CSV counterpart of [AccountResolvingBackupStore]; preserves dates like RoomCsvNoteStore. */
    private class AccountResolvingCsvNoteStore(
        private val provider: PerAccountDatabaseProvider,
        private val activeAccountId: () -> String,
    ) : CsvNoteStore {
        override val preservesDates: Boolean = true

        private fun store(): CsvNoteStore = RoomCsvNoteStore(provider.open(activeAccountId()))

        override suspend fun snapshot(): CsvSnapshot = store().snapshot()
        override suspend fun insertAll(rows: List<PlannedNote>): List<CsvInsertResult> =
            store().insertAll(rows)
    }

    companion object {
        private const val ACCOUNT_A = "scope-a"
        private const val ACCOUNT_B = "scope-b"
        private const val SCOPE_A = "alpha"
        private const val SCOPE_B = "bravo"
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
