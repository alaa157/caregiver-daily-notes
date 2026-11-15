package com.caregiver.mobile.data.recipient

import com.caregiver.mobile.data.api.RecipientDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RecipientNameRulesTest {

    private fun recipient(id: String, name: String, archived: Boolean = false) = RecipientDto(
        id = id,
        name = name,
        active = !archived,
        archivedAt = if (archived) 1_700_000_000_000L else null,
    )

    @Test
    fun normalizeTrimsAndCollapsesWhitespace() {
        assertEquals("Mama Aisha", RecipientNameRules.normalize(" Mama Aisha "))
        assertEquals("Mama Aisha Junior", RecipientNameRules.normalize("Mama\t\n Aisha \n Junior"))
    }

    @Test
    fun checkAccepts120AndRejects121() {
        assertEquals(NameCheck.Ok, RecipientNameRules.check("a".repeat(120)))
        assertEquals(NameCheck.TooLong, RecipientNameRules.check("a".repeat(121)))
    }

    @Test
    fun checkRequiresNonEmpty() {
        assertEquals(NameCheck.Required, RecipientNameRules.check(""))
        assertEquals(NameCheck.Required, RecipientNameRules.check("   \t\n "))
    }

    @Test
    fun initialReturnsFirstCodePoint() {
        assertEquals("😀", RecipientNameRules.initial("😀Sam"))
        assertEquals("أ", RecipientNameRules.initial("أحمد"))
        assertEquals("s", RecipientNameRules.initial(" sam"))
        assertEquals("", RecipientNameRules.initial("   "))
    }

    @Test
    fun findDuplicateMatchesCaseAndWhitespaceInsensitively() {
        val existing = recipient("r1", "Mama Aisha")
        assertSame(existing, RecipientNameRules.findDuplicate("MAMA aisha", listOf(existing)))
    }

    @Test
    fun findDuplicateSkipsExcludedId() {
        val existing = recipient("r1", "Mama Aisha")
        assertNull(RecipientNameRules.findDuplicate("Mama Aisha", listOf(existing), excludeId = "r1"))
    }

    @Test
    fun findDuplicateFindsArchivedRecipient() {
        val archived = recipient("r2", "Mama Aisha", archived = true)
        assertSame(archived, RecipientNameRules.findDuplicate("mama aisha", listOf(archived)))
    }

    @Test
    fun findDuplicateReturnsNullWhenNothingMatches() {
        assertNull(
            RecipientNameRules.findDuplicate(
                "Mama Aisha",
                listOf(recipient("r1", "Papa Ahmed")),
            ),
        )
    }
}
