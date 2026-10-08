package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto

/** Shared note fixtures for ViewModel tests. */
fun testNote(
    id: String = "n1",
    recipientId: String = "r1",
    date: String = "2026-10-07",
    mood: String = "good",
    appetite: String = "good",
    sleep: String = "ok",
    mobility: String = "walks",
    medicationTaken: String = "taken",
    pain: Int = 2,
    fall: Boolean = false,
    text: String = "ate well اليوم",
) = NoteDto(
    id = id, recipientId = recipientId, date = date, mood = mood,
    appetite = appetite, sleep = sleep, mobility = mobility,
    medicationTaken = medicationTaken, pain = pain, fall = fall, text = text,
)

fun testNoteDetail(
    note: NoteDto = testNote(),
    addenda: List<AddendumDto> = emptyList(),
) = NoteDetailDto(
    id = note.id, recipientId = note.recipientId, date = note.date, mood = note.mood,
    appetite = note.appetite, sleep = note.sleep, mobility = note.mobility,
    medicationTaken = note.medicationTaken, pain = note.pain, fall = note.fall,
    text = note.text, addenda = addenda,
)

fun testAddendum(
    id: String = "a1",
    noteId: String = "n1",
    text: String = "تصحيح: لم يأخذ الدواء",
) = AddendumDto(id = id, noteId = noteId, createdAt = "2026-10-07T10:00:00", text = text)
