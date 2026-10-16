package com.caregiver.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room cache of recipients and notes (Task 8a) — the offline copy the
 * repositories read from and the network refresh writes to. Version 1 is
 * the cache only; the outbox table lands in Task 8b (bump the version and
 * export a migration there).
 *
 * The exported schema (`app/schemas/`) is committed so 8b/8c migrations
 * have a baseline to diff against.
 */
@Database(
    entities = [RecipientEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipientDao(): RecipientDao

    abstract fun noteDao(): NoteDao

    companion object {
        /**
         * File-backed database for production graphs. Demo graphs never
         * call this: demo data must not reach disk (Task 8a rule), so
         * `AppGraph` only creates the database when the real backend is in
         * use.
         */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "caregiver-db")
                .build()
    }
}
