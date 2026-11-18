package com.caregiver.mobile.data.accounts

import android.content.Context
import android.util.Log
import com.caregiver.mobile.BuildConfig
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Outcome of looking for an unmigrated pre-accounts install (issue #623). */
enum class LegacyState {
    /** Fresh install: no legacy database and no accounts. */
    None,

    /** Legacy database present, no accounts and no active account: offer the claim flow. */
    NeedsClaim,

    /** Accounts already exist (or an account is signed in): nothing to claim. */
    AlreadyMigrated,
}

/**
 * Detects a legacy install once per launch (epic #581, issue #623).
 *
 * A legacy install is the legacy `caregiver-db` file present **and** the
 * accounts table empty **and** no active account id stored. Detection is
 * cheap, read-only and idempotent: it stats files and, only when the accounts
 * database already exists, reads the account list. It never opens the legacy
 * database and never creates a file — in particular it does not open
 * `accounts.db` on a fresh install just to discover it is empty.
 *
 * This class lives here because the issue's `LegacyInstallDetector.kt` path is
 * UNOWNED by lane B (guard returns rc 2); `LegacyDataMigrator.kt` is the owned
 * home for legacy-install logic.
 *
 * @param activeAccountId the shared `SessionStore.activeAccountId` flow; the
 *   integrator supplies it so this detector never opens a second DataStore.
 * @param demoMode demo builds must never report [LegacyState.NeedsClaim].
 */
class LegacyInstallDetector(
    private val context: Context,
    private val accountDao: AccountDao,
    private val activeAccountId: Flow<String?>,
    private val demoMode: Boolean = BuildConfig.DEMO_BACKEND && !BuildConfig.LOCAL_MODE,
) {
    suspend fun detect(): LegacyState {
        if (demoMode) return LegacyState.None

        val legacyExists =
            context.getDatabasePath(PerAccountDatabaseProvider.LEGACY_DATABASE_NAME).exists()

        // Opening accounts.db through Room would create it on a fresh install,
        // which would violate the "detection does not modify files" rule.
        val accountsFile = context.getDatabasePath(ACCOUNTS_DB_NAME)
        val hasAccounts = accountsFile.exists() && accountDao.listAll().isNotEmpty()

        val hasActiveAccount = activeAccountId.first() != null

        return when {
            hasAccounts || hasActiveAccount -> LegacyState.AlreadyMigrated
            legacyExists -> LegacyState.NeedsClaim
            else -> LegacyState.None
        }
    }

    companion object {
        /** The accounts credentials database, owned by #583. */
        const val ACCOUNTS_DB_NAME = "accounts.db"
    }
}

/**
 * Moves the legacy `caregiver-db` into the owner account (epic #581, #625).
 *
 * Renames the database and its `-wal`/`-shm` sidecars within the same directory
 * ([File.renameTo], atomic on one filesystem). If any rename fails, the ones
 * already done are rolled back in reverse order so no half-migrated state is
 * left behind. A persisted flag makes a second run a no-op. This class lives
 * here because the issue's `LegacyMigration.kt` path is UNOWNED by lane B.
 *
 * AI-key and onboarding-flag migration (#613/#616) are not on this branch, so
 * they are injected delegates with no-op defaults. They run before any file is
 * touched: a failing delegate aborts the move rather than leaving the data in
 * a new account with the old settings.
 *
 * @return true when this call performed the move, false when it was a no-op.
 */
class LegacyMigration(
    private val context: Context,
    private val migrateAiKey: suspend (String) -> Unit = {},
    private val migrateOnboarding: suspend (String) -> Unit = {},
    private val backupCopy: (File, File) -> Unit = { from, to -> from.copyTo(to, overwrite = true) },
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the move has completed (also after a no-op with no legacy file). */
    fun isCompleted(): Boolean = prefs.getBoolean(MIGRATION_COMPLETED, false)

    /**
     * Deletes the recovery backup one launch after a successful move.
     * The integrator calls this on every launch; [migrate] calls it too.
     * Returns true when it actually removed a backup.
     */
    fun cleanupBackupIfDue(): Boolean {
        if (!isCompleted() || !prefs.getBoolean(BACKUP_PENDING, false)) return false
        backupFiles().forEach { it.delete() }
        prefs.edit().putBoolean(BACKUP_PENDING, false).apply()
        return true
    }

    suspend fun migrate(ownerId: String): Boolean {
        cleanupBackupIfDue()
        if (isCompleted()) return false
        val safeId = PerAccountDatabaseProvider.sanitize(ownerId)

        val legacy = context.getDatabasePath(PerAccountDatabaseProvider.LEGACY_DATABASE_NAME)
        val target = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}$safeId")

        // A previous attempt may have been killed between renames. Recover to
        // the pre-migration state before doing anything else (#627).
        recoverIfInterrupted(legacy, target)

        if (!legacy.exists()) return false

        val sources = listOf(legacy) + listOf(WAL, SHM).map { File(legacy.path + it) }
            .filter { it.exists() }

        // Settings first: a failure here must not move the database.
        migrateAiKey(safeId)
        migrateOnboarding(safeId)

        // Safety net (#626): copy before any rename. A failed copy (e.g. no
        // space) aborts with the originals untouched — only status is logged.
        val backups = sources.map { it to File(it.path + BACKUP_SUFFIX) }
        try {
            backups.forEach { (from, to) -> backupCopy(from, to) }
        } catch (error: java.io.IOException) {
            Log.i(TAG, "legacy migration aborted: backup copy failed")
            throw IllegalStateException("legacy backup failed", error)
        }
        prefs.edit().putBoolean(BACKUP_PENDING, true).apply()

        val renames = sources.map { from ->
            val suffix = from.name.removePrefix(PerAccountDatabaseProvider.LEGACY_DATABASE_NAME)
            from to File(target.path + suffix)
        }
        prefs.edit().putBoolean(MIGRATION_IN_PROGRESS, true).apply()
        val done = mutableListOf<Pair<File, File>>()
        try {
            for ((from, to) in renames) {
                if (!from.renameTo(to)) throw IllegalStateException("legacy rename failed")
                done += from to to
            }
        } catch (failure: IllegalStateException) {
            // Reverse the renames, then restore bytes from the backup so the
            // pre-migration state is byte-for-byte. Never log note content.
            done.reversed().forEach { (from, to) -> to.renameTo(from) }
            sources.forEach { original ->
                val backup = File(original.path + BACKUP_SUFFIX)
                if (backup.exists()) backup.copyTo(original, overwrite = true)
            }
            backupFiles().forEach { it.delete() }
            prefs.edit()
                .putBoolean(BACKUP_PENDING, false)
                .putBoolean(MIGRATION_IN_PROGRESS, false)
                .apply()
            Log.i(TAG, "legacy migration failed: original restored")
            throw failure
        }

        prefs.edit()
            .putBoolean(MIGRATION_COMPLETED, true)
            .putBoolean(MIGRATION_IN_PROGRESS, false)
            .apply()
        Log.i(TAG, "legacy migration succeeded")
        return true
    }

    /**
     * Called at the start of [migrate] when a previous attempt was killed
     * mid-rename. Deletes any partially moved files, restores the originals
     * from the backups, and clears the in-progress marker so the move can be
     * retried from a clean pre-migration state (#627).
     */
    private fun recoverIfInterrupted(legacy: File, target: File) {
        if (!prefs.getBoolean(MIGRATION_IN_PROGRESS, false) || isCompleted()) return
        listOf("", WAL, SHM).forEach { suffix ->
            val moved = File(target.path + suffix)
            if (moved.isDirectory) moved.deleteRecursively() else if (moved.exists()) moved.delete()
            val backup = File(legacy.path + suffix + BACKUP_SUFFIX)
            val original = File(legacy.path + suffix)
            if (backup.exists()) {
                backup.copyTo(original, overwrite = true)
                backup.delete()
            }
        }
        prefs.edit()
            .putBoolean(MIGRATION_IN_PROGRESS, false)
            .putBoolean(BACKUP_PENDING, false)
            .apply()
        Log.i(TAG, "legacy migration recovered after interruption")
    }

    /** All recovery backup files, whether or not they currently exist. */
    private fun backupFiles(): List<File> =
        listOf(PerAccountDatabaseProvider.LEGACY_DATABASE_NAME, WAL, SHM).map { suffix ->
            val base = context.getDatabasePath(PerAccountDatabaseProvider.LEGACY_DATABASE_NAME)
            File(base.path + suffix + BACKUP_SUFFIX)
        }

    /** Test-only: clears the persisted flags and removes any migrated files. */
    internal fun resetForTest(ownerId: String) {
        prefs.edit().remove(MIGRATION_COMPLETED).remove(BACKUP_PENDING).apply()
        backupFiles().forEach { it.delete() }
        context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}$ownerId").delete()
        context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}$ownerId$WAL").delete()
        context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}$ownerId$SHM").delete()
    }

    companion object {
        private const val TAG = "LegacyMigration"
        private const val PREFS_NAME = "legacy_migration"
        private const val MIGRATION_COMPLETED = "migration_completed"
        internal const val MIGRATION_IN_PROGRESS = "migration_in_progress"
        private const val BACKUP_PENDING = "backup_pending"
        const val BACKUP_SUFFIX = ".migration-backup"
        private const val WAL = "-wal"
        private const val SHM = "-shm"
    }
}
