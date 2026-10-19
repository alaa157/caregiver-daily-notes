package com.caregiver.mobile.data.backup

import java.security.MessageDigest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Every way a backup or restore can end in a user-visible error. The UI maps
 * the reason to localized copy; note text and recipient details never travel
 * through this type.
 */
enum class BackupFailureReason {
    EMPTY_FILE,
    READ_FAILED,
    TOO_LARGE,
    WRITE_FAILED,
    UNREADABLE,
    NOT_A_BACKUP,
    UNSUPPORTED_VERSION,
    MISSING_INTEGRITY,
    UNSUPPORTED_INTEGRITY,
    CHECKSUM_MISMATCH,
    INVALID_DATA,
    SOURCE_FAILED,
    RESTORE_FAILED,
    RESTORE_CONFLICT,
    UNAVAILABLE,
    UNKNOWN,
}

/**
 * One user-visible failure. [detail] carries technical context only (a format
 * version number) — never note text, names or file content.
 */
data class BackupFailure(
    val reason: BackupFailureReason,
    val detail: String? = null,
    /** Invalid records counted during validation ([BackupFailureReason.INVALID_DATA]). */
    val issueCount: Int = 0,
)

/** Result of parsing and integrity-checking a backup document. */
sealed interface BackupDecodeResult {
    data class Success(val backup: DecodedBackup) : BackupDecodeResult

    data class Failure(val failure: BackupFailure) : BackupDecodeResult
}

/** A structurally valid, integrity-checked document; not yet domain-validated. */
data class DecodedBackup(
    val createdAt: String,
    val formatVersion: Int,
    val appVersion: String,
    val data: BackupData,
)

/**
 * Reads and writes the documented `.carebackup.json` shape.
 *
 * The envelope carries a stable `format` id and an integer `formatVersion`;
 * this codec accepts exactly version [FORMAT_VERSION] and rejects newer files
 * with [BackupFailureReason.UNSUPPORTED_VERSION] (a future version must be
 * migrated deliberately — see the migration notes in the docs).
 *
 * Integrity: `integrity.digest` is the SHA-256 of the **canonical compact
 * JSON of `data`**, re-encoded with this app's serializer on both sides. It
 * catches corruption and hand-edits; it does not claim authenticity (anyone
 * can recompute a digest), so import safety relies on semantic validation and
 * the restore policy, never on the digest alone.
 *
 * The parser is strict (unknown keys are rejected) and never touches the
 * database; callers validate the payload before anything is written.
 */
object CareBackupCodec {
    const val FORMAT_ID = "caregiver-daily-notes.backup"
    const val FORMAT_VERSION = 1
    const val APP_NAME = "caregiver-daily-notes"
    const val INTEGRITY_ALGORITHM = "SHA-256"

    /** Canonical encoder: deterministic field order, compact, used for digests. */
    private val canonical = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    /** Human-readable writer for the file itself (the digest is whitespace-independent). */
    private val pretty = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(createdAt: String, appVersion: String, data: BackupData): String {
        val envelope = BackupEnvelope(
            format = FORMAT_ID,
            formatVersion = FORMAT_VERSION,
            appName = APP_NAME,
            appVersion = appVersion,
            createdAt = createdAt,
            integrity = BackupIntegrity(INTEGRITY_ALGORITHM, digestOf(data)),
            data = data,
        )
        return pretty.encodeToString(envelope)
    }

    fun decode(text: String): BackupDecodeResult {
        // Editors sometimes prepend a UTF-8 BOM; it is not part of JSON.
        val body = text.removePrefix("\uFEFF")
        if (body.isBlank()) {
            return BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.EMPTY_FILE))
        }

        val envelope = try {
            canonical.decodeFromString<BackupEnvelope>(body)
        } catch (e: SerializationException) {
            return BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.UNREADABLE))
        } catch (e: IllegalArgumentException) {
            return BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.UNREADABLE))
        }

        if (envelope.format != FORMAT_ID) {
            return BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.NOT_A_BACKUP))
        }
        if (envelope.formatVersion != FORMAT_VERSION) {
            return if (envelope.formatVersion > FORMAT_VERSION) {
                BackupDecodeResult.Failure(
                    BackupFailure(
                        BackupFailureReason.UNSUPPORTED_VERSION,
                        detail = envelope.formatVersion.toString(),
                    ),
                )
            } else {
                BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.NOT_A_BACKUP))
            }
        }

        val integrity = envelope.integrity
            ?: return BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.MISSING_INTEGRITY))
        if (!integrity.algorithm.equals(INTEGRITY_ALGORITHM, ignoreCase = true)) {
            return BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.UNSUPPORTED_INTEGRITY))
        }
        if (!digestOf(envelope.data).equals(integrity.digest, ignoreCase = true)) {
            return BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.CHECKSUM_MISMATCH))
        }

        return BackupDecodeResult.Success(
            DecodedBackup(
                createdAt = envelope.createdAt,
                formatVersion = envelope.formatVersion,
                appVersion = envelope.appVersion,
                data = envelope.data,
            ),
        )
    }

    private fun digestOf(data: BackupData): String {
        val bytes = canonical.encodeToString(data).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
