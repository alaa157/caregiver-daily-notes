package com.caregiver.mobile.data.demo

import java.time.LocalDate

/**
 * First-launch demo content (Step 6). Clearly fictional people, initials-only
 * avatars (no photos), ~10 notes across the last 14 days in English and
 * Arabic. Wire values match NoteOptions so editor chips round-trip.
 * Includes one recorded fall (red-flag demo) and one high-pain note.
 */
object DemoSeed {

    fun recipients(nowMs: Long): List<RecipientEntity> = listOf(
        RecipientEntity("r-layla", "Layla H.", nowMs),
        RecipientEntity("r-omar", "Omar K.", nowMs),
        RecipientEntity("r-salem", "Salem N.", nowMs),
    )

    fun notes(today: LocalDate, nowMs: Long): List<NoteEntity> {
        fun day(ago: Long): String = today.minusDays(ago).toString()
        return listOf(
            NoteEntity(
                id = "n1", recipientId = "r-layla", date = day(0),
                mood = "good", appetite = "good", sleep = "good",
                mobility = "walks", medicationTaken = "taken",
                pain = 1, fall = false,
                text = "Ate well today and walked to the garden in the morning.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n2", recipientId = "r-omar", date = day(1),
                mood = "fair", appetite = "reduced", sleep = "broken",
                mobility = "assisted", medicationTaken = "taken",
                pain = 8, fall = false,
                text = "High pain in the evening, needed help getting up.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n3", recipientId = "r-layla", date = day(2),
                mood = "good", appetite = "good", sleep = "good",
                mobility = "walks", medicationTaken = "taken",
                pain = 2, fall = false,
                text = "نامت جيدًا الليلة الماضية وكانت معنوياتها مرتفعة.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n4", recipientId = "r-salem", date = day(3),
                mood = "fair", appetite = "reduced", sleep = "broken",
                mobility = "assisted", medicationTaken = "unsure",
                pain = 4, fall = false,
                text = "شهية منخفضة على الغداء، تناول نصف الوجبة فقط.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n5", recipientId = "r-omar", date = day(4),
                mood = "good", appetite = "good", sleep = "good",
                mobility = "walks", medicationTaken = "taken",
                pain = 3, fall = false,
                text = "أخذ الدواء في موعده وتحسّن الألم بعد الراحة.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n6", recipientId = "r-layla", date = day(5),
                mood = "fair", appetite = "reduced", sleep = "broken",
                mobility = "assisted", medicationTaken = "taken",
                pain = 5, fall = false,
                text = "Restless night, needed company until late.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n7", recipientId = "r-salem", date = day(6),
                mood = "good", appetite = "good", sleep = "good",
                mobility = "walks", medicationTaken = "taken",
                pain = 0, fall = false,
                text = "A calm day, enjoyed tea with the family.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n8", recipientId = "r-layla", date = day(9),
                mood = "bad", appetite = "poor", sleep = "poor",
                mobility = "bed", medicationTaken = "missed",
                pain = 6, fall = true,
                text = "Slipped near the bathroom in the morning, no bleeding. Rested in bed afterwards.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n9", recipientId = "r-omar", date = day(11),
                mood = "fair", appetite = "reduced", sleep = "good",
                mobility = "assisted", medicationTaken = "taken",
                pain = 4, fall = false,
                text = "Steady day, short walk with help in the afternoon.",
                createdAt = nowMs,
            ),
            NoteEntity(
                id = "n10", recipientId = "r-salem", date = day(13),
                mood = "good", appetite = "good", sleep = "broken",
                mobility = "walks", medicationTaken = "taken",
                pain = 2, fall = false,
                text = "استيقظ مبكرًا وتناول فطوره كاملًا.",
                createdAt = nowMs,
            ),
        )
    }

    fun addenda(nowMs: Long): List<AddendumEntity> = listOf(
        AddendumEntity(
            id = "a1", noteId = "n1",
            text = "Correction: appetite at dinner was reduced, not good.",
            createdAt = nowMs,
        ),
    )
}
