package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.room.Room
import com.caregiver.mobile.data.local.AppDatabase
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
