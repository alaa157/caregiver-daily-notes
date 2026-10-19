package com.caregiver.mobile.data.backup

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Format v1: the envelope, the strict parser, version gating and the
 * payload digest. Hand-written documents cover shapes this writer never
 * produces; [encodeBackup] covers what it does.
 */
class BackupCodecTest {

    @Test
    fun roundTripPreservesArabicUnicodeAndEveryField() {
        val data = backupData(
            recipients = listOf(
                backupRecipient(serverId = "r1", name = "منى العليّ"),
                backupRecipient(serverId = "r2", name = "Renée \"Momo\"", active = false),
            ),
            notes = listOf(
                backupNote(
                    serverId = "n1",
                    recipientId = "r1",
                    text = "ملاحظة \"مهمة\"\nسطر ثانٍ، فاصلة، ١٢٣\nliteral\\nbackslash 🙂",
                    pain = 0,
                    fall = true,
                ),
            ),
            addenda = listOf(backupAddendum(text = "ملحق بالعربية")),
            plans = listOf(backupPlan(versionsJson = """[{"version":1,"reason":"مراجعة عربية"}]""")),
        )

        val decoded = (CareBackupCodec.decode(encodeBackup(data)) as BackupDecodeResult.Success).backup

        assertEquals(data, decoded.data)
        assertEquals("2026-10-19T09:00:00Z", decoded.createdAt)
        assertEquals("test (1)", decoded.appVersion)
        assertEquals(CareBackupCodec.FORMAT_VERSION, decoded.formatVersion)
    }

    @Test
    fun emptyBackupRoundTrips() {
        val data = backupData(emptyList(), emptyList(), emptyList(), emptyList())
        val decoded = (CareBackupCodec.decode(encodeBackup(data)) as BackupDecodeResult.Success).backup
        assertEquals(data, decoded.data)
        assertEquals(0, decoded.data.counts.total)
    }

    @Test
    fun encodedDocumentCarriesTheDocumentedEnvelopeFields() {
        val text = encodeBackup()
        assertTrue(text.contains("\"format\": \"${CareBackupCodec.FORMAT_ID}\""))
        assertTrue(text.contains("\"formatVersion\": ${CareBackupCodec.FORMAT_VERSION}"))
        assertTrue(text.contains("\"appName\": \"${CareBackupCodec.APP_NAME}\""))
        assertTrue(text.contains("\"algorithm\": \"${CareBackupCodec.INTEGRITY_ALGORITHM}\""))
        assertTrue(text.contains("\"digest\":"))
        assertTrue(text.contains("\"createdAt\": \"2026-10-19T09:00:00Z\""))
        assertTrue(text.contains("\"data\":"))
    }

    @Test
    fun aUtf8BomIsTolerated() {
        val withBom = "\uFEFF" + encodeBackup()
        assertTrue(CareBackupCodec.decode(withBom) is BackupDecodeResult.Success)
    }

    @Test
    fun blankFilesAreRejectedAsEmpty() {
        assertEquals(BackupFailureReason.EMPTY_FILE, decodeFailure("   \n ").reason)
        assertEquals(BackupFailureReason.EMPTY_FILE, decodeFailure("").reason)
    }

    @Test
    fun malformedJsonIsUnreadableNotCrashing() {
        assertEquals(BackupFailureReason.UNREADABLE, decodeFailure("{ not json").reason)
        assertEquals(BackupFailureReason.UNREADABLE, decodeFailure("[1,2,3]").reason)
    }

    @Test
    fun anUnknownEnvelopeKeyIsRejected() {
        val text = encodeBackup().replaceFirst("\"formatVersion\":", "\"extra\": true,\n  \"formatVersion\":")
        assertEquals(BackupFailureReason.UNREADABLE, decodeFailure(text).reason)
    }

    @Test
    fun anUnknownPayloadKeyIsRejected() {
        val text = encodeBackup().replaceFirst("\"recipients\":", "\"extra\": 1,\n    \"recipients\":")
        assertEquals(BackupFailureReason.UNREADABLE, decodeFailure(text).reason)
    }

    @Test
    fun aWrongTypeInThePayloadIsUnreadable() {
        // "pain" must be an integer; a string is a type error, not a value error.
        val text = encodeBackup().replaceFirst("\"pain\": 2", "\"pain\": \"two\"")
        assertEquals(BackupFailureReason.UNREADABLE, decodeFailure(text).reason)
    }

    @Test
    fun aDifferentFormatIdIsNotABackup() {
        val text = encodeBackup().replaceFirst(CareBackupCodec.FORMAT_ID, "some-other-app.backup")
        assertEquals(BackupFailureReason.NOT_A_BACKUP, decodeFailure(text).reason)
    }

