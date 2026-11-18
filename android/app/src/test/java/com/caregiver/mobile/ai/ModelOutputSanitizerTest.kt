package com.caregiver.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelOutputSanitizerTest {
    @Test
    fun plainJsonIsUnchanged() {
        val json = """{"observations":[]}"""
        assertEquals(json, ModelOutputSanitizer.extractJson(json))
    }

    @Test
    fun jsonFenceIsRemoved() {
        val raw = "```json\n{\"observations\":[],\"uncertainties\":[]}\n```"
        assertEquals(
            """{"observations":[],"uncertainties":[]}""",
            ModelOutputSanitizer.extractJson(raw),
        )
    }

    @Test
    fun jsonFenceWithoutLanguageTagIsRemoved() {
        val raw = "```\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", ModelOutputSanitizer.extractJson(raw))
    }

    @Test
    fun preambleIsIgnored() {
        val raw = "Here is the summary you asked for:\n{\"observations\":[]}"
        assertEquals("""{"observations":[]}""", ModelOutputSanitizer.extractJson(raw))
    }

    @Test
    fun trailingProseIsIgnored() {
        val raw = """{"observations":[]} I hope this helps!"""
        assertEquals("""{"observations":[]}""", ModelOutputSanitizer.extractJson(raw))
    }

    @Test
    fun thinkBlockIsIgnored() {
        val raw = "<think>The user wants a summary.</think>{\"observations\":[]}"
        assertEquals("""{"observations":[]}""", ModelOutputSanitizer.extractJson(raw))
    }

    @Test
    fun fenceWithPreambleAndTrailingProseIsIgnored() {
        val raw = "Sure!\n```json\n{\"observations\":[]}\n```\nLet me know if you need more."
        assertEquals("""{"observations":[]}""", ModelOutputSanitizer.extractJson(raw))
    }

    @Test
    fun bracesInsideStringsDoNotBreakExtraction() {
        val raw = """{"text":"a } brace","noteId":"n1"}"""
        assertEquals(raw, ModelOutputSanitizer.extractJson(raw))
    }

    @Test
    fun escapedQuoteInsideStringDoesNotBreakExtraction() {
        val raw = """{"text":"she said \"hi\" today"}"""
        assertEquals(raw, ModelOutputSanitizer.extractJson(raw))
    }

    @Test
    fun jsonArrayIsExtracted() {
        assertEquals("""[1,2,3]""", ModelOutputSanitizer.extractJson("Result: [1,2,3]"))
    }

    @Test
    fun bracesInThePreambleDoNotShadowThePayload() {
        val raw = "Here is the {summary}: {\"observations\":[{\"noteId\":\"n1\"}],\"uncertainties\":[]}"
        assertEquals(
            """{"observations":[{"noteId":"n1"}],"uncertainties":[]}""",
            ModelOutputSanitizer.extractJson(raw),
        )
    }

    @Test
    fun nonJsonReplyHasNoPayload() {
        assertNull(ModelOutputSanitizer.extractJson("not json"))
        assertNull(ModelOutputSanitizer.extractJson(""))
        assertNull(ModelOutputSanitizer.extractJson("   "))
    }

    @Test
    fun truncatedJsonHasNoPayload() {
        assertNull(ModelOutputSanitizer.extractJson("""{"observations":[{"text":"walked" """))
    }

    @Test
    fun stripRemovesWrappersOnly() {
        assertEquals("""{"a":1}""", ModelOutputSanitizer.strip("```json\n{\"a\":1}\n```"))
        assertEquals("hello", ModelOutputSanitizer.strip("<think>x</think>hello"))
    }

    @Test
    fun isJsonStructureRejectsProse() {
        assertEquals(true, ModelOutputSanitizer.isJsonStructure("""{"a":1}"""))
        assertEquals(false, ModelOutputSanitizer.isJsonStructure("nope"))
        assertEquals(false, ModelOutputSanitizer.isJsonStructure(""))
        assertEquals(false, ModelOutputSanitizer.isJsonStructure("""{"a":"""))
    }
}
