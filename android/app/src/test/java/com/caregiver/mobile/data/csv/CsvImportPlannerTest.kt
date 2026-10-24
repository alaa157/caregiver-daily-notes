package com.caregiver.mobile.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation and recipient-resolution rules: required fields, the date
 * policy, recipient matching by stable id first, duplicate skipping, and
 * preview counts that match what a commit would do.
 */
class CsvImportPlannerTest {

    private val today = "2026-10-19"
    private val recipients = listOf(
        RecipientRef(id = "r1", name = "Mona", active = true),
        RecipientRef(id = "r2", name = "Omar", active = true),
    )

    private fun rows(vararg dataRows: List<String>): List<CareNoteCsvRow> =
        (CareNoteCsv.decode(csvText(*dataRows)) as CsvDecodeResult.Success).rows

    private fun plan(
        dataRows: List<List<String>>,
        existing: Set<ExistingNoteKey> = emptySet(),
        policy: ImportPolicy = ImportPolicy(preservesDates = true, today = today),
        overrides: Map<String, String> = emptyMap(),
    ): ImportPlan = CsvImportPlanner.plan(
        rows = rows(*dataRows.toTypedArray()),
        recipients = recipients,
        existing = existing,
        policy = policy,
        overrides = overrides,
    )

    @Test
    fun validRowIsPlannedWithNormalizedOptionValues() {
        val plan = plan(
            listOf(
                csvRow(
                    date = "2026-09-30",
                    mood = " Good ",
                    appetite = "Fair",
                    sleep = "",
                    mobility = "Walks",
                    medicationTaken = "TAKEN",
                    pain = "7",
                    fall = "yes",
                    text = "أكل جيدًا",
                ),
            ),
        )

        val note = plan.valid.single()
        assertEquals("2026-09-30", note.date)
        assertEquals("r1", note.recipientId)
        assertEquals("good", note.mood)
        assertEquals("fair", note.appetite)
        assertEquals("", note.sleep)
        assertEquals("walks", note.mobility)
        assertEquals("taken", note.medicationTaken)
        assertEquals(7, note.pain)
        assertTrue(note.fall)
        assertEquals("أكل جيدًا", note.text)
    }

    @Test
    fun missingOrInvalidDateIsRejectedWithTheColumn() {
        val missing = plan(listOf(csvRow(date = ""))).invalid.single()
        assertEquals(ImportIssue.MISSING_DATE, missing.issue)
        assertEquals("date", missing.column)

        val invalid = plan(listOf(csvRow(date = "2026/10/01"))).invalid.single()
        assertEquals(ImportIssue.INVALID_DATE, invalid.issue)
        assertEquals("date", invalid.column)
    }

    @Test
    fun nonTodayRowsAreRejectedWhenTheStoreCannotPreserveDates() {
        val policy = ImportPolicy(preservesDates = false, today = today)
        val plan = plan(
            listOf(
                csvRow(date = today),
                csvRow(date = "2026-10-18", text = "old"),
            ),
            policy = policy,
        )

        assertEquals(listOf(today), plan.valid.map { it.date })
        assertEquals(ImportIssue.DATE_NOT_SUPPORTED, plan.invalid.single().issue)
        assertEquals("date", plan.invalid.single().column)
    }

    @Test
    fun painOutsideZeroToTenIsRejected() {
        listOf("11", "-1", "two", "").forEach { value ->
            val plan = plan(listOf(csvRow(pain = value)))
            assertEquals(
                "pain='$value' must be rejected",
                ImportIssue.INVALID_PAIN,
                plan.invalid.single().issue,
            )
            assertEquals("pain", plan.invalid.single().column)
        }
    }

    @Test
    fun fallMustParseAsABoolean() {
        val plan = plan(listOf(csvRow(fall = "maybe")))

        assertEquals(ImportIssue.INVALID_FALL, plan.invalid.single().issue)
        assertEquals("fall", plan.invalid.single().column)
    }

    @Test
    fun moodAndNoteTextAreRequired() {
        val missingMood = plan(listOf(csvRow(mood = " "))).invalid.single()
        assertEquals(ImportIssue.MISSING_MOOD, missingMood.issue)
        assertEquals("mood", missingMood.column)

        val missingText = plan(listOf(csvRow(text = "  "))).invalid.single()
        assertEquals(ImportIssue.EMPTY_TEXT, missingText.issue)
        assertEquals("note_text", missingText.column)
    }

