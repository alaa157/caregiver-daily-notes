package com.caregiver.mobile.data.plan

import com.caregiver.mobile.data.api.PlanVersionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan state machine: every legal and illegal transition, and the rule
 * that an accepted version supersedes the ones before it.
 */
class PlanStateMachineTest {
    private val suggested = "Suggested"
    private val accepted = "Accepted"
    private val editedAccepted = "Edited-and-Accepted"
    private val dismissed = "Dismissed"
    private val archived = "Archived"

    /** The legal moves, straight from the workflow. */
    private val legal = mapOf(
        suggested to setOf(accepted, editedAccepted, dismissed, archived),
        accepted to setOf(editedAccepted, archived),
        editedAccepted to setOf(accepted, editedAccepted, dismissed, archived),
        dismissed to setOf(editedAccepted, archived),
        archived to emptySet(),
    )

    private fun version(number: Int, status: String) = PlanVersionDto(
        version = number,
        status = status,
        items = listOf("item$number"),
        createdAt = "2026-10-01",
        reason = "",
    )

    @Test
    fun everyLegalTransitionIsAllowed() {
        for ((from, targets) in legal) {
            for (to in targets) {
                assertTrue("$from -> $to", PlanStateMachine.canTransition(from, to))
            }
        }
    }

    @Test
    fun everyIllegalTransitionIsRejected() {
        for (from in PlanStateMachine.statuses) {
            for (to in PlanStateMachine.statuses) {
                if (to in legal.getValue(from)) continue
                assertFalse("$from -> $to", PlanStateMachine.canTransition(from, to))
            }
        }
    }

    @Test
    fun archivedIsFinal() {
        for (to in PlanStateMachine.statuses) {
            assertFalse("Archived -> $to", PlanStateMachine.canTransition(archived, to))
        }
    }

    @Test
    fun unknownStatusIsNotAKnownState() {
        assertFalse(PlanStateMachine.isKnown("Pending"))
        assertFalse(PlanStateMachine.canTransition(suggested, "Pending"))
    }

    @Test
    fun transitionMovesTheNewestVersion() {
        val versions = listOf(version(1, accepted), version(2, suggested))
        val moved = PlanStateMachine.transition(versions, accepted)
        assertEquals(accepted, moved.first { it.version == 2 }.status)
    }

    @Test
    fun acceptedVersionArchivesTheOneItSupersedes() {
        val versions = listOf(version(1, accepted), version(2, suggested))
        val moved = PlanStateMachine.transition(versions, accepted)
        assertEquals(archived, moved.first { it.version == 1 }.status)
        assertEquals(accepted, moved.first { it.version == 2 }.status)
    }

    @Test
    fun acceptedVersionArchivesADismissedOne() {
        val versions = listOf(version(1, dismissed), version(2, suggested))
        val moved = PlanStateMachine.transition(versions, editedAccepted)
        assertEquals(archived, moved.first { it.version == 1 }.status)
        assertEquals(editedAccepted, moved.first { it.version == 2 }.status)
    }

    @Test
    fun onlyOneVersionStaysLive() {
        val versions = listOf(version(1, accepted), version(2, suggested))
        val moved = PlanStateMachine.transition(versions, accepted)
        assertEquals(1, moved.count { it.status in PlanStateMachine.live })
    }

    @Test
    fun archivingDoesNotTouchTheOlderVersions() {
        val versions = listOf(version(1, accepted), version(2, suggested))
        val moved = PlanStateMachine.transition(versions, archived)
        assertEquals(accepted, moved.first { it.version == 1 }.status)
        assertEquals(archived, moved.first { it.version == 2 }.status)
    }

    @Test
    fun illegalTransitionThrows() {
        val versions = listOf(version(1, suggested))
        val failure = runCatching { PlanStateMachine.transition(versions, "Pending") }
        assertTrue(failure.isFailure)
        val dismissedFirst = listOf(version(1, suggested))
        val afterDismiss = PlanStateMachine.transition(dismissedFirst, dismissed)
        val acceptDismissed = runCatching { PlanStateMachine.transition(afterDismiss, accepted) }
        assertTrue(acceptDismissed.isFailure)
    }

    @Test
    fun appendAddsANewVersion() {
        val versions = listOf(version(1, suggested))
        val appended = PlanStateMachine.append(versions, editedAccepted, listOf("edited"), "2026-10-02")
        assertEquals(2, appended.size)
        assertEquals(2, appended.last().version)
        assertEquals(editedAccepted, appended.last().status)
        assertEquals(listOf("edited"), appended.last().items)
    }

    @Test
    fun appendedAcceptedVersionArchivesTheOldAcceptedOne() {
        val versions = listOf(version(1, accepted), version(2, suggested))
        val appended = PlanStateMachine.append(versions, accepted, listOf("new"), "2026-10-02")
        assertEquals(archived, appended.first { it.version == 1 }.status)
        assertEquals(accepted, appended.last().status)
        assertEquals(1, appended.count { it.status in PlanStateMachine.live })
    }

    @Test
    fun anEditedVersionCanFollowASuggestedPlan() {
        // The everyday flow: the caregiver edits the suggestion and accepts it.
        val versions = listOf(version(1, suggested))
        val appended = PlanStateMachine.append(versions, editedAccepted, listOf("edited"), "2026-10-02")
        assertEquals(editedAccepted, appended.last().status)
        assertEquals(suggested, appended.first { it.version == 1 }.status)
    }

    @Test
    fun anAcceptedVersionCanFollowADismissedPlan() {
        val versions = listOf(version(1, dismissed))
        val appended = PlanStateMachine.append(versions, accepted, listOf("new"), "2026-10-02")
        assertEquals(archived, appended.first { it.version == 1 }.status)
        assertEquals(accepted, appended.last().status)
    }

    @Test
    fun appendedVersionCannotBeSuggested() {
        val versions = listOf(version(1, suggested))
        assertTrue(runCatching { PlanStateMachine.append(versions, suggested, emptyList(), "2026-10-02") }.isFailure)
    }

    @Test
    fun appendToAnArchivedPlanIsRejected() {
        val versions = listOf(version(1, archived))
        assertTrue(runCatching { PlanStateMachine.append(versions, accepted, emptyList(), "2026-10-02") }.isFailure)
    }

    @Test
    fun appendWithAnUnknownStatusIsRejected() {
        val versions = listOf(version(1, suggested))
        assertTrue(runCatching { PlanStateMachine.append(versions, "Pending", emptyList(), "2026-10-02") }.isFailure)
    }

    @Test
    fun appendCannotDismissAnAcceptedPlan() {
        val versions = listOf(version(1, accepted))
        assertTrue(runCatching { PlanStateMachine.append(versions, dismissed, emptyList(), "2026-10-02") }.isFailure)
    }
}
