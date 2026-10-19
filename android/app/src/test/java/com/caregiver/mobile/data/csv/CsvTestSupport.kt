package com.caregiver.mobile.data.csv

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import java.io.IOException

/**
 * In-memory [CsvNoteStore] for service/ViewModel tests. Records exactly what
 * a commit would write and lets a test force per-row results or a write
 * failure.
 */
class FakeCsvNoteStore(
    override val preservesDates: Boolean = true,
    var notes: List<NoteDto> = emptyList(),
    var recipients: List<RecipientDto> = emptyList(),
    var results: List<CsvInsertResult>? = null,
    var throwOnInsert: Boolean = false,
) : CsvNoteStore {
    val inserted = mutableListOf<PlannedNote>()

    override suspend fun snapshot(): CsvSnapshot = CsvSnapshot(notes = notes, recipients = recipients)

    override suspend fun insertAll(rows: List<PlannedNote>): List<CsvInsertResult> {
        if (throwOnInsert) throw IOException("store unavailable")
        inserted += rows
        return results ?: rows.map { CsvInsertResult.Inserted }
    }
}

/** A row in the documented column order, with import-friendly defaults. */
fun csvRow(
    date: String = "2026-10-01",
    recipientId: String = "r1",
    recipientName: String = "Mona",
    mood: String = "good",
    appetite: String = "good",
    sleep: String = "ok",
    mobility: String = "walks",
    medicationTaken: String = "taken",
    pain: String = "2",
    fall: String = "false",
    text: String = "note",
): List<String> = listOf(
    date,
    recipientId,
    recipientName,
    mood,
    appetite,
    sleep,
    mobility,
    medicationTaken,
    pain,
    fall,
    text,
)

/** Header + rows encoded the way the exporter writes them (no BOM). */
fun csvText(vararg rows: List<String>): String =
    CsvCodec.encode(listOf(CareNoteCsv.HEADERS) + rows)