    @Test
    fun optionValuesLongerThanTheBackendColumnAreRejected() {
        val plan = plan(listOf(csvRow(appetite = "a".repeat(33))))

        assertEquals(ImportIssue.VALUE_TOO_LONG, plan.invalid.single().issue)
        assertEquals("appetite", plan.invalid.single().column)
    }

    @Test
    fun wrongFieldCountIsRejectedBeforeAnyValueChecks() {
        val plan = plan(listOf(listOf("2026-10-01", "r1", "Mona")))

        assertEquals(ImportIssue.WRONG_FIELD_COUNT, plan.invalid.single().issue)
        assertEquals(0, plan.valid.size)
    }

    @Test
    fun rowWithoutAnyRecipientIdentifierIsRejected() {
        val plan = plan(listOf(csvRow(recipientId = "", recipientName = "")))

        assertEquals(ImportIssue.MISSING_RECIPIENT, plan.invalid.single().issue)
    }

    @Test
    fun unknownIdIsNotSilentlyAttachedByMatchingName() {
        val plan = plan(listOf(csvRow(recipientId = "gone", recipientName = "Mona")))

        assertEquals(0, plan.valid.size)
        assertEquals(1, plan.unmatched.size)
        val unmatched = plan.unmatched.single()
        assertEquals("gone", unmatched.identifier)
        assertTrue(unmatched.byId)
        assertEquals(1, unmatched.rowCount)
        assertEquals(ImportIssue.UNMATCHED_RECIPIENT, plan.skipped.single().issue)
    }

    @Test
    fun nameIsMatchedOnlyWhenTheIdColumnIsEmpty() {
        val plan = plan(listOf(csvRow(recipientId = "", recipientName = " mona ")))

        assertEquals("r1", plan.valid.single().recipientId)
        assertEquals(0, plan.unmatched.size)
    }

    @Test
    fun ambiguousNameIsUnmatchedUntilTheUserResolvesIt() {
        val ambiguous = recipients + RecipientRef(id = "r3", name = "Mona", active = false)
        val csvRows = rows(csvRow(recipientId = "", recipientName = "Mona"))
        val base = CsvImportPlanner.plan(
            rows = csvRows,
            recipients = ambiguous,
            existing = emptySet(),
            policy = ImportPolicy(preservesDates = true, today = today),
        )

        assertEquals(0, base.valid.size)
        assertEquals("Mona", base.unmatched.single().identifier)

        val resolved = base.withResolutions(
            mapOf(CsvImportPlanner.nameKey("Mona") to "r3"),
        )
        assertEquals("r3", resolved.valid.single().recipientId)
        assertEquals(0, resolved.unmatched.size)
    }

    @Test
    fun resolutionsAttachUnmatchedRowsToTheChosenRecipient() {
        val base = plan(
            listOf(
                csvRow(recipientId = "gone", text = "first"),
                csvRow(recipientId = "gone", text = "second", date = "2026-10-02"),
            ),
        )

        assertEquals(0, base.valid.size)
        assertEquals(2, base.unmatched.single().rowCount)
        assertEquals(2, base.skipped.size)

        val resolved = base.withResolutions(mapOf(CsvImportPlanner.idKey("gone") to "r2"))

        assertEquals(2, resolved.valid.size)
        assertTrue(resolved.valid.all { it.recipientId == "r2" })
        assertEquals(0, resolved.skipped.size)
        assertEquals(0, resolved.unmatched.size)
    }

    @Test
    fun resolutionToAnUnknownRecipientKeepsTheRowsSkipped() {
        val base = plan(listOf(csvRow(recipientId = "gone")))

        val resolved = base.withResolutions(mapOf(CsvImportPlanner.idKey("gone") to "missing"))

        assertEquals(0, resolved.valid.size)
        assertEquals(1, resolved.skipped.size)
    }

    @Test
    fun existingNoteForSameRecipientAndDayIsADuplicate() {
        val plan = plan(
            listOf(csvRow(date = "2026-10-01")),
            existing = setOf(ExistingNoteKey("r1", "2026-10-01")),
        )

        assertEquals(0, plan.valid.size)
        assertEquals(ImportIssue.DUPLICATE, plan.duplicates.single().issue)
    }

