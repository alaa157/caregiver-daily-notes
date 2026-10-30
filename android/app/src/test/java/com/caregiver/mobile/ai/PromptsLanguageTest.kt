package com.caregiver.mobile.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claim grounding compares the claim with the quote word by word. A summary
 * prompt that asks for Modern Standard Arabic while the note is Egyptian
 * dialect (or asks for English while the note is Arabizi) makes every good
 * observation look unsupported, so the language rule has to keep the claim
 * in the note's own language and script.
 */
class PromptsLanguageTest {
    @Test
    fun observationsStayInTheNotesLanguageAndScript() {
        for (language in listOf("en", "ar")) {
            val prompt = Prompts.summarySystemPrompt(language)
            assertTrue(
                "prompt for $language does not tie claims to the note's language",
                prompt.contains("same language and script as the note"),
            )
            assertTrue(prompt.contains("Do not translate a dialect note"))
            // The old Arabic rule required every observation in MSA, which
            // is exactly what made dialect claims ungroundable.
            assertFalse(prompt.contains("All human-readable values"))
        }
    }

    @Test
    fun arabicUncertaintiesStayInArabic() {
        val prompt = Prompts.summarySystemPrompt("ar")
        assertTrue(
            prompt.contains(
                "The \"topic\" and \"detail\" of each uncertainty must be written in " +
                    "clear, simple Modern Standard Arabic",
            ),
        )
    }

    @Test
    fun englishUncertaintiesStayInEnglish() {
        val prompt = Prompts.summarySystemPrompt("en")
        assertTrue(
            prompt.contains(
                "The \"topic\" and \"detail\" of each uncertainty must be written in English",
            ),
        )
    }

    @Test
    fun quotesAreStillVerbatim() {
        val prompt = Prompts.summarySystemPrompt("ar")
        assertTrue(prompt.contains("Keep every \"quote\" EXACTLY as it appears in its source note"))
    }
}
