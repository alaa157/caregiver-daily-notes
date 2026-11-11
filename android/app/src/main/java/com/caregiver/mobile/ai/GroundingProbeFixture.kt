package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import java.time.LocalDate

/** Fictional records used only to check whether a selected model preserves evidence. */
internal object GroundingProbeFixture {
    const val RECIPIENT_ID = "grounding-probe-recipient"
    const val MEDICATION_NOTE_ID = "grounding-probe-medication"
    const val ROUTINE_NOTE_ID = "grounding-probe-routine"

    fun notes(endInclusive: LocalDate): List<NoteDto> = listOf(
        NoteDto(
            id = "grounding-probe-mood",
            recipientId = RECIPIENT_ID,
            date = endInclusive.minusDays(2).toString(),
            mood = "good",
            appetite = "normal",
            sleep = "rested",
            mobility = "steady",
            medicationTaken = "yes",
            pain = 0,
            fall = false,
            text = "Had a calm morning.",
        ),
        NoteDto(
            id = MEDICATION_NOTE_ID,
            recipientId = RECIPIENT_ID,
            date = endInclusive.minusDays(1).toString(),
            mood = "neutral",
            appetite = "normal",
            sleep = "rested",
            mobility = "steady",
            medicationTaken = "yes",
            pain = 0,
            fall = false,
            text = "Took morning medication with breakfast.",
        ),
        NoteDto(
            id = ROUTINE_NOTE_ID,
            recipientId = RECIPIENT_ID,
            date = endInclusive.toString(),
            mood = "good",
            appetite = "normal",
            sleep = "rested",
            mobility = "steady",
            medicationTaken = "yes",
            pain = 0,
            fall = false,
            text = "Enjoyed a quiet afternoon at home.",
        ),
    )
}
