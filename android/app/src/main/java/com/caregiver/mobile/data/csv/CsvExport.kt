package com.caregiver.mobile.data.csv

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto

/** A ready-to-write CSV document plus the number of data records in it. */
data class CsvExportDocument(val content: String, val recordCount: Int)

/**
 * Maps the active data source's notes into the documented CSV shape.
 *
 * The export always covers every note the store can see (the whole history),
 * never just a visible date range. Recipient names are joined from the
 * recipient list; a note whose recipient cannot be found keeps its
 * `recipient_id` and exports an empty `recipient_name` rather than a
 * fabricated one.
 *
 * Pending outbox notes (`NoteDto.pending`) are part of the user's data and
 * are included. A pending note is dropped only when an identical confirmed
 * note already exists — that happens when a drain wrote the server row but
 * did not delete the outbox row, and exporting both would duplicate it.
 */
object CsvExport {
    fun buildRecords(notes: List<NoteDto>, recipients: List<RecipientDto>): List<CareNoteRecord> {
        val names = recipients.associate { it.id to it.name }
        return dedupePending(notes)
            .sortedWith(compareByDescending<NoteDto> { it.date }.thenBy { it.id })
            .map { note ->
                CareNoteRecord(
                    date = note.date,
                    recipientId = note.recipientId,
                    recipientName = names[note.recipientId].orEmpty(),
                    mood = note.mood,
                    appetite = note.appetite,
                    sleep = note.sleep,
                    mobility = note.mobility,
                    medicationTaken = note.medicationTaken,
                    pain = note.pain,
                    fall = note.fall,
                    text = note.text,
                )
            }
    }

    /**
     * Removes pending notes that duplicate a confirmed row, and duplicate
     * pending rows with identical content. Content identity compares the full
     * note, not just the date, so two genuinely different same-day notes are
     * both kept.
     */
    fun dedupePending(notes: List<NoteDto>): List<NoteDto> {
        val confirmedKeys = notes.filterNot { it.pending }.mapTo(HashSet()) { identity(it) }
        val emitted = HashSet<String>()
        return notes.filter { note ->
            if (!note.pending) {
                true
            } else {
                val key = identity(note)
                key !in confirmedKeys && emitted.add(key)
            }
        }
    }

    private fun identity(note: NoteDto): String = listOf(
        note.recipientId,
        note.date,
        note.mood,
        note.appetite,
        note.sleep,
        note.mobility,
        note.medicationTaken,
        note.pain.toString(),
        note.fall.toString(),
        note.text,
    ).joinToString("\u0000")
}
