package com.caregiver.mobile.ai

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingProbeTest {
    private class FakeLlmClient(vararg private val results: LlmResult) : LlmClient {
        val requests = mutableListOf<LlmRequest>()
        private var responseIndex = 0

        override suspend fun complete(request: LlmRequest): LlmResult {
            requests += request
            val index = responseIndex++.coerceAtMost(results.lastIndex)
            return results[index]
        }
    }

    private fun observation(noteId: String, text: String, quote: String): String =
        """{"text":"$text","noteId":"$noteId","evidenceType":"NOTE_TEXT","quote":"$quote"}"""

    private fun response(vararg observations: String): String =
        """{"observations":[${observations.joinToString(",")}],"uncertainties":[],"urgent":false}"""

    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun verbatimGroundedFindingPassesAndOnlyFixtureNotesAreSent() = runBlocking {
        val client = FakeLlmClient(
            LlmResult(
                LlmStatus.OK,
                response(
                    observation(
                        noteId = "grounding-probe-medication",
                        text = "Took morning medication",
                        quote = "Took morning medication with breakfast.",
                    ),
                ),
            ),
        )

        val result = GroundingProbe.run(client, "en", endInclusive = today)

        assertEquals(GroundingProbeStatus.PASS, result.status)
        assertEquals(1, result.totalObservations)
        assertEquals(1, result.groundedObservations)
        assertEquals(1, client.requests.size)
        val dataBlock = client.requests.single().dataBlock
        val fixtureNotes = GroundingProbeFixture.notes(today)
        fixtureNotes.forEach { note -> assertTrue(dataBlock.contains(note.text)) }
        val sentNoteIds = Json.parseToJsonElement(dataBlock)
            .jsonObject["notes"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(
            listOf(
                "grounding-probe-mood",
                "grounding-probe-medication",
                "grounding-probe-routine",
            ),
            sentNoteIds,
        )
        assertTrue(fixtureNotes.all { LocalDate.parse(it.date) in today.minusDays(6)..today })
    }

    @Test
    fun paraphrasedEvidenceFailsGrounding() = runBlocking {
        val client = FakeLlmClient(
            LlmResult(
                LlmStatus.OK,
                response(
                    observation(
                        noteId = "grounding-probe-medication",
                        text = "Took morning medication",
                        quote = "She took her pills before breakfast.",
                    ),
                ),
            ),
        )

        val result = GroundingProbe.run(client, "en", endInclusive = today)

        assertEquals(GroundingProbeStatus.FAIL, result.status)
        assertEquals(1, result.totalObservations)
        assertEquals(0, result.groundedObservations)
    }

    @Test
    fun mixedGroundedAndParaphrasedFindingsArePartialWithExactCounts() = runBlocking {
        val client = FakeLlmClient(
            LlmResult(
                LlmStatus.OK,
                response(
                    observation(
                        noteId = "grounding-probe-medication",
                        text = "Took morning medication",
                        quote = "Took morning medication with breakfast.",
                    ),
                    observation(
                        noteId = "grounding-probe-routine",
                        text = "Enjoyed a quiet afternoon indoors",
                        quote = "Spent a peaceful afternoon at home.",
                    ),
                ),
            ),
        )

        val result = GroundingProbe.run(client, "en", endInclusive = today)

        assertEquals(GroundingProbeStatus.PARTIAL, result.status)
        assertEquals(2, result.totalObservations)
        assertEquals(1, result.groundedObservations)
    }

    @Test
    fun invalidKeyIsAnErrorWithTheProviderReasonCode() = runBlocking {
        val client = FakeLlmClient(LlmResult(LlmStatus.FALLBACK, error = "invalid_key"))

        val result = GroundingProbe.run(client, "en", endInclusive = today)

        assertEquals(GroundingProbeStatus.ERROR, result.status)
        assertEquals("invalid_key", result.reasonCode)
    }

    @Test
    fun providerFailureDuringJsonRepairIsAnErrorWithTheRepairReason() = runBlocking {
        val client = FakeLlmClient(
            LlmResult(LlmStatus.OK, "not valid JSON"),
            LlmResult(LlmStatus.FALLBACK, error = "invalid_key"),
        )

        val result = GroundingProbe.run(client, "en", endInclusive = today)

        assertEquals(GroundingProbeStatus.ERROR, result.status)
        assertEquals("invalid_key", result.reasonCode)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun noConsentMeansTheProbeDoesNotCallTheProvider() = runBlocking {
        val client = FakeLlmClient(
            LlmResult(
                LlmStatus.OK,
                response(
                    observation(
                        noteId = "grounding-probe-medication",
                        text = "Took morning medication",
                        quote = "Took morning medication with breakfast.",
                    ),
                ),
            ),
        )

        val result = GroundingProbe.runIfConsented(
            client = client,
            language = "en",
            consentGiven = false,
            endInclusive = today,
        )

        assertNull(result)
        assertTrue(client.requests.isEmpty())
    }
}
