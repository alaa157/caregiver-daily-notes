package com.caregiver.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room cache of recipients and notes (Task 8a) plus the note outbox
 * (Task 8b) — the offline copy the repositories read from, the network
 * refresh writes to, and the queue for notes saved while offline.
 *
 * The exported schema (`app/schemas/`) is committed so migrations have a
 * baseline to diff against: 1.json is the Task 8a cache-only shape, 2.json
 * adds the outbox.
 */
@Database(
    entities = [RecipientEntity::class, NoteEntity::class, OutboxEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipientDao(): RecipientDao

    abstract fun noteDao(): NoteDao

    abstract fun outboxDao(): OutboxDao

    companion object {
        /**
         * Task 8b: adds the outbox table. Must stay identical to the
         * `CREATE TABLE` Room generates for [OutboxEntity] (see the
         * exported 2.json schema).
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox` (" +
                        "`localId` TEXT NOT NULL, " +
                        "`recipientId` TEXT NOT NULL, " +
                        "`mood` TEXT NOT NULL, " +
                        "`appetite` TEXT NOT NULL, " +
                        "`sleep` TEXT NOT NULL, " +
                        "`mobility` TEXT NOT NULL, " +
                        "`medicationTaken` TEXT NOT NULL, " +
                        "`pain` INTEGER NOT NULL, " +
                        "`fall` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "PRIMARY KEY(`localId`))",
                )
            }
        }

        /**
         * File-backed database for production graphs. Demo graphs never
         * call this: demo data must not reach disk (Task 8a rule), so
         * `AppGraph` only creates the database when the real backend is in
         * use.
         */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "caregiver-db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
