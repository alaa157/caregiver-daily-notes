package com.caregiver.mobile.presentation.common

import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.csv.RecipientRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Archived recipients must never leak into a creation flow. These filters are
 * the single invariant used by the note picker, the home/plan/medication
 * surfaces, and the CSV import target list, so testing them here protects
 * every entry point without duplicating the rule in each screen.
 */
class RecipientFiltersTest {

    private val active = RecipientDto("a", "Aisha", true)
    private val inactive = RecipientDto("i", "Inactive", false)
    private val archived = RecipientDto("z", "Archived", true, 1_000L)
    private val archivedInactive = RecipientDto("zi", "Archived inactive", false, 2_000L)

    private val all = listOf(active, inactive, archived, archivedInactive)

    @Test
    fun archivedRecipientIsNotSelectableForNewNote() {
        assertEquals(listOf("a"), selectableRecipients(all).map { it.id })
    }

    @Test
    fun archivedRecipientIsNotSelectableForNewPlan() {
        assertEquals(listOf("a"), selectableRecipients(all).map { it.id })
    }

    @Test
    fun archivedRecipientIsNotSelectableForMedication() {
        assertEquals(listOf("a"), selectableRecipients(all).map { it.id })
    }

    @Test
    fun archivedRecipientIsNotSelectableForCsvImport() {
        val refs = listOf(
            RecipientRef("a", "Aisha", true, archived = false),
            RecipientRef("z", "Archived", true, archived = true),
        )

        assertEquals(listOf("a"), selectableCsvRecipients(refs).map { it.id })
    }

    @Test
    fun inactiveRecipientIsNotSelectableEvenWhenNotArchived() {
        assertEquals(listOf("a"), selectableRecipients(all).map { it.id })
    }
}
