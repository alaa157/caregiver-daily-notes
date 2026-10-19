package com.caregiver.mobile.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Documented headers, decode validation, extra columns and encode shape. */
class CareNoteCsvTest {

    @Test
    fun headersAreStableAndInDocumentedOrder() {
        assertEquals(
            listOf(
                "date",
                "recipient_id",
                "recipient_name",
                "mood",
                "appetite",
                "sleep",
                "mobility",
                "medication_taken",
                "pain",
                "fall",
                "note_text",
            ),
            CareNoteCsv.HEADERS,
        )
    }

    @Test
    fun decodeRejectsMissingRequiredHeaders() {
        val result = CareNoteCsv.decode("date,recipient_id\n2026-10-01,r1")

        val failure = (result as CsvDecodeResult.Failure).failure
        assertEquals(CsvFailureReason.MISSING_HEADERS, failure.reason)
        assertTrue(failure.missingHeaders.contains("mood"))
        assertTrue(failure.missingHeaders.contains("note_text"))
    }

    @Test
    fun decodeAcceptsRecipientNameWithoutAnIdColumn() {
        val header = CareNoteCsv.HEADERS.filterNot { it == "recipient_id" }
        val data = listOf("2026-10-01", "Mona", "good", "good", "ok", "walks", "taken", "2", "false", "note")
        val text = CsvCodec.encode(listOf(header, data))

        val success = CareNoteCsv.decode(text) as CsvDecodeResult.Success

        assertEquals(1, success.rows.size)
        assertEquals("Mona", success.rows.single().values["recipient_name"])
    }

    @Test
    fun decodeRejectsDuplicateHeaders() {
        val result = CareNoteCsv.decode("date,date,recipient_id\n2026-10-01,2026-10-01,r1")

        val failure = (result as CsvDecodeResult.Failure).failure
        assertEquals(CsvFailureReason.DUPLICATE_HEADERS, failure.reason)
        assertEquals("date", failure.detail)
    }

    @Test
    fun decodeReportsExtraColumnsAsIgnored() {
        val text = CsvCodec.encode(
            listOf(CareNoteCsv.HEADERS + listOf("notes", "source"), csvRow()),
        )

        val success = CareNoteCsv.decode(text) as CsvDecodeResult.Success

        assertEquals(listOf("notes", "source"), success.extraHeaders)
    }

    @Test
    fun decodeSkipsBlankRows() {
        val text = csvText(csvRow()) + "\r\n,\r\n"

        val success = CareNoteCsv.decode(text) as CsvDecodeResult.Success

        assertEquals(1, success.rows.size)
    }

    @Test
    fun decodeRecordsFieldCountMismatchForThePlanner() {
        val header = CareNoteCsv.HEADERS
        val short = listOf("2026-10-01", "r1", "Mona")
        val text = CsvCodec.encode(listOf(header, short))

        val row = (CareNoteCsv.decode(text) as CsvDecodeResult.Success).rows.single()

        assertEquals(3, row.fieldCount)
        assertEquals(header.size, row.expectedFieldCount)
    }

    @Test
    fun decodeRejectsEmptyInput() {
        val result = CareNoteCsv.decode("")

        assertEquals(
            CsvFailureReason.EMPTY_FILE,
            (result as CsvDecodeResult.Failure).failure.reason,
        )
    }

    @Test
    fun decodeAcceptsHeaderOnlyFileAsZeroRows() {
        val text = CsvCodec.encode(listOf(CareNoteCsv.HEADERS))

        val success = CareNoteCsv.decode(text) as CsvDecodeResult.Success

        assertEquals(0, success.rows.size)
    }

    @Test
    fun booleanAcceptsDocumentedVariants() {
        listOf("true", "TRUE", "yes", "Yes", "y", "1").forEach {
            assertEquals("$it should be true", true, CareNoteCsv.booleanOf(it))
        }
        listOf("false", "FALSE", "no", "No", "n", "0").forEach {
            assertEquals("$it should be false", false, CareNoteCsv.booleanOf(it))
        }
        assertNull(CareNoteCsv.booleanOf("maybe"))
        assertNull(CareNoteCsv.booleanOf(""))
    }

    @Test
    fun encodeWritesBomAndRoundTripsArabicAndSpecialCharacters() {
        val record = CareNoteRecord(
            date = "2026-10-01",
            recipientId = "r1",
            recipientName = "منى",
            mood = "good",
            appetite = "good",
            sleep = "ok",
            mobility = "walks",
            medicationTaken = "taken",
            pain = 3,
            fall = true,
            text = "أكل جيدًا، \"تحسّن\" ملحوظ\nمع ملاحظة ثانية",
        )

        val content = CareNoteCsv.encode(listOf(record))
        assertTrue(content.startsWith(CsvCodec.BOM))

        val decoded = CareNoteCsv.decode(content) as CsvDecodeResult.Success
        val row = decoded.rows.single()
        assertEquals("2026-10-01", row.values["date"])
        assertEquals("منى", row.values["recipient_name"])
        assertEquals("3", row.values["pain"])
        assertEquals("true", row.values["fall"])
        assertEquals(record.text, row.values["note_text"])
    }

    @Test
    fun malformedCsvSurfacesTheParserLine() {
        val result = CareNoteCsv.decode("date,recipient_id\n2026-10-01,\"open")

        val failure = (result as CsvDecodeResult.Failure).failure
        assertEquals(CsvFailureReason.MALFORMED_CSV, failure.reason)
        assertEquals(2, failure.line)
    }
}
