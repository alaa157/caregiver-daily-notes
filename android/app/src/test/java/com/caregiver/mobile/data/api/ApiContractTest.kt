package com.caregiver.mobile.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(true, parsed.painRecorded)
        assertEquals(true, parsed.fallRecorded)
    }

    @Test
    fun noteDtoRecordedFlagsRoundTripAndCopy() {
        val parsed = ApiClient.json.decodeFromString<NoteDto>(
            NoteDto.serializer(),
            """{"id":"n1","recipientId":"r1","date":"2026-10-07","mood":"good","appetite":"good","sleep":"ok","mobility":"walks","medicationTaken":"taken","pain":2,"fall":false,"text":"ate well","painRecorded":false,"fallRecorded":false}""",
        )
        assertEquals(false, parsed.painRecorded)
        assertEquals(false, parsed.fallRecorded)

        val encoded = ApiClient.json.encodeToString(NoteDto.serializer(), parsed)
        assertTrue(encoded.contains("\"painRecorded\":false"))
        assertTrue(encoded.contains("\"fallRecorded\":false"))

        val roundTripped = ApiClient.json.decodeFromString<NoteDto>(NoteDto.serializer(), encoded)
        assertEquals(false, roundTripped.painRecorded)
        assertEquals(false, roundTripped.fallRecorded)

        val copied = parsed.copy(text = "changed")
        assertEquals(parsed.painRecorded, copied.painRecorded)
        assertEquals(parsed.fallRecorded, copied.fallRecorded)
    }

    @Test
    fun noteDetailDtoDefaultsRecordedFlagsWhenFieldsAreMissing() {
        val parsed = ApiClient.json.decodeFromString<NoteDetailDto>(
            NoteDetailDto.serializer(),
            """{"id":"n1","recipientId":"r1","date":"2026-10-07","mood":"good","appetite":"good","sleep":"ok","mobility":"walks","medicationTaken":"taken","pain":2,"fall":false,"text":"ate well","addenda":[]}""",
        )
        assertEquals(true, parsed.painRecorded)
        assertEquals(true, parsed.fallRecorded)
    }

    @Test
    fun createNoteRequestDefaultsRecordedFlagsWhenFieldsAreMissing() {
        val parsed = ApiClient.json.decodeFromString<CreateNoteRequest>(
            CreateNoteRequest.serializer(),
            """{"recipientId":"r1","mood":"good","appetite":"good","sleep":"ok","mobility":"walks","medicationTaken":"taken","pain":2,"fall":false,"text":"ate well"}""",
        )
        assertEquals(true, parsed.painRecorded)
        assertEquals(true, parsed.fallRecorded)
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
    fun evidenceWithoutClaimDecodesEmpty() {
        val parsed = ApiClient.json.decodeFromString<SummaryDto>(
            SummaryDto.serializer(),
            """{"id":"s1","recipientId":"r1","periodDays":7,"text":"ok","redFlags":[],"evidence":[{"noteId":"n1","quote":"fell"}],"uncertainties":[]}""",
        )
        assertEquals("", parsed.evidence[0].claim)
    }

    @Test
    fun emptySummarySectionsParse() {
        // The contract has no trends/appetite/sleep/medication sections —
        // only these fields. Empty lists must parse, not crash.
        val parsed = ApiClient.json.decodeFromString<SummaryDto>(
            SummaryDto.serializer(),
            """{"id":"s1","recipientId":"r1","periodDays":7,"text":"No notes in range.","redFlags":[],"evidence":[],"uncertainties":[]}""",
        )
        assertTrue(parsed.redFlags.isEmpty())
        assertTrue(parsed.evidence.isEmpty())
        assertTrue(parsed.uncertainties.isEmpty())
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
    fun baseUrlValidation() {
        assertEquals(
            BaseUrlCheck.Valid("https://h.com/api/"),
            ApiClient.checkBaseUrl("https://h.com/api"),
        )
        assertEquals(
            BaseUrlCheck.Valid("https://h.com/api/"),
            ApiClient.checkBaseUrl("https://h.com/api/"),
        )
        assertEquals(
            BaseUrlCheck.Valid("https://h.com/"),
            ApiClient.checkBaseUrl("  https://h.com "),
        )
        // Missing scheme defaults to HTTPS, never silently to HTTP.
        assertEquals(
            BaseUrlCheck.Valid("https://h.com:8080/"),
            ApiClient.checkBaseUrl("h.com:8080"),
        )
        assertEquals(
            BaseUrlCheck.Invalid(UrlProblem.Empty),
            ApiClient.checkBaseUrl("   "),
        )
        assertEquals(
            BaseUrlCheck.Invalid(UrlProblem.Unparsable),
            ApiClient.checkBaseUrl("https://"),
        )
        assertEquals(
            BaseUrlCheck.Invalid(UrlProblem.UnsupportedScheme),
            ApiClient.checkBaseUrl("ftp://h.com/"),
        )
        assertEquals(
            BaseUrlCheck.Invalid(UrlProblem.UserInfo),
            ApiClient.checkBaseUrl("https://user@h.com/"),
        )
        assertEquals(
            BaseUrlCheck.Invalid(UrlProblem.Query),
            ApiClient.checkBaseUrl("https://h.com/?x=1"),
        )
        assertEquals(
            BaseUrlCheck.Invalid(UrlProblem.Fragment),
            ApiClient.checkBaseUrl("https://h.com/#f"),
        )
    }

    @Test
    fun httpAllowedOnlyForDevHosts() {
        assertEquals(
            BaseUrlCheck.Valid("http://localhost:8080/api/"),
            ApiClient.checkBaseUrl("http://localhost:8080/api"),
        )
        assertEquals(
            BaseUrlCheck.Valid("http://127.0.0.1:8080/"),
            ApiClient.checkBaseUrl("http://127.0.0.1:8080/"),
        )
        assertEquals(
            BaseUrlCheck.Valid("http://10.0.2.2:8080/"),
            ApiClient.checkBaseUrl("http://10.0.2.2:8080/"),
        )
    }

    @Test
    fun httpRejectedOutsideTheNetworkSecurityAllowlist() {
        // These passed the old in-code check and were then blocked by Android
        // at connect time, so the user saw a network failure instead of a
        // base-URL problem.
        for (url in listOf(
            "http://192.168.1.5/",
            "http://10.1.2.3/",
            "http://172.16.0.1/",
            "http://127.0.0.2/",
            "http://[::1]/",
            "http://h.com/",
        )) {
            assertEquals(
                "cleartext must be rejected for $url",
                BaseUrlCheck.Invalid(UrlProblem.HttpNotAllowed),
                ApiClient.checkBaseUrl(url),
            )
        }
    }

    @Test
    fun recipientDtoWithoutCareFieldsDecodesNulls() {
        val parsed = ApiClient.json.decodeFromString<RecipientDto>(
            RecipientDto.serializer(),
            """{"id":"r1","name":"Aisha","active":true}""",
        )
        assertNull(parsed.relationship)
        assertNull(parsed.dateOfBirth)
        assertNull(parsed.allergies)
        assertNull(parsed.conditions)
        assertNull(parsed.emergencyContactName)
        assertNull(parsed.emergencyContactPhone)
    }

    @Test
    fun recipientDtoCareFieldsRoundTrip() {
        val dto = RecipientDto(
            id = "r1",
            name = "Aisha",
            active = true,
            archivedAt = 1_700_000_000_000L,
            relationship = "MOTHER",
            dateOfBirth = "1950-04-12",
            allergies = "penicillin",
            conditions = "hypertension",
            emergencyContactName = "Omar",
            emergencyContactPhone = "+966500000000",
        )
        val json = ApiClient.json.encodeToString(RecipientDto.serializer(), dto)
        val parsed = ApiClient.json.decodeFromString<RecipientDto>(RecipientDto.serializer(), json)
        assertEquals(dto, parsed)
    }

    @Test
    fun recipientDtoCopyKeepsCareFields() {
        val dto = RecipientDto(
            id = "r1",
            name = "Aisha",
            active = true,
            relationship = "MOTHER",
            dateOfBirth = "1950-04-12",
            allergies = "penicillin",
            conditions = "hypertension",
            emergencyContactName = "Omar",
            emergencyContactPhone = "+966500000000",
        )
        val copied = dto.copy(name = "Aisha Mohamed")
        assertEquals("MOTHER", copied.relationship)
        assertEquals("1950-04-12", copied.dateOfBirth)
        assertEquals("penicillin", copied.allergies)
        assertEquals("hypertension", copied.conditions)
        assertEquals("Omar", copied.emergencyContactName)
        assertEquals("+966500000000", copied.emergencyContactPhone)
    }

    @Test
    fun createRecipientRequestWithoutRelationshipDecodesNull() {
        val parsed = ApiClient.json.decodeFromString<CreateRecipientRequest>(
            CreateRecipientRequest.serializer(),
            """{"name":"Aisha"}""",
        )
        assertEquals("Aisha", parsed.name)
        assertNull(parsed.relationship)
    }
}
