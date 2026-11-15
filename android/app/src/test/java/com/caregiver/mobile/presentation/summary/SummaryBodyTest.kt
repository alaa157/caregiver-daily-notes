package com.caregiver.mobile.presentation.summary

import com.caregiver.mobile.data.api.SummaryDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `usesObservationCards` decides whether the summary body swaps the overview
 * paragraph and flat quote list for per-observation cards.
 */
class SummaryBodyTest {

    private fun evidence(claim: String) = SummaryDto.EvidenceDto(
        noteId = "n1",
        quote = "a quote",
        claim = claim,
    )

    @Test
    fun emptyEvidenceGivesFalse() {
        assertFalse(usesObservationCards(emptyList()))
    }

    @Test
    fun evidenceWhereEveryItemHasAClaimGivesTrue() {
        assertTrue(
            usesObservationCards(
                listOf(evidence("claim one"), evidence("claim two")),
            ),
        )
    }

    @Test
    fun evidenceWithOneBlankClaimGivesFalse() {
        assertFalse(
            usesObservationCards(
                listOf(evidence("claim one"), evidence("   ")),
            ),
        )
    }
}
