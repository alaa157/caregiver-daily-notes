package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.presentation.notes.OptionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastNoteItemsTest {

    private fun note(
        mood: String = "",
        appetite: String = "",
        sleep: String = "",
        mobility: String = "",
        medicationTaken: String = "",
        pain: Int = 0,
        fall: Boolean = false,
        painRecorded: Boolean = false,
        fallRecorded: Boolean = false,
    ) = NoteDto(
        id = "n1",
        recipientId = "r1",
        date = "2026-10-07",
        mood = mood,
        appetite = appetite,
        sleep = sleep,
        mobility = mobility,
        medicationTaken = medicationTaken,
        pain = pain,
        fall = fall,
        text = "",
        painRecorded = painRecorded,
        fallRecorded = fallRecorded,
    )

    @Test
    fun unansweredPainProducesNoPainItem() {
        val items = lastNoteItems(note(pain = 7, painRecorded = false))

        assertFalse(items.any { it is LastNoteItem.Pain })
    }

    @Test
    fun recordedZeroPainProducesPainItem() {
        assertEquals(
            listOf(LastNoteItem.Pain(0)),
            lastNoteItems(note(pain = 0, painRecorded = true)),
        )
    }

    @Test
    fun recordedFallFlagControlsFallItem() {
        assertFalse(
            lastNoteItems(note(fall = true, fallRecorded = false))
                .any { it == LastNoteItem.FallReported },
        )
        assertEquals(
            1,
            lastNoteItems(note(fall = true, fallRecorded = true))
                .count { it == LastNoteItem.FallReported },
        )
    }

    @Test
    fun allBlankOptionsAndUnrecordedFlagsProduceNoItems() {
        val items = lastNoteItems(note(painRecorded = false, fallRecorded = false))

        assertTrue(items.isEmpty())
    }

    @Test
    fun nonBlankOptionsIncludeEveryRecordedOptionInDisplayOrder() {
        assertEquals(
            listOf(
                LastNoteItem.Option(OptionGroup.Mood, "good"),
                LastNoteItem.Option(OptionGroup.Appetite, "good"),
                LastNoteItem.Option(OptionGroup.Sleep, "good"),
                LastNoteItem.Option(OptionGroup.Mobility, "walks"),
                LastNoteItem.Option(OptionGroup.Medication, "taken"),
            ),
            lastNoteItems(
                note(
                    mood = "good",
                    appetite = "good",
                    sleep = "good",
                    mobility = "walks",
                    medicationTaken = "taken",
                ),
            ),
        )
    }
}
