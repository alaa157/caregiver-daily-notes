package com.caregiver.mobile.data.demo

import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.RegisterRequest
import com.caregiver.mobile.data.api.SummaryRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/**
 * Task 1: the debug demo provider behind [DemoBackendApis] — consistent
 * fixtures, in-memory mutations visible across calls, and 404s for
 * unknown ids (so screens render error states, never crashes).
 */
class DemoBackendApisTest {

    private fun apis() = DemoBackendApis()

    @Test
    fun defaultFixturesAreConsistent() = runTest {
        val backend = apis()
        val recipients = backend.recipients().list()
        assertTrue(recipients.any { it.id == "r1" && it.active })
        assertTrue(recipients.any { it.id == "r2" && it.active })
        val ids = recipients.map { it.id }.toSet()

        val notes = backend.notes().history(null, null, null)
        assertTrue(notes.isNotEmpty())
        notes.forEach { assertTrue("orphan note ${it.id}", it.recipientId in ids) }

        val withAddendum = notes.first { it.id == "n2" }
        val detail = backend.notes().detail(withAddendum.id)
        assertEquals(withAddendum.text, detail.text)
        assertTrue(detail.addenda.isNotEmpty())
        detail.addenda.forEach { assertEquals(withAddendum.id, it.noteId) }

        val summaries = backend.summaries().signals("r1", null, null)
        assertTrue(summaries.isNotEmpty())
        assertTrue(summaries.all { it.recipientId == "r1" })

        val plans = backend.plans().list()
        assertTrue(plans.isNotEmpty())
        plans.forEach { assertTrue("orphan plan ${it.id}", it.recipientId in ids) }
    }

    @Test
    fun anyCredentialsSignInOffline() = runTest {
        val backend = apis()
        assertEquals("demo-token", backend.auth().login(LoginRequest("a@b.c", "x")).token)
        assertEquals("demo-token", backend.auth().register(RegisterRequest("a@b.c", "x")).token)
    }

    @Test
    fun createdNoteAppearsInHistoryAndDetail() = runTest {
        val backend = apis()
        val created = backend.notes().create(
            CreateNoteRequest(
                recipientId = "r1", mood = "good", appetite = "good", sleep = "good",
                mobility = "walks", medicationTaken = "taken", pain = 1,
                fall = false, text = "demo round trip",
            ),
        )
        val inHistory = backend.notes().history("r1", null, null)
        assertTrue(inHistory.any { it.id == created.id && it.text == "demo round trip" })
        val detail = backend.notes().detail(created.id)
        assertEquals("demo round trip", detail.text)
        assertEquals(created.id, detail.id)
        assertTrue(detail.addenda.isEmpty())
    }

    @Test
    fun addendumAppendsWithoutChangingOriginal() = runTest {
        val backend = apis()
        val before = backend.notes().detail("n1")
        assertTrue(before.addenda.isEmpty())
        backend.notes().append("n1", CreateAddendumRequest("demo correction"))
        val after = backend.notes().detail("n1")
        assertEquals(before.text, after.text)
        assertEquals(listOf("demo correction"), after.addenda.map { it.text })
    }

    @Test
    fun createdRecipientAppearsInList() = runTest {
        val backend = apis()
        val created = backend.recipients().create(CreateRecipientRequest("Demo Person"))
        assertTrue(created.active)
        assertTrue(backend.recipients().list().any { it.id == created.id })
        val fetched = backend.recipients().get(created.id)
        assertEquals("Demo Person", fetched.name)
    }

    @Test
    fun planActionsUpdateLocalVersions() = runTest {
        val backend = apis()
        val versions = backend.plans().versions("p1")
        assertTrue(versions.size >= 2)
        assertEquals("Suggested", versions.maxByOrNull { it.version }?.status)

        backend.plans().accept("p1")
        assertEquals("Accepted", backend.plans().versions("p1").maxByOrNull { it.version }?.status)
        // The accepted version supersedes the one before it.
        assertEquals("Archived", backend.plans().versions("p1").first { it.version == 1 }.status)

        backend.plans().archive("p1")
        assertEquals("Archived", backend.plans().versions("p1").maxByOrNull { it.version }?.status)
    }

