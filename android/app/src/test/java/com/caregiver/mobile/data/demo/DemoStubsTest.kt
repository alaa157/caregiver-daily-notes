package com.caregiver.mobile.data.demo

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline stubs return fixed sample content (labeled "Demo" in the UI),
 * never medical advice, and only the demo fall rule raises flags.
 */
class DemoStubsTest {

    private val stub = DemoSummaryStub()
    private val today = LocalDate.now().toString()

    private fun note(id: String, fall: Boolean, text: String) = NoteEntity(
        id = id, recipientId = "r", date = today,
        mood = "good", appetite = "good", sleep = "good", mobility = "walks",
        medicationTaken = "taken", pain = 1, fall = fall, text = text,
        createdAt = 0,
    )

    @Test
    fun summaryBodyIsFixedSampleTextWithoutAdvice() {
        val summary = stub.summarize(listOf(note("a", false, "quote")), 7, false)
        assertTrue(summary.text.startsWith("Demo summary:"))
        val ar = stub.summarize(listOf(note("a", false, "quote")), 7, true)
        assertTrue(ar.text.startsWith("ملخص تجريبي:"))
    }

    @Test
    fun evidenceQuotesRecordedNotes() {
        val summary = stub.summarize(
            listOf(note("a", false, "first"), note("b", false, "second")),
            7, false,
        )
        assertEquals(listOf("first", "second"), summary.evidence.map { it.quote })
    }

    @Test
    fun onlyFallsRaiseFlags() {
        assertEquals(
            listOf("FALL_REPORTED"),
            stub.summarize(listOf(note("a", true, "t")), 7, false).redFlags,
        )
        assertEquals(
            emptyList<String>(),
            stub.summarize(listOf(note("a", false, "t")), 7, false).redFlags,
        )
    }

    @Test
    fun planProposalKeepsMedicationReadOnly() {
        val plans = DemoPlanStub()
        listOf(false, true).forEach { arabic ->
            val items = plans.proposal(arabic)
            assertTrue(items.isNotEmpty())
            assertTrue(items.any { it.medication })
        }
    }

    @Test
    fun planAcceptDismissAndEditFlow() {
        val plans = DemoPlanStub()
        assertEquals("Suggested", plans.versions(false).first().status)
        plans.accept()
        assertEquals("Accepted", plans.versions(false).first().status)
        plans.dismiss()
        assertEquals("Dismissed", plans.versions(false).first().status)
        plans.reset()
        assertEquals("Suggested", plans.versions(false).first().status)
    }

    @Test
    fun planEditAppendsVersionKeepingMedication() {
        val plans = DemoPlanStub()
        val edited = listOf(DemoPlanItem("Edited action", "reason", false))
        plans.editAccept(edited, false)
        val versions = plans.versions(false)
        assertEquals("Edited-and-Accepted", versions.first().status)
        assertTrue(versions.first().items.any { it.medication })
        assertTrue(versions.first().items.any { it.text == "Edited action" })
    }
}
