package com.caregiver.mobile.presentation.notes

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure dictation-result handling for #905, testable without a device. */
class DictationTest {

    @Test
    fun blankResultNeverChangesTypedText() {
        assertEquals("Already typed", appendDictation("Already typed", ""))
        assertEquals("Already typed", appendDictation("Already typed", "   \n "))
    }

    @Test
    fun resultFillsAnEmptyField() {
        assertEquals("Felt fine", appendDictation("", "  Felt fine  "))
        assertEquals("Felt fine", appendDictation("   ", "Felt fine"))
    }

    @Test
    fun resultIsAppendedWithOneSpace() {
        assertEquals("First day Second day", appendDictation("First day ", "Second day"))
        assertEquals("First day Second day", appendDictation("First day\n", "Second day"))
    }

    @Test
    fun blankFieldAndBlankResultStayBlank() {
        assertEquals("", appendDictation("", ""))
    }
}
