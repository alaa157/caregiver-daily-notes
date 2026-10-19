package com.caregiver.mobile.data.csv

import java.util.Locale

/**
 * Every way a CSV transfer can end in a user-visible error. The UI maps the
 * reason (plus [CsvFailure.line] / [CsvFailure.missingHeaders]) to localized
 * copy; note text and recipient details never travel through this type.
 */
enum class CsvFailureReason {
    EMPTY_FILE,
    NO_ROWS,
    MALFORMED_CSV,
    MISSING_HEADERS,
    DUPLICATE_HEADERS,
    READ_FAILED,
    TOO_LARGE,
    WRITE_FAILED,
    SOURCE_FAILED,
    INSERT_FAILED,
    UNKNOWN,
}

data class CsvFailure(
    val reason: CsvFailureReason,
    val line: Int? = null,
    /** Technical context only (column names or a parser message), never note content. */
    val detail: String? = null,
    val missingHeaders: List<String> = emptyList(),
)

/** One decoded CSV data row, keyed by normalized header name. */
data class CareNoteCsvRow(
    val line: Int,
    val fieldCount: Int,
    val expectedFieldCount: Int,
    val values: Map<String, String>,
)

/** One note in the documented export shape. */
data class CareNoteRecord(
    val date: String,
    val recipientId: String,
    val recipientName: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
)

sealed interface CsvDecodeResult {
    data class Success(val rows: List<CareNoteCsvRow>, val extraHeaders: List<String>) : CsvDecodeResult
    data class Failure(val failure: CsvFailure) : CsvDecodeResult
}

/**
 * The documented CSV shape for daily care notes.
 *
 * Stable headers (export order):
 * `date,recipient_id,recipient_name,mood,appetite,sleep,mobility,medication_taken,pain,fall,note_text`
 *
 * Values:
 * - `date` is an ISO local date (`YYYY-MM-DD`).
 * - `recipient_id` is the stable backend/local recipient id; `recipient_name`
 *   is informational and used as a fallback match only when the id column is
 *   empty (see the import planner).
 * - `mood,appetite,sleep,mobility,medication_taken` are the canonical wire
 *   values used by the editor; they may be empty when the original note left
 *   the group unselected.
 * - `pain` is an integer 0..10, `fall` is `true`/`false` (import also accepts
 *   `yes/no`, `y/n`, `1/0`, case-insensitively).
 * - `note_text` is the free text note; commas, quotes and newlines are
 *   escaped by the writer.
 *
 * Exported files are UTF-8 with a BOM so spreadsheet software renders Arabic
 * correctly; the parser strips that BOM on import.
 */
object CareNoteCsv {
    const val COL_DATE = "date"
    const val COL_RECIPIENT_ID = "recipient_id"
    const val COL_RECIPIENT_NAME = "recipient_name"
    const val COL_MOOD = "mood"
    const val COL_APPETITE = "appetite"
    const val COL_SLEEP = "sleep"
    const val COL_MOBILITY = "mobility"
    const val COL_MEDICATION_TAKEN = "medication_taken"
    const val COL_PAIN = "pain"
    const val COL_FALL = "fall"
    const val COL_NOTE_TEXT = "note_text"

    val HEADERS = listOf(
        COL_DATE,
        COL_RECIPIENT_ID,
        COL_RECIPIENT_NAME,
        COL_MOOD,
        COL_APPETITE,
        COL_SLEEP,
        COL_MOBILITY,
        COL_MEDICATION_TAKEN,
        COL_PAIN,
        COL_FALL,
        COL_NOTE_TEXT,
    )

    /** Headers that must be present; recipient info may come from either recipient column. */
    val REQUIRED_HEADERS = listOf(
        COL_DATE,
        COL_MOOD,
        COL_APPETITE,
        COL_SLEEP,
        COL_MOBILITY,
        COL_MEDICATION_TAKEN,
        COL_PAIN,
        COL_FALL,
        COL_NOTE_TEXT,
    )

    val TRUE_VALUES = setOf("true", "yes", "1", "y")
    val FALSE_VALUES = setOf("false", "no", "0", "n")

    fun booleanOf(value: String): Boolean? {
        val normalized = value.trim().lowercase(Locale.ROOT)
        return when (normalized) {
            in TRUE_VALUES -> true
            in FALSE_VALUES -> false
            else -> null
        }
    }

    fun decode(text: String): CsvDecodeResult {
        val table = try {
            CsvCodec.parse(text)
        } catch (e: CsvFormatException) {
            return CsvDecodeResult.Failure(
                CsvFailure(CsvFailureReason.MALFORMED_CSV, line = e.line, detail = e.message),
            )
        }

        if (table.header.isEmpty() || table.header.all { it.isBlank() }) {
            return CsvDecodeResult.Failure(CsvFailure(CsvFailureReason.EMPTY_FILE))
        }

        val normalized = table.header.map { it.trim().lowercase(Locale.ROOT) }
        val duplicates = normalized
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        if (duplicates.isNotEmpty()) {
            return CsvDecodeResult.Failure(
                CsvFailure(
                    CsvFailureReason.DUPLICATE_HEADERS,
                    detail = duplicates.joinToString(", "),
                ),
            )
        }

        val missing = buildList {
            REQUIRED_HEADERS.filterTo(this) { it !in normalized }
            if (COL_RECIPIENT_ID !in normalized && COL_RECIPIENT_NAME !in normalized) {
                add("$COL_RECIPIENT_ID/$COL_RECIPIENT_NAME")
            }
        }
        if (missing.isNotEmpty()) {
            return CsvDecodeResult.Failure(
                CsvFailure(CsvFailureReason.MISSING_HEADERS, missingHeaders = missing),
            )
        }

        val extras = normalized.filter { it.isNotBlank() && it !in HEADERS }.distinct().sorted()

        val rows = table.records
            .filterNot { record -> record.fields.all { it.isBlank() } }
            .map { record ->
                val values = normalized.mapIndexedNotNull { index, header ->
                    if (header.isBlank()) null else header to record.fields.getOrElse(index) { "" }
                }.toMap()
                CareNoteCsvRow(
                    line = record.line,
                    fieldCount = record.fields.size,
                    expectedFieldCount = table.header.size,
                    values = values,
                )
            }

        return CsvDecodeResult.Success(rows = rows, extraHeaders = extras)
    }

    fun encode(records: List<CareNoteRecord>, includeBom: Boolean = true): String {
        val rows = buildList {
            add(HEADERS)
            records.forEach { record ->
                add(
                    listOf(
                        record.date,
                        record.recipientId,
                        record.recipientName,
                        record.mood,
                        record.appetite,
                        record.sleep,
                        record.mobility,
                        record.medicationTaken,
                        record.pain.toString(),
                        if (record.fall) "true" else "false",
                        record.text,
                    ),
                )
            }
        }
        val body = CsvCodec.encode(rows)
        return if (includeBom) CsvCodec.BOM + body else body
    }
}
