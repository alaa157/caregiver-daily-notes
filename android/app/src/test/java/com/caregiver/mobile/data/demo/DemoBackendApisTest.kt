package com.caregiver.mobile.data.demo

import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.RegisterRequest
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

        backend.plans().dismiss("p1")
        assertEquals("Dismissed", backend.plans().versions("p1").maxByOrNull { it.version }?.status)

        backend.plans().archive("p1")
        assertEquals("Archived", backend.plans().versions("p1").maxByOrNull { it.version }?.status)

        val before = backend.plans().versions("p1").size
        backend.plans().appendVersion(
            "p1",
            com.caregiver.mobile.data.api.AppendVersionRequest("Edited-and-Accepted", listOf("demo item")),
        )
        val after = backend.plans().versions("p1")
        assertEquals(before + 1, after.size)
        assertEquals("Edited-and-Accepted", after.maxByOrNull { it.version }?.status)
        assertEquals(listOf("demo item"), after.maxByOrNull { it.version }?.items)
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
}