    @Test
    fun aFutureFormatVersionIsRejectedWithItsNumber() {
        val failure = decodeFailure(futureVersionDocument(version = 2))
        assertEquals(BackupFailureReason.UNSUPPORTED_VERSION, failure.reason)
        assertEquals("2", failure.detail)
    }

    @Test
    fun aVersionBelowOneIsNotABackup() {
        assertEquals(BackupFailureReason.NOT_A_BACKUP, decodeFailure(futureVersionDocument(version = 0)).reason)
    }

    @Test
    fun aMissingIntegrityBlockIsRejected() {
        assertEquals(BackupFailureReason.MISSING_INTEGRITY, decodeFailure(documentWithoutIntegrity()).reason)
    }

    @Test
    fun anUnsupportedIntegrityAlgorithmIsRejected() {
        val text = documentWithoutIntegrity()
            .replaceFirst("\"createdAt\": \"2026-10-19T09:00:00Z\",", "\"createdAt\": \"2026-10-19T09:00:00Z\",\n  \"integrity\": { \"algorithm\": \"MD5\", \"digest\": \"00\" },")
        assertEquals(BackupFailureReason.UNSUPPORTED_INTEGRITY, decodeFailure(text).reason)
    }

    @Test
    fun aTamperedPayloadFailsTheChecksum() {
        val original = encodeBackup(backupData(notes = listOf(backupNote(text = "original"))))
        assertTrue(original.contains("\"original\""))
        val tampered = original.replace("\"original\"", "\"edited\"")
        assertEquals(BackupFailureReason.CHECKSUM_MISMATCH, decodeFailure(tampered).reason)
    }

    @Test
    fun theDigestCoversTheCanonicalPayloadJson() {
        // The documented contract: digest = SHA-256 of the compact canonical
        // JSON of `data`, independent of how the file itself is formatted.
        val data = backupData()
        val canonical = Json { encodeDefaults = true; explicitNulls = false }.encodeToString(data)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val digest = Regex("\"digest\": \"([0-9a-f]+)\"").find(encodeBackup(data))?.groupValues?.get(1)
        assertEquals(expected, digest)
    }

    @Test
    fun aCompactHandBuiltDocumentStillVerifies() {
        val data = backupData()
        val canonical = Json { encodeDefaults = true; explicitNulls = false }.encodeToString(data)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val compact = "{\"format\":\"${CareBackupCodec.FORMAT_ID}\"," +
            "\"formatVersion\":${CareBackupCodec.FORMAT_VERSION}," +
            "\"appName\":\"${CareBackupCodec.APP_NAME}\",\"appVersion\":\"test (1)\"," +
            "\"createdAt\":\"2026-10-19T09:00:00Z\"," +
            "\"integrity\":{\"algorithm\":\"SHA-256\",\"digest\":\"$digest\"}," +
            "\"data\":$canonical}"
        val decoded = CareBackupCodec.decode(compact)
        assertTrue(decoded is BackupDecodeResult.Success)
        assertEquals(data, (decoded as BackupDecodeResult.Success).backup.data)
    }

    @Test
    fun theDigestIsLowercaseHexSha256() {
        val text = encodeBackup()
        val digest = Regex("\"digest\": \"([0-9a-f]+)\"").find(text)?.groupValues?.get(1)
        assertTrue("digest must be lowercase hex SHA-256", digest != null && digest.length == 64 && digest.all { it in "0123456789abcdef" })
    }

    @Test
    fun aBackupNeverUsesTheOutboxAsAContainer() {
        val text = encodeBackup()
        assertFalse(text.contains("outbox"))
        assertFalse(text.contains("localId"))
    }

    /** A minimal document with an arbitrary format version; integrity never checked. */
    private fun futureVersionDocument(version: Int): String = """
        {
          "format": "${CareBackupCodec.FORMAT_ID}",
          "formatVersion": $version,
          "appName": "${CareBackupCodec.APP_NAME}",
          "appVersion": "9 (9)",
          "createdAt": "2026-10-19T09:00:00Z",
          "integrity": { "algorithm": "SHA-256", "digest": "00" },
          "data": { "recipients": [], "notes": [], "addenda": [], "plans": [] }
        }
    """.trimIndent()

    private fun documentWithoutIntegrity(): String = """
        {
          "format": "${CareBackupCodec.FORMAT_ID}",
          "formatVersion": ${CareBackupCodec.FORMAT_VERSION},
          "appName": "${CareBackupCodec.APP_NAME}",
          "appVersion": "9 (9)",
          "createdAt": "2026-10-19T09:00:00Z",
          "data": { "recipients": [], "notes": [], "addenda": [], "plans": [] }
        }
    """.trimIndent()
}
