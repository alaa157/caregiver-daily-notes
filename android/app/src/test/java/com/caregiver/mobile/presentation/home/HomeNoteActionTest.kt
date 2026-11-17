package com.caregiver.mobile.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure logic behind the "Write a note" action (#896): who to offer, in what
 * order, and that the chosen id is the one carried from the Home card.
 */
class HomeNoteActionTest {

    private fun card(id: String, name: String, writtenToday: Boolean) = HomeCard(
        recipientId = id,
        name = name,
        chips = if (writtenToday) listOf(RecipientChip.DoneToday) else listOf(RecipientChip.NoNote),
        lastNote = null,
    )

    @Test
    fun peopleWithoutANoteTodayComeFirstAndNamesBreakTies() {
        val cards = listOf(
            card("r-done", "Zainab", writtenToday = true),
            card("r-b", "Baker", writtenToday = false),
            card("r-a", "Amina", writtenToday = false),
        )

        assertEquals(
            listOf("r-a", "r-b", "r-done"),
            noteTargets(cards).map { it.recipientId },
        )
    }

    @Test
    fun pickerKeepsCardIdsInTheUndoneFirstOrder() {
        val cards = listOf(
            card("r-done", "Zainab", writtenToday = true),
            card("r-b", "Baker", writtenToday = false),
            card("r-a", "Amina", writtenToday = false),
        )

        val recipients = notePickerRecipients(cards)

        assertEquals(listOf("r-a", "r-b", "r-done"), recipients.map { it.id })
        assertEquals(listOf("Amina", "Baker", "Zainab"), recipients.map { it.name })
        assertEquals(true, recipients.all { it.active })
    }

    @Test
    fun onlyOnePersonSkipsThePicker() {
        assertNull(singleNoteTarget(emptyList()))
        assertEquals("r1", singleNoteTarget(listOf(card("r1", "Amina", writtenToday = false)))?.recipientId)
        assertNull(
            singleNoteTarget(
                listOf(
                    card("r1", "Amina", writtenToday = false),
                    card("r2", "Baker", writtenToday = true),
                ),
            ),
        )
    }
}
