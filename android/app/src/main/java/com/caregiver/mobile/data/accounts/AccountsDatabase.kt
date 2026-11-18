package com.caregiver.mobile.data.accounts

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The accounts database (`accounts.db`): one row per local caregiver
 * account, credentials only (epic #581).
 *
 * This file is deliberately separate from `caregiver-db` ([AppDatabase]):
 * each signed-in account gets its own note database, so the accounts
 * table must never live inside one of them — removing an account would
 * otherwise delete the very credentials needed to manage the others.
 *
 * No `fallbackToDestructiveMigration()`: a lost accounts table orphans
 * every per-account database, so a schema mismatch must fail loudly
 * rather than silently wipe credentials.
 */
@Database(
    entities = [AccountEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AccountsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    companion object {
        fun create(context: Context): AccountsDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AccountsDatabase::class.java,
                "accounts.db",
            ).build()
    }
}
