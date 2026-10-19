package com.caregiver.mobile.data.csv

import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.testNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Export mapping: whole history, stable ids, no fabricated names, safe pending merge. */
class CsvExportTest {

    private val recipients = listOf(
        RecipientDto(id = "r1", name = "منى", active = true),
        RecipientDto(id = "r2", name = "Omar", active = true),
    )

    @Test
    fun mapsEveryNoteFieldAndJoinsTheRecipientName() {
        val note = testNote(
            id = "n1",
            recipientId = "r1",
            date = "2026-10-07",
            mood = "low",
            appetite = "fair",
            sleep = "poor",
            mobility = "assisted",
            medicationTaken = "missed",
            pain = 6,
            fall = true,
            text = "ذكر السقوط، نحتاج متابعة",
        )

        val record = CsvExport.buildRecords(listOf(note), recipients).single()

        assertEquals("2026-10-07", record.date)
        assertEquals("r1", record.recipientId)
        assertEquals("منى", record.recipientName)
        assertEquals("low", record.mood)
        assertEquals("fair", record.appetite)
        assertEquals("poor", record.sleep)
        assertEquals("assisted", record.mobility)
        assertEquals("missed", record.medicationTaken)
        assertEquals(6, record.pain)
        assertTrue(record.fall)
        assertEquals(note.text, record.text)
    }

    @Test
    fun unknownRecipientKeepsTheIdAndExportsNoFabricatedName() {
        val record = CsvExport.buildRecords(
            listOf(testNote(id = "n1", recipientId = "gone")),
            recipients,
        ).single()

        assertEquals("gone", record.recipientId)
        assertEquals("", record.recipientName)
    }

    @Test
    fun recordsAreSortedNewestFirst() {
        val records = CsvExport.buildRecords(
            listOf(
                testNote(id = "n1", date = "2026-10-01"),
                testNote(id = "n2", date = "2026-10-07"),
                testNote(id = "n3", date = "2026-10-05"),
            ),
            recipients,
        )

        assertEquals(listOf("2026-10-07", "2026-10-05", "2026-10-01"), records.map { it.date })
    }

    @Test
    fun pendingNoteIdenticalToAConfirmedNoteIsExportedOnce() {
        val confirmed = testNote(id = "n1")
        val pendingCopy = confirmed.copy(id = "local-1", pending = true)

        val records = CsvExport.buildRecords(listOf(confirmed, pendingCopy), recipients)

        assertEquals(1, records.size)
    }

    @Test
    fun pendingNoteWithDifferentContentIsKept() {
        val confirmed = testNote(id = "n1", text = "morning")
        val pending = testNote(id = "local-1", text = "evening").copy(pending = true)

        val records = CsvExport.buildRecords(listOf(confirmed, pending), recipients)

        assertEquals(setOf("morning", "evening"), records.map { it.text }.toSet())
    }

    @Test
    fun exactDuplicatePendingRowsAreExportedOnce() {
        val pending = testNote(id = "local-1").copy(pending = true)

        val records = CsvExport.buildRecords(
            listOf(pending, pending.copy(id = "local-2")),
            recipients,
        )

        assertEquals(1, records.size)
    }

    @Test
    fun noNotesExportsNothing() {
        assertEquals(0, CsvExport.buildRecords(emptyList(), recipients).size)
    }
}
