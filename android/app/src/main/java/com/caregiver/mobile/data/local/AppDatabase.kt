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
 * adds the outbox, 3.json the plans, 4.json the addenda and 5.json the
 * standalone medication tracker.
 *
 * File-backed database for production graphs. Demo graphs never call this:
 * demo data must not reach disk (Task 8a rule), so `AppGraph` only creates
 * the database when the real backend is in use.
 */
@Database(
    entities = [
        RecipientEntity::class,
        NoteEntity::class,
        OutboxEntity::class,
        PlanEntity::class,
        AddendumEntity::class,
        MedicationEntity::class,
        MedicationIntakeEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipientDao(): RecipientDao

    abstract fun noteDao(): NoteDao

    abstract fun outboxDao(): OutboxDao

    abstract fun planDao(): PlanDao

    abstract fun addendumDao(): AddendumDao

    abstract fun medicationDao(): MedicationDao

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
         * Adds the plans table: one plan per id, its version chain as JSON,
         * and the last-write timestamp.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plans` (" +
                        "`id` TEXT NOT NULL, " +
                        "`recipientId` TEXT NOT NULL, " +
                        "`versionsJson` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `addenda` (" +
                        "`id` TEXT NOT NULL, " +
                        "`noteId` TEXT NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * Medication tracker tables (5.json): seed data behind the home
         * card. Column order and names must stay identical to the
         * `CREATE TABLE` Room generates for [MedicationEntity] and
         * [MedicationIntakeEntity] so post-migration validation passes on
         * devices upgrading from v0.3.x installs.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medications` (" +
                        "`id` TEXT NOT NULL, " +
                        "`recipientId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`dose` TEXT, " +
                        "`schedule` TEXT, " +
                        "`active` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medications_recipientId` ON `medications` (`recipientId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medication_intakes` (" +
                        "`id` TEXT NOT NULL, " +
                        "`medicationId` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`times` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * Doses a day (6.json): a twice-daily medication needs one mark per
         * dose, so the medication carries how many doses a day it is taken
         * and each dose of a day gets its own intake row.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The new column is last, so a plain ALTER matches the
                // table Room now expects.
                db.execSQL(
                    "ALTER TABLE `medications` ADD COLUMN `dosesPerDay` INTEGER NOT NULL DEFAULT 1",
                )
                // The intake key carries the dose, so a day holds one row per
                // dose instead of a single count. The old rows are the first
                // dose of their day.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medication_intakes_new` (" +
                        "`id` TEXT NOT NULL, " +
                        "`medicationId` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`times` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `medication_intakes_new` " +
                        "(`id`, `medicationId`, `date`, `times`) " +
                        "SELECT `medicationId` || ':0', `medicationId`, `date`, `times` " +
                        "FROM `medication_intakes`",
                )
                db.execSQL("DROP TABLE `medication_intakes`")
                db.execSQL("ALTER TABLE `medication_intakes_new` RENAME TO `medication_intakes`")
            }
        }

        /**
         * Recipient archive state (7.json): nullable timestamp, null for every
         * existing row. Plain ALTER keeps the column last, matching Room.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recipients` ADD COLUMN `archivedAt` INTEGER")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "caregiver-db")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .build()
    }
}
