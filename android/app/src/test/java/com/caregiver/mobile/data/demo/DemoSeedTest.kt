package com.caregiver.mobile.data.demo

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First-launch seed: 3 fictional recipients, ~10 notes across the last 14
 * days in English and Arabic, one fall, corrections appended separately.
 */
class DemoSeedTest {

    private val today = LocalDate.now()
    private val notes = DemoSeed.notes(today, 0)

    @Test
    fun threeFictionalRecipients() {
        val people = DemoSeed.recipients(0)
        assertEquals(3, people.size)
        assertEquals(3, people.map { it.id }.toSet().size)
        people.forEach { assertTrue(it.name.isNotBlank()) }
    }

    @Test
    fun aboutTenNotesWithinFourteenDays() {
        assertTrue(notes.size in 8..12)
        notes.forEach { note ->
            val daysAgo = ChronoUnit.DAYS.between(LocalDate.parse(note.date), today)
            assertTrue("note ${note.id} dated ${note.date}", daysAgo in 0..14)
        }
    }

    @Test
    fun notesSpanEnglishAndArabic() {
        val arabic = notes.count { it.text.any { ch -> ch in '\u0600'..'\u06FF' } }
        val latin = notes.count { it.text.any { ch -> ch in 'A'..'z' } }
        assertTrue("expected Arabic notes, got $arabic", arabic >= 2)
        assertTrue("expected English notes, got $latin", latin >= 2)
    }

    @Test
    fun seedContainsAFallForTheRedFlagDemo() {
        assertTrue(notes.any { it.fall })
    }

    @Test
    fun seedContainsHighPainForThePreviewDemo() {
        assertTrue(notes.any { it.pain >= DemoSafetyRule.HIGH_PAIN_PREVIEW })
    }

    @Test
    fun addendaReferenceSeedNotes() {
        val ids = notes.map { it.id }.toSet()
        val addenda = DemoSeed.addenda(0)
        assertTrue(addenda.isNotEmpty())
        addenda.forEach { assertTrue(ids.contains(it.noteId)) }
    }
}
