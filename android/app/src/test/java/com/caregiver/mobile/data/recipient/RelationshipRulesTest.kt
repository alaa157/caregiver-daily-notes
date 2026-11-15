package com.caregiver.mobile.data.recipient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipRulesTest {

    @Test
    fun codeOfNormalizesCaseAndWhitespace() {
        assertEquals("MOTHER", RelationshipOptions.codeOf(" mother "))
        assertNull(RelationshipOptions.codeOf("Aunt"))
        assertNull(RelationshipOptions.codeOf(null))
        assertNull(RelationshipOptions.codeOf("  "))
    }

    @Test
    fun choiceOfMapsCodesAndFreeText() {
        assertEquals("OTHER" to "Aunt", RelationshipOptions.choiceOf("Aunt"))
        assertEquals("FRIEND" to "", RelationshipOptions.choiceOf("FRIEND"))
        assertEquals(null to "", RelationshipOptions.choiceOf(" "))
    }

    @Test
    fun storedValueUsesOtherTextOnlyForTheOtherChip() {
        assertEquals("Aunt", RelationshipOptions.storedValue("OTHER", " Aunt "))
        assertNull(RelationshipOptions.storedValue("OTHER", " "))
        assertEquals("MOTHER", RelationshipOptions.storedValue("MOTHER", "ignored"))
    }

    @Test
    fun normalizeAndLengthLimit() {
        assertEquals("Aunt", RelationshipOptions.normalize("  Aunt  "))
        assertNull(RelationshipOptions.normalize("   "))
        assertNull(RelationshipOptions.normalize(null))

        val overLimit = "x".repeat(RelationshipOptions.MAX_LENGTH + 1)
        assertTrue(RelationshipOptions.isTooLong(overLimit))
        assertFalse(RelationshipOptions.isTooLong("x".repeat(RelationshipOptions.MAX_LENGTH)))
        assertFalse(RelationshipOptions.isTooLong(null))
    }
}
