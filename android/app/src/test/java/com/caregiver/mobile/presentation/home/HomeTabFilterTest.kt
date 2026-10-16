package com.caregiver.mobile.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 5 sub-tab filter: pure client-side narrowing of the cards the
 * dashboard already loaded. Today = a note was recorded today,
 * Needs attention = high pain or a fall (the same rule the dashboard stat
 * uses), All = everything. No request per tab, no pagination.
 */
class HomeTabFilterTest {

    private fun card(vararg chips: RecipientChip) =
        HomeCard(recipientId = "r", name = "n", chips = chips.toList(), lastNote = null)

    private val done = card(RecipientChip.DoneToday)
    private val missed = card(RecipientChip.MissedToday)
    private val pain = card(RecipientChip.MissedToday, RecipientChip.HighPain)
    private val fall = card(RecipientChip.DoneToday, RecipientChip.FallFlag)
    private val noNote = card(RecipientChip.NoNote)

    @Test
    fun todayKeepsOnlyCardsWithANoteRecordedToday() {
        val cards = listOf(done, missed, pain, fall, noNote)

        assertEquals(listOf(done, fall), homeCardsForTab(cards, HomeTab.Today))
    }

    @Test
    fun needsAttentionKeepsHighPainAndFallOnly() {
        val cards = listOf(done, missed, pain, fall, noNote)

        assertEquals(listOf(pain, fall), homeCardsForTab(cards, HomeTab.NeedsAttention))
    }

    @Test
    fun allKeepsEveryCardInOrder() {
        val cards = listOf(done, missed, pain, fall, noNote)

        assertEquals(cards, homeCardsForTab(cards, HomeTab.All))
    }

    @Test
    fun todayCanFilterToEmpty() {
        val cards = listOf(missed, noNote)

        assertEquals(emptyList<HomeCard>(), homeCardsForTab(cards, HomeTab.Today))
    }

    @Test
    fun needsAttentionCanFilterToEmpty() {
        val cards = listOf(done, missed, noNote)

        assertEquals(emptyList<HomeCard>(), homeCardsForTab(cards, HomeTab.NeedsAttention))
    }
}