    @Test
    fun illegalPlanTransitionsAreRejected() = runTest {
        val backend = apis()
        backend.plans().dismiss("p1")
        assertEquals("Dismissed", backend.plans().versions("p1").maxByOrNull { it.version }?.status)

        // A dismissed plan cannot be accepted or dismissed again.
        assertEquals(422, illegalTransition { backend.plans().accept("p1") })
        assertEquals(422, illegalTransition { backend.plans().dismiss("p1") })

        // It can still be edited and accepted, which supersedes it.
        backend.plans().editAccept("p1")
        val versions = backend.plans().versions("p1")
        assertEquals("Edited-and-Accepted", versions.maxByOrNull { it.version }?.status)
        assertEquals("Archived", versions.first { it.version == 1 }.status)
    }

    @Test
    fun anArchivedPlanIsFinal() = runTest {
        val backend = apis()
        backend.plans().archive("p1")
        assertEquals(422, illegalTransition { backend.plans().accept("p1") })
        assertEquals(422, illegalTransition { backend.plans().dismiss("p1") })
        assertEquals(422, illegalTransition { backend.plans().editAccept("p1") })
        assertEquals(
            422,
            illegalTransition {
                backend.plans().appendVersion(
                    "p1",
                    com.caregiver.mobile.data.api.AppendVersionRequest("Edited-and-Accepted", listOf("x")),
                )
            },
        )
    }

    @Test
    fun aNewVersionSupersedesTheAcceptedOne() = runTest {
        val backend = apis()
        backend.plans().accept("p1")
        backend.plans().appendVersion(
            "p1",
            com.caregiver.mobile.data.api.AppendVersionRequest("Accepted", listOf("demo item")),
        )
        val versions = backend.plans().versions("p1")
        assertEquals(1, versions.count { it.status == "Accepted" })
        assertEquals("Accepted", versions.maxByOrNull { it.version }?.status)
    }

    @Test
    fun unknownIdsReturnNotFound() = runTest {
        val backend = apis()
        assertNotFound { backend.notes().detail("nope") }
        assertNotFound { backend.notes().append("nope", CreateAddendumRequest("x")) }
        assertNotFound { backend.recipients().get("nope") }
        assertNotFound { backend.plans().versions("nope") }
        assertNotFound { backend.plans().accept("nope") }
    }

    private suspend fun assertNotFound(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("expected 404 HttpException")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
    }

    private suspend fun illegalTransition(block: suspend () -> Unit): Int {
        try {
            block()
            throw AssertionError("expected an illegal transition to be rejected")
        } catch (e: HttpException) {
            return e.code()
        }
    }

    @Test
    fun summaryIncludesOnlyNotesInsidePeriod() = runTest {
        val backend = apis()
        // r1 fixtures: n1 (today, pain 2) and n2 (2 days ago, fall, pain 8).
        val week = backend.summaries().summarize(SummaryRequest("r1", 7))
        assertTrue(week.redFlags.contains("FALL_REPORTED"))
        assertTrue(week.redFlags.contains("HIGH_PAIN"))
        assertEquals(listOf("n1", "n2"), week.evidence.map { it.noteId })

        // A 1-day window excludes the 2-day-old fall note.
        val day = backend.summaries().summarize(SummaryRequest("r1", 1))
        assertTrue(day.redFlags.isEmpty())
        assertEquals(listOf("n1"), day.evidence.map { it.noteId })
    }

    @Test
    fun summaryFlagsFollowTheWindowNotAllNotes() = runTest {
        val backend = apis()
        // r2 fixtures: n3 (3 days ago) and n4 (6 days ago, pain 5).
        val narrow = backend.summaries().summarize(SummaryRequest("r2", 5))
        assertEquals(listOf("n3"), narrow.evidence.map { it.noteId })

        val wide = backend.summaries().summarize(SummaryRequest("r2", 7))
        assertEquals(listOf("n3", "n4"), wide.evidence.map { it.noteId })
    }

    @Test
    fun emptyPeriodYieldsEmptySummary() = runTest {
        val backend = apis()
        // r3's only note is 10 days old: a 7-day window is empty.
        val empty = backend.summaries().summarize(SummaryRequest("r3", 7))
        assertTrue(empty.redFlags.isEmpty())
        assertTrue(empty.evidence.isEmpty())
        assertTrue(empty.uncertainties.isEmpty())
        assertTrue(empty.text.contains("No demo notes"))
    }
}
