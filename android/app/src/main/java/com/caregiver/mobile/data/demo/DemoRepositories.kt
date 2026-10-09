package com.caregiver.mobile.data.demo

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** UI-friendly note with its appended corrections (original never edited). */
data class DemoNoteDetail(
    val note: NoteEntity,
    val addenda: List<AddendumEntity>,
)

/** Today-status counts for Home. */
data class DemoTodayStatus(val done: Int, val total: Int)

/**
 * Offline recipient store: seeded Room data plus the selected-recipient
 * state shared by Home and the add-note/summary flows.
 */
class DemoRecipientRepository internal constructor(private val db: DemoDatabase) {
    /** Null until a recipient is picked; Home defaults to the first one. */
    val selectedId = MutableStateFlow<String?>(null)

    fun observeRecipients(): Flow<List<RecipientEntity>> = db.recipients().observeAll()

    suspend fun getRecipient(id: String): RecipientEntity? = db.recipients().getById(id)

    suspend fun addRecipient(name: String): String {
        val id = "r-" + UUID.randomUUID().toString().take(8)
        db.recipients().insert(RecipientEntity(id, name.trim(), System.currentTimeMillis()))
        selectedId.value = id
        return id
    }

    suspend fun isEmpty(): Boolean = db.recipients().count() == 0
}

/**
 * Offline note store. Saves always succeed locally (even if summary
 * generation is unavailable); corrections append, originals never change.
 */
class DemoNoteRepository internal constructor(private val db: DemoDatabase) {

    fun observeNotes(
        recipientId: String? = null,
        from: String? = null,
        to: String? = null,
    ): Flow<List<NoteEntity>> {
        val base = if (recipientId == null) db.notes().observeAll()
        else db.notes().observeByRecipient(recipientId)
        return base.map { notes ->
            notes.filter { note ->
                (from == null || note.date >= from) && (to == null || note.date <= to)
            }
        }
    }

    fun observeNoteDetail(noteId: String): Flow<DemoNoteDetail?> =
        combine(db.notes().observeAll(), db.addenda().observeByNote(noteId)) { notes, addenda ->
            notes.firstOrNull { it.id == noteId }?.let { DemoNoteDetail(it, addenda) }
        }

    suspend fun getNoteDetail(noteId: String): DemoNoteDetail? {
        val note = db.notes().getById(noteId) ?: return null
        return DemoNoteDetail(note, db.addenda().getByNote(noteId))
    }

    suspend fun saveNote(
        recipientId: String,
        date: String,
        mood: String,
        appetite: String,
        sleep: String,
        mobility: String,
        medicationTaken: String,
        pain: Int,
        fall: Boolean,
        text: String,
    ): String {
        val id = "n-" + UUID.randomUUID().toString().take(8)
        db.notes().insert(
            NoteEntity(
                id = id, recipientId = recipientId, date = date,
                mood = mood, appetite = appetite, sleep = sleep, mobility = mobility,
                medicationTaken = medicationTaken, pain = pain.coerceIn(0, 10),
                fall = fall, text = text.trim(), createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun addAddendum(noteId: String, text: String) {
        db.addenda().insert(
            AddendumEntity(
                id = "a-" + UUID.randomUUID().toString().take(8),
                noteId = noteId, text = text.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** "Reset demo data": wipe everything and reseed the fixed sample set. */
    suspend fun resetToSeed(today: LocalDate = LocalDate.now()) {
        val now = System.currentTimeMillis()
        db.addenda().clear()
        db.notes().clear()
        db.recipients().clear()
        db.recipients().insertAll(DemoSeed.recipients(now))
        db.notes().insertAll(DemoSeed.notes(today, now))
        db.addenda().insertAll(DemoSeed.addenda(now))
    }
}
