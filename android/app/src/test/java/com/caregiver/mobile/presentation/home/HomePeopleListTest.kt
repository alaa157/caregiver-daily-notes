package com.caregiver.mobile.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Pure logic behind the redesigned Home people list (#897). */
class HomePeopleListTest {

    private val today = LocalDate.of(2026, 11, 17)

    private fun card(id: String, doneToday: Boolean) = HomeCard(
        recipientId = id,
        name = id,
        chips = listOf(if (doneToday) RecipientChip.DoneToday else RecipientChip.MissedToday),
        lastNote = null,
    )

    @Test
    fun lastNoteWhen_classifiesTodayYesterdayAndOlder() {
        assertEquals(LastNoteWhen.Today, lastNoteWhen("2026-11-17", today))
        assertEquals(LastNoteWhen.Yesterday, lastNoteWhen("2026-11-16", today))
        assertEquals(LastNoteWhen.Older, lastNoteWhen("2026-11-15", today))
        assertEquals(LastNoteWhen.Older, lastNoteWhen("2026-11-18", today))
    }

    @Test
    fun lastNoteWhen_toleratesDateTimeAndRejectsMalformed() {
        assertEquals(LastNoteWhen.Today, lastNoteWhen("2026-11-17T18:30:00", today))
        assertNull(lastNoteWhen(null, today))
        assertNull(lastNoteWhen("", today))
        assertNull(lastNoteWhen("not-a-date", today))
    }

    @Test
    fun peopleWithoutTodayNoteFirst_putsNotDoneFirstAndStaysStable() {
        val a = card("a", doneToday = false)
        val b = card("b", doneToday = true)
        val c = card("c", doneToday = false)
        val d = card("d", doneToday = true)

        assertEquals(
            listOf(a, c, b, d),
            peopleWithoutTodayNoteFirst(listOf(a, b, c, d)),
        )
    }

    @Test
    fun peopleWithoutTodayNoteFirst_handlesEmptyAndSingleGroups() {
        assertEquals(emptyList<HomeCard>(), peopleWithoutTodayNoteFirst(emptyList()))

        val done = card("done", doneToday = true)
        val notDone = card("not-done", doneToday = false)
        assertEquals(listOf(done), peopleWithoutTodayNoteFirst(listOf(done)))
        assertEquals(listOf(notDone), peopleWithoutTodayNoteFirst(listOf(notDone)))
    }
}
