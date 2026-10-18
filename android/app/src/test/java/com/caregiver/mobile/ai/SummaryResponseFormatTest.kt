package com.caregiver.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Feature-level format validation for the settings screen: distinct from
 * connection testing and strict about the summary shape the app expects.
 */
class SummaryResponseFormatTest {

    @Test
    fun wellFormedSummaryPasses() {
        assertNull(
            SummaryResponseFormat.validate(
                """{"observations":[{"text":"t","noteId":"n1","quote":"q"}],"uncertainties":[{"topic":"x","detail":"y"}]}""",
            ),
        )
    }

    @Test
    fun emptyArraysPass() {
        assertNull(SummaryResponseFormat.validate("""{"observations":[],"uncertainties":[]}"""))
    }

    @Test
    fun nonJsonIsRejected() {
        assertNotNull(SummaryResponseFormat.validate("OK"))
        assertNotNull(SummaryResponseFormat.validate("Here you go: {\"observations\":[],\"uncertainties\":[]}"))
    }

    @Test
    fun missingTopLevelArraysAreRejected() {
        assertNotNull(SummaryResponseFormat.validate("""{}"""))
        assertNotNull(SummaryResponseFormat.validate("""{"observations":[]}"""))
        assertNotNull(SummaryResponseFormat.validate("""{"observations":{},"uncertainties":[]}"""))
    }

    @Test
    fun observationMissingRequiredStringsIsRejected() {
        assertNotNull(SummaryResponseFormat.validate("""{"observations":[{}],"uncertainties":[]}"""))
        assertNotNull(
            SummaryResponseFormat.validate(
                """{"observations":[{"text":"t","noteId":"n1"}],"uncertainties":[]}""",
            ),
        )
    }

    @Test
    fun reasonNamesTheProblem() {
        assertEquals("not a JSON object", SummaryResponseFormat.validate("nope"))
        assertEquals("missing uncertainties array", SummaryResponseFormat.validate("""{"observations":[]}"""))
    }
}
