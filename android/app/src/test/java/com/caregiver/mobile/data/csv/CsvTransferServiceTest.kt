package com.caregiver.mobile.data.csv

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.testNote
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Service orchestration: export snapshot, preview failures, commit accounting. */
class CsvTransferServiceTest {

    private val today = "2026-10-19"
    private val clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC)

    private val sampleRecipients = listOf(
        RecipientDto(id = "r1", name = "Mona", active = true),
        RecipientDto(id = "r2", name = "Omar", active = true),
    )

    private fun store(
        notes: List<NoteDto> = emptyList(),
        recipients: List<RecipientDto> = sampleRecipients,
        preservesDates: Boolean = true,
    ) = FakeCsvNoteStore(preservesDates = preservesDates, notes = notes, recipients = recipients)

    @Test
    fun exportSerializesEveryNoteWithARecordCountAndBom() = runTest {
        val notes = listOf(
            testNote(id = "n1", date = "2026-10-07"),
            testNote(id = "n2", recipientId = "r2", date = "2026-10-06"),
        )
        val service = CsvTransferService(store(notes = notes), clock)

        val export = service.export()

        assertEquals(2, export.recordCount)
        assertTrue(export.content.startsWith(CsvCodec.BOM))
        val decoded = CareNoteCsv.decode(export.content) as CsvDecodeResult.Success
        assertEquals(2, decoded.rows.size)
        assertEquals(listOf("r1", "r2"), decoded.rows.map { it.values["recipient_id"] })
    }

    @Test
    fun exportWithoutNotesWritesTheHeaderOnlyAndReportsZero() = runTest {
        val service = CsvTransferService(store(), clock)

        val export = service.export()

        assertEquals(0, export.recordCount)
        assertEquals(CsvCodec.BOM + CsvCodec.encode(listOf(CareNoteCsv.HEADERS)), export.content)
    }

    @Test
    fun exportThenImportRoundTripsTheSameFieldsOnAnotherDevice() = runTest {
        val exportStore = store(
            notes = listOf(
                testNote(
                    id = "n1",
                    date = "2026-09-30",
                    pain = 8,
                    fall = true,
                    text = "أكل جيدًا، سقط مرة",
                ),
            ),
        )
        val exported = CsvTransferService(exportStore, clock).export()

        // A fresh device: no notes, no duplicates.
        val importStore = store()
        val prepared = CsvTransferService(importStore, clock).prepare(exported.content)

        val plan = (prepared as ImportPreparation.Ready).plan
        val note = plan.valid.single()
        assertEquals("2026-09-30", note.date)
        assertEquals("r1", note.recipientId)
        assertEquals(8, note.pain)
        assertTrue(note.fall)
        assertEquals("أكل جيدًا، سقط مرة", note.text)
    }

    @Test
    fun prepareReportsMalformedCsvWithItsLine() = runTest {
        val service = CsvTransferService(store(), clock)

        val failure = (service.prepare("date,recipient_id\n2026-10-01,\"open") as ImportPreparation.Failed)
            .failure

        assertEquals(CsvFailureReason.MALFORMED_CSV, failure.reason)
        assertEquals(2, failure.line)
    }

    @Test
    fun prepareReportsEmptyFile() = runTest {
        val failure = (CsvTransferService(store(), clock).prepare("") as ImportPreparation.Failed).failure

        assertEquals(CsvFailureReason.EMPTY_FILE, failure.reason)
    }

    @Test
    fun prepareReportsAHeaderOnlyFileAsNoRows() = runTest {
        val text = CsvCodec.encode(listOf(CareNoteCsv.HEADERS))

        val failure = (CsvTransferService(store(), clock).prepare(text) as ImportPreparation.Failed).failure

        assertEquals(CsvFailureReason.NO_ROWS, failure.reason)
    }

    @Test
    fun prepareReportsMissingHeaders() = runTest {
        val failure = (
            CsvTransferService(store(), clock).prepare("date,recipient_id\n2026-10-01,r1")
                as ImportPreparation.Failed
            ).failure

        assertEquals(CsvFailureReason.MISSING_HEADERS, failure.reason)
        assertTrue(failure.missingHeaders.isNotEmpty())
    }

    @Test
    fun prepareBuildsAPlanThatCountsExistingDuplicates() = runTest {
        val service = CsvTransferService(
            store(notes = listOf(testNote(id = "n1", recipientId = "r1", date = "2026-10-01"))),
            clock,
        )

        val plan = (service.prepare(csvText(csvRow(date = "2026-10-01"))) as ImportPreparation.Ready).plan

        assertEquals(0, plan.valid.size)
        assertEquals(1, plan.duplicates.size)
    }

    @Test
    fun commitWritesValidRowsAndCountsPerRowResults() = runTest {
        val fake = store()
        fake.results = listOf(
            CsvInsertResult.Inserted,
            CsvInsertResult.SkippedDuplicate,
            CsvInsertResult.Rejected(CsvFailure(CsvFailureReason.INSERT_FAILED)),
        )
        val service = CsvTransferService(fake, clock)
        val plan = (
            service.prepare(
                csvText(
                    csvRow(date = "2026-10-01", text = "a"),
                    csvRow(date = "2026-10-02", text = "b"),
                    csvRow(date = "2026-10-03", text = "c"),
                ),
            ) as ImportPreparation.Ready
            ).plan

        val summary = service.commit(plan)

        assertEquals(3, fake.inserted.size)
        assertEquals(1, summary.imported)
        assertEquals(1, summary.duplicates)
        assertEquals(1, summary.rejected)
        assertEquals(0, summary.skipped)
        assertEquals(3, summary.total)
        assertTrue(summary.failure != null)
    }

    @Test
    fun commitWithoutValidRowsWritesNothing() = runTest {
        val fake = store()
        val service = CsvTransferService(fake, clock)
        val plan = (
            service.prepare(csvText(csvRow(pain = "42"))) as ImportPreparation.Ready
            ).plan

        val summary = service.commit(plan)

        assertEquals(0, fake.inserted.size)
        assertEquals(0, summary.imported)
        assertEquals(1, summary.rejected)
        assertNull(summary.failure)
    }

    @Test
    fun commitReportsAWholeStoreFailureAsRejectedRows() = runTest {
        val fake = store()
        fake.throwOnInsert = true
        val service = CsvTransferService(fake, clock)
        val plan = (
            service.prepare(
                csvText(
                    csvRow(date = "2026-10-01", text = "a"),
                    csvRow(date = "2026-10-02", text = "b"),
                ),
            ) as ImportPreparation.Ready
            ).plan

        val summary = service.commit(plan)

        assertEquals(0, summary.imported)
        assertEquals(2, summary.rejected)
        assertEquals(CsvFailureReason.INSERT_FAILED, summary.failure?.reason)
    }

    @Test
    fun remotePolicyRejectsRowsThatAreNotDatedToday() = runTest {
        val service = CsvTransferService(store(preservesDates = false), clock)

        val plan = (
            service.prepare(
                csvText(
                    csvRow(date = today, text = "today"),
                    csvRow(date = "2026-10-18", text = "yesterday"),
                ),
            ) as ImportPreparation.Ready
            ).plan

        assertEquals(listOf(today), plan.valid.map { it.date })
        assertEquals(ImportIssue.DATE_NOT_SUPPORTED, plan.invalid.single().issue)
    }

    @Test
    fun exportFileNameIsDated() {
        val service = CsvTransferService(store(), clock)

        assertEquals("caregiver-notes-2026-10-19.csv", service.exportFileName())
    }
}
