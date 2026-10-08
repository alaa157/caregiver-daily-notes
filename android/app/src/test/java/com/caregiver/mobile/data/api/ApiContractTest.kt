package com.caregiver.mobile.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wire fixtures: Kotlin DTOs must serialize/parse exactly the backend shapes
 * (field names from the preview controllers), tolerate unknown future fields,
 * and normalize user-typed server URLs into valid Retrofit base URLs.
 */
class ApiContractTest {

    @Test
    fun loginRequestSerializesExactly() {
        assertEquals(
            """{"email":"a@b.c","password":"pw"}""",
            ApiClient.json.encodeToString(
                LoginRequest.serializer(),
                LoginRequest("a@b.c", "pw"),
            ),
        )
    }

    @Test
    fun authResponseParses() {
        val parsed = ApiClient.json.decodeFromString<AuthResponse>(
            AuthResponse.serializer(),
            """{"token":"tok123"}""",
        )
        assertEquals("tok123", parsed.token)
    }

    @Test
    fun errorCodesParse() {
        val duplicate = ApiClient.json.decodeFromString<ErrorResponse>(
            ErrorResponse.serializer(),
            """{"code":"DUPLICATE_EMAIL","message":"Email is already registered."}""",
        )
        assertEquals("DUPLICATE_EMAIL", duplicate.code)

        val invalid = ApiClient.json.decodeFromString<ErrorResponse>(
            ErrorResponse.serializer(),
            """{"code":"INVALID_CREDENTIALS","message":"Invalid email or password."}""",
        )
        assertEquals("INVALID_CREDENTIALS", invalid.code)
    }

    @Test
    fun noteDtoParsesAllFields() {
        val parsed = ApiClient.json.decodeFromString<NoteDto>(
            NoteDto.serializer(),
            """{"id":"n1","recipientId":"r1","date":"2026-10-07","mood":"good","appetite":"good","sleep":"ok","mobility":"walks","medicationTaken":"taken","pain":2,"fall":false,"text":"ate well"}""",
        )
        assertEquals("n1", parsed.id)
        assertEquals("r1", parsed.recipientId)
        assertEquals("2026-10-07", parsed.date)
        assertEquals("taken", parsed.medicationTaken)
        assertEquals(2, parsed.pain)
        assertEquals(false, parsed.fall)
    }

    @Test
    fun summaryDtoNestedParses() {
        val parsed = ApiClient.json.decodeFromString<SummaryDto>(
            SummaryDto.serializer(),
            """{"id":"s1","recipientId":"r1","periodDays":7,"text":"ok","redFlags":["fall"],"evidence":[{"noteId":"n1","quote":"fell"}],"uncertainties":[{"topic":"meds","detail":"unsure"}]}""",
        )
        assertEquals(listOf("fall"), parsed.redFlags)
        assertEquals("n1", parsed.evidence[0].noteId)
        assertEquals("fell", parsed.evidence[0].quote)
        assertEquals("meds", parsed.uncertainties[0].topic)
    }

    @Test
    fun planVersionDtoParses() {
        val parsed = ApiClient.json.decodeFromString<PlanVersionDto>(
            PlanVersionDto.serializer(),
            """{"version":2,"status":"Accepted","items":["walk"],"createdAt":"2026-10-07T10:00:00","reason":"ok"}""",
        )
        assertEquals(2, parsed.version)
        assertEquals("Accepted", parsed.status)
        assertEquals(listOf("walk"), parsed.items)
    }

    @Test
    fun unknownFutureFieldsAreIgnored() {
        val parsed = ApiClient.json.decodeFromString<AuthResponse>(
            AuthResponse.serializer(),
            """{"token":"tok","expiresAt":"tomorrow"}""",
        )
        assertEquals("tok", parsed.token)
    }

    @Test
    fun baseUrlNormalization() {
        assertEquals("https://h.com/api/", ApiClient.normalizeBaseUrl("https://h.com/api/"))
        assertEquals("https://h.com/api/", ApiClient.normalizeBaseUrl("https://h.com/api"))
        assertEquals("https://h.com/", ApiClient.normalizeBaseUrl("  https://h.com "))
        assertEquals("http://h.com:8080/", ApiClient.normalizeBaseUrl("h.com:8080"))
    }
}