    @Test
    fun duplicateWithinTheFileKeepsTheFirstRowAndSkipsTheNext() {
        val plan = plan(
            listOf(
                csvRow(text = "first"),
                csvRow(text = "second"),
            ),
        )

        assertEquals(listOf("first"), plan.valid.map { it.text })
        assertEquals(1, plan.duplicates.size)
        assertEquals(plan.rows.last().line, plan.duplicates.single().line)
    }

    @Test
    fun countsAddUpAcrossValidDuplicateInvalidAndSkippedRows() {
        val plan = plan(
            listOf(
                csvRow(text = "valid"),
                csvRow(pain = "42", text = "invalid"),
                csvRow(date = "2026-10-05", text = "duplicate"),
                csvRow(recipientId = "gone", text = "unmatched"),
            ),
            existing = setOf(ExistingNoteKey("r1", "2026-10-05")),
        )

        assertEquals(4, plan.totalRows)
        assertEquals(1, plan.valid.size)
        assertEquals(1, plan.invalid.size)
        assertEquals(1, plan.duplicates.size)
        assertEquals(1, plan.skipped.size)
    }

    @Test
    fun archivedRecipientNeverMatchesByIdAndKeepsItsState() {
        val withArchived = recipients + RecipientRef(
            id = "r3",
            name = "Zaid",
            active = true,
            archived = true,
        )
        val csvRows = rows(csvRow(recipientId = "r3"))
        val base = CsvImportPlanner.plan(
            rows = csvRows,
            recipients = withArchived,
            existing = emptySet(),
            policy = ImportPolicy(preservesDates = true, today = today),
        )

        assertEquals(0, base.valid.size)
        assertEquals(CsvImportPlanner.idKey("r3"), base.unmatched.single().key)
        assertEquals(1, base.skipped.size)
        assertTrue(base.recipients.first { it.id == "r3" }.archived)

        val resolved = base.withResolutions(mapOf(CsvImportPlanner.idKey("r3") to "r3"))
        assertEquals(0, resolved.valid.size)
        assertEquals(1, resolved.skipped.size)
    }

    @Test
    fun archivedRecipientNeverMatchesByName() {
        val custom = listOf(
            RecipientRef(id = "r1", name = "Omar", active = true),
            RecipientRef(id = "r2", name = "Mona", active = true, archived = true),
        )
        val csvRows = rows(csvRow(recipientId = "", recipientName = "Mona"))
        val base = CsvImportPlanner.plan(
            rows = csvRows,
            recipients = custom,
            existing = emptySet(),
            policy = ImportPolicy(preservesDates = true, today = today),
        )

        assertEquals(0, base.valid.size)
        assertEquals("Mona", base.unmatched.single().identifier)
        assertFalse(base.unmatched.single().byId)
        assertEquals(1, base.skipped.size)

        val resolved = base.withResolutions(mapOf(CsvImportPlanner.nameKey("Mona") to "r2"))
        assertEquals(0, resolved.valid.size)
        assertEquals(1, resolved.skipped.size)
    }

    @Test
    fun anActiveRecipientStillMatchesWhenAnArchivedOneSharesTheName() {
        val custom = listOf(
            RecipientRef(id = "r1", name = "Mona", active = true),
            RecipientRef(id = "r2", name = "Mona", active = true, archived = true),
        )
        val csvRows = rows(csvRow(recipientId = "", recipientName = "Mona"))
        val base = CsvImportPlanner.plan(
            rows = csvRows,
            recipients = custom,
            existing = emptySet(),
            policy = ImportPolicy(preservesDates = true, today = today),
        )

        assertEquals("r1", base.valid.single().recipientId)
        assertEquals(0, base.unmatched.size)
    }

    @Test
    fun extraHeadersAreCarriedIntoThePlan() {
        val header = CareNoteCsv.HEADERS + listOf("source")
        val text = CsvCodec.encode(listOf(header, csvRow() + "app"))

        val success = CareNoteCsv.decode(text) as CsvDecodeResult.Success
        val plan = CsvImportPlanner.plan(
            rows = success.rows,
            recipients = recipients,
            existing = emptySet(),
            policy = ImportPolicy(preservesDates = true, today = today),
            extraHeaders = success.extraHeaders,
        )

        assertEquals(listOf("source"), plan.extraHeaders)
    }
}
