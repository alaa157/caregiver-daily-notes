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
 * The archive column must arrive without touching existing data: a v6
 * install upgrades to v7 with every owned row intact and every recipient
 * unarchived.
 */
@RunWith(AndroidJUnit4::class)
class RecipientMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration6To7PreservesEveryTableAndLeavesRecipientsUnarchived() {
        helper.createDatabase(DB_NAME, 6).apply {
            execSQL(
                "INSERT INTO recipients (serverId, localId, name, active) VALUES (?, ?, ?, ?)",
                arrayOf("r1", null, "Aisha", 1),
            )
            execSQL(
                "INSERT INTO recipients (serverId, localId, name, active) VALUES (?, ?, ?, ?)",
                arrayOf("r2", null, "Karim", 0),
            )
            execSQL(
                "INSERT INTO notes (serverId, localId, recipientId, date, mood, appetite, " +
                    "sleep, mobility, medicationTaken, pain, fall, text) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "n1", null, "r1", "2026-10-24", "good", "good", "good",
                    "walks", "taken", 2, 0, "Stable day",
                ),
            )
            execSQL(
                "INSERT INTO addenda (id, noteId, createdAt, text) VALUES (?, ?, ?, ?)",
                arrayOf("a1", "n1", "2026-10-24T18:00:00", "Correction"),
            )
            execSQL(
                "INSERT INTO plans (id, recipientId, versionsJson, updatedAt) VALUES (?, ?, ?, ?)",
                arrayOf("p1", "r1", "[]", 1L),
            )
            execSQL(
                "INSERT INTO medications (id, recipientId, name, dose, schedule, active, " +
                    "createdAt, dosesPerDay) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf("m1", "r1", "Med", null, null, 1, 1L, 1),
            )
            execSQL(
                "INSERT INTO medication_intakes (id, medicationId, date, times) VALUES (?, ?, ?, ?)",
                arrayOf("m1:2026-10-24:0", "m1", "2026-10-24", 1),
            )
            execSQL(
                "INSERT INTO outbox (localId, recipientId, mood, appetite, sleep, mobility, " +
                    "medicationTaken, pain, fall, text, date, createdAt, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "o1", "r1", "good", "good", "good", "walks", "taken",
                    1, 0, "Pending", "2026-10-24", 1L, "pending",
                ),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            7,
            true,
            AppDatabase.MIGRATION_6_7,
        )

        assertEquals(2, migrated.count("recipients"))
        assertEquals(1, migrated.count("notes"))
        assertEquals(1, migrated.count("addenda"))
        assertEquals(1, migrated.count("plans"))
        assertEquals(1, migrated.count("medications"))
        assertEquals(1, migrated.count("medication_intakes"))
        assertEquals(1, migrated.count("outbox"))

        migrated.query(
            "SELECT serverId, name, active, archivedAt FROM recipients ORDER BY rowId",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("r1", cursor.getString(0))
            assertEquals("Aisha", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertTrue(cursor.isNull(3))

            assertTrue(cursor.moveToNext())
            assertEquals("r2", cursor.getString(0))
            assertEquals("Karim", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
        }
        migrated.close()
    }

    @Test
    fun migration7To8BackfillsRecordedFlagsOnNotesAndOutbox() {
        helper.createDatabase(DB_NAME, 7).apply {
            execSQL(
                "INSERT INTO notes (serverId, localId, recipientId, date, mood, appetite, " +
                    "sleep, mobility, medicationTaken, pain, fall, text) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "n1", null, "r1", "2026-10-24", "good", "good", "good",
                    "walks", "taken", 2, 0, "Stable day",
                ),
            )
            execSQL(
                "INSERT INTO outbox (localId, recipientId, mood, appetite, sleep, mobility, " +
                    "medicationTaken, pain, fall, text, date, createdAt, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "o1", "r1", "good", "good", "good", "walks", "taken",
                    1, 0, "Pending", "2026-10-24", 1L, "pending",
                ),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            8,
            true,
            AppDatabase.MIGRATION_7_8,
        )

        assertEquals(1, migrated.count("notes"))
        assertEquals(1, migrated.count("outbox"))

        migrated.query(
            "SELECT serverId, recipientId, date, mood, appetite, sleep, mobility, " +
                "medicationTaken, pain, fall, text, painRecorded, fallRecorded FROM notes",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("n1", cursor.getString(0))
            assertEquals("r1", cursor.getString(1))
            assertEquals("2026-10-24", cursor.getString(2))
            assertEquals("good", cursor.getString(3))
            assertEquals("good", cursor.getString(4))
            assertEquals("good", cursor.getString(5))
            assertEquals("walks", cursor.getString(6))
            assertEquals("taken", cursor.getString(7))
            assertEquals(2, cursor.getInt(8))
            assertEquals(0, cursor.getInt(9))
            assertEquals("Stable day", cursor.getString(10))
            assertEquals(1, cursor.getInt(11))
            assertEquals(1, cursor.getInt(12))
        }
        migrated.query(
            "SELECT localId, recipientId, mood, appetite, sleep, mobility, " +
                "medicationTaken, pain, fall, text, date, createdAt, status, " +
                "painRecorded, fallRecorded FROM outbox",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("o1", cursor.getString(0))
            assertEquals("r1", cursor.getString(1))
            assertEquals("good", cursor.getString(2))
            assertEquals("good", cursor.getString(3))
            assertEquals("good", cursor.getString(4))
            assertEquals("walks", cursor.getString(5))
            assertEquals("taken", cursor.getString(6))
            assertEquals(1, cursor.getInt(7))
            assertEquals(0, cursor.getInt(8))
            assertEquals("Pending", cursor.getString(9))
            assertEquals("2026-10-24", cursor.getString(10))
            assertEquals(1L, cursor.getLong(11))
            assertEquals("pending", cursor.getString(12))
            assertEquals(1, cursor.getInt(13))
            assertEquals(1, cursor.getInt(14))
        }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val DB_NAME = "recipient-migration-test"
    }
}
