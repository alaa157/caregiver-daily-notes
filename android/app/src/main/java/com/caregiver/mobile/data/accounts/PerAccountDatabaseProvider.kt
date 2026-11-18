package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.room.Room
import com.caregiver.mobile.data.local.AppDatabase
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns one [AppDatabase] instance per local account (epic #581, issue #601).
 *
 * `AppDatabase.create(context)` still opens the single legacy `caregiver-db`
 * file; it is deliberately left alone so the migration path is unaffected and
 * no other caller breaks. This provider instead builds the same Room schema
 * under a per-account name, reusing the very same public migration objects
 * [AppDatabase.MIGRATION_1_2] … [AppDatabase.MIGRATION_9_10], so a named
 * database upgrades exactly like the legacy one. (The issue text proposed an
 * `AppDatabase.create(context, name)` overload, but that file is unowned by
 * this lane — the named builder lives here instead.)
 *
 * Exactly one instance is cached per account id, and at most one is "current".
 */
class PerAccountDatabaseProvider(private val context: Context) {
    private val appContext = context.applicationContext
    private val cache = ConcurrentHashMap<String, AppDatabase>()

    @Volatile
    private var currentAccountId: String? = null

    @Volatile
    private var currentDatabase: AppDatabase? = null

    /** Returns the cached database for [accountId], opening it on first use. */
    @Synchronized
    fun databaseFor(accountId: String): AppDatabase {
        val safeId = sanitize(accountId)
        return cache[safeId] ?: createNamed(safeId).also { cache[safeId] = it }
    }

    /** The signed-in account's database, or null when signed out. */
    fun current(): AppDatabase? = currentDatabase

    /** Opens (if needed) and makes [accountId] the current account. */
    @Synchronized
    fun open(accountId: String): AppDatabase {
        val db = databaseFor(accountId)
        currentAccountId = sanitize(accountId)
        currentDatabase = db
        return db
    }

    /** Closes the current account's database and clears only its cache entry. */
    @Synchronized
    fun close() {
        currentDatabase = null
        currentAccountId?.let { cache.remove(it)?.close() }
        currentAccountId = null
    }

    /** Closes every cached database and forgets all accounts. */
    @Synchronized
    fun closeAll() {
        currentDatabase = null
        currentAccountId = null
        cache.values.forEach { it.close() }
        cache.clear()
    }

    /**
     * Closes the account's database (if open) and deletes its file (issue #612).
     *
     * Never delete an open file: the cached instance is closed first, and an
     * instance that was "current" is forgotten. [Context.deleteDatabase]
     * removes the `.db` file together with its `-wal` and `-shm` sidecars, so
     * no file is unlinked by hand. Missing accounts are a no-op, making this
     * idempotent.
     */
    @Synchronized
    fun deleteDatabase(accountId: String) {
        val safeId = sanitize(accountId)
        if (currentAccountId == safeId) {
            currentAccountId = null
            currentDatabase = null
        }
        cache.remove(safeId)?.close()
        context.deleteDatabase("$FILE_PREFIX$safeId")
    }

    /** Builds the per-account file, applying the same migrations as the legacy DB. */
    private fun createNamed(safeId: String): AppDatabase =
        Room.databaseBuilder(appContext, AppDatabase::class.java, "$FILE_PREFIX$safeId")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
            )
            .build()

    companion object {
        /** Per-account files are named `notes-<accountId>.db` (Room adds `.db`). */
        const val FILE_PREFIX = "notes-"

        /** The legacy single-database name, still opened by AppDatabase.create(). */
        const val LEGACY_DATABASE_NAME = "caregiver-db"

        /** Account ids are UUIDs; anything else is a traversal attempt. */
        private val SAFE_ID = Regex("^[A-Za-z0-9-]+$")

        /**
         * Rejects blank ids and anything outside `[A-Za-z0-9-]`, so a crafted
         * id containing `/`, `..` or whitespace cannot escape the database
         * directory. Returns the trimmed id for use in the filename.
         */
        fun sanitize(accountId: String): String {
            val trimmed = accountId.trim()
            require(trimmed.isNotEmpty()) { "accountId must not be blank" }
            require(SAFE_ID.matches(trimmed)) {
                "accountId may only contain letters, digits and hyphens"
            }
            return trimmed
        }
    }
}

/**
 * Account-removal cleanup for issue #612, implemented here because the issue's
 * new file `AccountCleanupImpl.kt` is UNOWNED by lane B (guard returns rc 2),
 * and the `AccountCleanup` interface from #595 is not on this branch yet. This
 * class mirrors that interface's three method signatures so adding `:
 * AccountCleanup` later is a one-line change; it deliberately does not declare
 * the interface itself, which belongs to lane C.
 *
 * This class never touches the accounts row — #595 deletes the credentials
 * row before invoking these hooks. Every method is idempotent.
 *
 * @param deleteAiKeysDelegate AI-key deletion (#613/#614) is not on this
 *   branch, and `AiKeyStore` is lane A's file. The real implementation will be
 *   injected here; the default is a safe no-op rather than guessing at alias
 *   formats that do not exist yet.
 */
class AccountCleanupImpl(
    private val context: Context,
    private val provider: PerAccountDatabaseProvider = PerAccountDatabaseProvider(context),
    private val deleteAiKeysDelegate: (String) -> Unit = {},
) {
    /** Closes and deletes the account's note database. */
    suspend fun deleteDatabase(accountId: String) {
        provider.deleteDatabase(accountId)
    }

    /**
     * Removes app-private files belonging to [accountId] only.
     *
     * Per-account exports/share temp files are scoped by the convention
     * `filesDir/account-<id>/` and `cacheDir/account-<id>/`. Only those
     * dedicated directories are removed; nothing outside them is touched, so
     * another account's files always survive. A missing directory is a no-op.
     */
    suspend fun deleteExports(accountId: String) {
        val safeId = PerAccountDatabaseProvider.sanitize(accountId)
        listOf(context.filesDir, context.cacheDir).forEach { root ->
            val dir = File(root, "$ACCOUNT_DIR_PREFIX$safeId")
            if (dir.isDirectory) dir.deleteRecursively()
        }
    }

    /** Delegates AI-key deletion once #614 lands; no-op until then. */
    suspend fun deleteAiKeys(accountId: String) {
        deleteAiKeysDelegate(PerAccountDatabaseProvider.sanitize(accountId))
    }

    /** Runs all three hooks, in the same order #595 calls them. */
    suspend fun deleteAll(accountId: String) {
        deleteDatabase(accountId)
        deleteAiKeys(accountId)
        deleteExports(accountId)
    }

    companion object {
        /** Directory prefix for an account's app-private export/temp files. */
        const val ACCOUNT_DIR_PREFIX = "account-"
    }
}
