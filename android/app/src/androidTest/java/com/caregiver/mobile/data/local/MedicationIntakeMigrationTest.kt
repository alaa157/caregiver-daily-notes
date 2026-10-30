package com.caregiver.mobile.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Version 5 tracked one taken-mark per medication per day, keyed
 * `<medicationId>:<date>`. Version 6 keys one row per dose,
 * `<medicationId>:<date>:<doseIndex>`, so every migrated day must keep its
 * own row: a migration that dropped the date would collapse all of a
 * medication's history onto a single primary key.
 */
@RunWith(AndroidJUnit4::class)
class MedicationIntakeMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration5To6KeepsEveryIntakeDateAsTheFirstDose() {
        helper.createDatabase(DB_NAME, 5).apply {
            execSQL(
                "INSERT INTO medications (id, recipientId, name, dose, schedule, active, createdAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("med-1", "r1", "Metformin", null, null, 1, 1L),
            )
            execSQL(
                "INSERT INTO medications (id, recipientId, name, dose, schedule, active, createdAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("med-2", "r1", "Aspirin", null, null, 1, 2L),
            )
            execSQL(
                "INSERT INTO medication_intakes (id, medicationId, date, times) VALUES (?, ?, ?, ?)",
                arrayOf("med-1:2026-01-01", "med-1", "2026-01-01", 1),
            )
            execSQL(
                "INSERT INTO medication_intakes (id, medicationId, date, times) VALUES (?, ?, ?, ?)",
                arrayOf("med-1:2026-01-02", "med-1", "2026-01-02", 1),
            )
            execSQL(
                "INSERT INTO medication_intakes (id, medicationId, date, times) VALUES (?, ?, ?, ?)",
                arrayOf("med-2:2026-01-02", "med-2", "2026-01-02", 1),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            6,
            true,
            AppDatabase.MIGRATION_5_6,
        )

        assertEquals(3, migrated.count("medication_intakes"))
        migrated.query(
            "SELECT id, medicationId, date, times FROM medication_intakes ORDER BY id",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("med-1:2026-01-01:0", cursor.getString(0))
            assertEquals("med-1", cursor.getString(1))
            assertEquals("2026-01-01", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))

            assertTrue(cursor.moveToNext())
            assertEquals("med-1:2026-01-02:0", cursor.getString(0))
            assertEquals("med-1", cursor.getString(1))
            assertEquals("2026-01-02", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))

            assertTrue(cursor.moveToNext())
            assertEquals("med-2:2026-01-02:0", cursor.getString(0))
            assertEquals("med-2", cursor.getString(1))
            assertEquals("2026-01-02", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
        }

        // Every medication gains the new column with the single-dose default.
        migrated.query("SELECT id, dosesPerDay FROM medications ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("med-1", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertTrue(cursor.moveToNext())
            assertEquals("med-2", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val DB_NAME = "medication-intake-migration-test"
    }
}
