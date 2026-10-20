package com.caregiver.mobile.data.plan

import com.caregiver.mobile.data.api.PlanVersionDto

/**
 * The plan workflow, enforced wherever a plan is stored.
 *
 * Mirrors the server rules and the state diagram in the README: a plan starts
 * as Suggested, can be Accepted, Edited-and-Accepted or Dismissed from there,
 * and Archived is final. Only a plan in a state that allows the move can be
 * moved.
 *
 * A version that is accepted also supersedes the versions before it: they are
 * archived, so a plan never has two live versions and the history stays
 * readable.
 */
object PlanStateMachine {
    const val SUGGESTED = "Suggested"
    const val ACCEPTED = "Accepted"
    const val EDITED_ACCEPTED = "Edited-and-Accepted"
    const val DISMISSED = "Dismissed"
    const val ARCHIVED = "Archived"

    /** Every status a plan version may hold. */
    val statuses: Set<String> = setOf(SUGGESTED, ACCEPTED, EDITED_ACCEPTED, DISMISSED, ARCHIVED)

    /** Statuses that count as the live version of a plan. */
    val live: Set<String> = setOf(ACCEPTED, EDITED_ACCEPTED)

    /** Statuses a newly accepted version replaces. */
    private val superseded: Set<String> = live + DISMISSED

    /** Which status may follow which on the newest version of a plan. */
    private val allowed: Map<String, Set<String>> = mapOf(
        SUGGESTED to setOf(ACCEPTED, EDITED_ACCEPTED, DISMISSED, ARCHIVED),
        ACCEPTED to setOf(EDITED_ACCEPTED, ARCHIVED),
        EDITED_ACCEPTED to setOf(ACCEPTED, EDITED_ACCEPTED, DISMISSED, ARCHIVED),
        DISMISSED to setOf(EDITED_ACCEPTED, ARCHIVED),
        ARCHIVED to emptySet(),
    )

    fun isKnown(status: String): Boolean = status in statuses

    /** True when the newest version of a plan may move from [from] to [to]. */
    fun canTransition(from: String, to: String): Boolean = allowed[from]?.contains(to) == true

    /**
     * Moves the newest version of [versions] to [status] and archives the
     * versions that status supersedes.
     *
     * @throws IllegalPlanTransitionException when the move is not allowed.
     */
    fun transition(versions: List<PlanVersionDto>, status: String): List<PlanVersionDto> {
        val latest = newest(versions)
        requireKnown(status)
        if (!canTransition(latest.status, status)) {
            throw IllegalPlanTransitionException("Illegal transition from ${latest.status} to $status.")
        }
        return versions.map { version ->
            when {
                version.version == latest.version -> version.copy(status = status)
                status in live && version.status in superseded -> version.copy(status = ARCHIVED)
                else -> version
            }
        }
    }

    /**
     * Adds a new newest version with [status] and archives the versions it
     * supersedes.
     *
     * The new version has to be a move the current state allows. A new
     * accepted version is the exception: it supersedes whatever came before
     * it, so it may also follow a plan that was accepted or dismissed
     * before — those versions are archived.
     *
     * @throws IllegalPlanTransitionException when the move is not allowed.
     */
    fun append(
        versions: List<PlanVersionDto>,
        status: String,
        items: List<String>,
        createdAt: String,
    ): List<PlanVersionDto> {
        val latest = newest(versions)
        requireKnown(status)
        if (status == SUGGESTED) {
            throw IllegalPlanTransitionException("A new plan version cannot be suggested.")
        }
        val reachable = allowed[latest.status].orEmpty().toMutableSet()
        if (status in live && latest.status != ARCHIVED) {
            reachable += status
        }
        if (status !in reachable) {
            throw IllegalPlanTransitionException("Illegal transition from ${latest.status} to $status.")
        }
        val archived = versions
            .filter { status in live && it.status in superseded }
            .map { it.version }
            .toSet()
        val next = PlanVersionDto(
            version = (versions.maxOfOrNull { it.version } ?: 0) + 1,
            status = status,
            items = items,
            createdAt = createdAt,
            reason = "",
        )
        return versions.map { version ->
            if (version.version in archived) version.copy(status = ARCHIVED) else version
        } + next
    }

    private fun newest(versions: List<PlanVersionDto>): PlanVersionDto =
        versions.maxByOrNull { it.version }
            ?: throw IllegalPlanTransitionException("Plan has no versions.")

    private fun requireKnown(status: String) {
        if (!isKnown(status)) {
            throw IllegalPlanTransitionException("Unknown plan status: $status.")
        }
    }
}

/** Raised when a plan status change is not allowed by the workflow. */
class IllegalPlanTransitionException(message: String) : Exception(message)
