package com.caregiver.mobile.data.backup

import java.security.MessageDigest
import kotlinx.serialization.Serializable
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
 * The v1/v2 payload shape. Adding fields to [BackupData] changes its
 * canonical JSON even when the new lists are empty (`encodeDefaults`), so
 * old digests must be re-computed over exactly the fields the old writer
 * emitted.
 */
@Serializable
private data class LegacyBackupData(
    val recipients: List<BackupRecipient>,
    val notes: List<BackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
)

/**
 * Reads and writes the documented `.carebackup.json` shape.
 *
 * The envelope carries a stable `format` id and an integer `formatVersion`;
 * this codec writes [FORMAT_VERSION] (3), still reads v1/v2, and rejects newer
 * files with [BackupFailureReason.UNSUPPORTED_VERSION] (a future version must
 * be migrated deliberately — see the migration notes in the docs).
 *
 * Integrity: `integrity.digest` is the SHA-256 of the **canonical compact
 * JSON of `data`**, re-encoded with this app's serializer on both sides. It
 * catches corruption and hand-edits; it does not claim authenticity (anyone
 * can recompute a digest), so import safety relies on semantic validation and
 * the restore policy, never on the digest alone. v1/v2 files predate the
 * medication tables, so their digest covers only the legacy payload shape.
 *
 * The parser is strict (unknown keys are rejected) and never touches the
 * database; callers validate the payload before anything is written.
 */
object CareBackupCodec {
    const val FORMAT_ID = "caregiver-daily-notes.backup"
    const val FORMAT_VERSION = 3
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
        if (envelope.formatVersion !in 1..FORMAT_VERSION) {
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
        if (!expectedDigest(envelope.data, envelope.formatVersion)
                .equals(integrity.digest, ignoreCase = true)
        ) {
            return BackupDecodeResult.Failure(BackupFailure(BackupFailureReason.CHECKSUM_MISMATCH))
        }

        return BackupDecodeResult.Success(
            DecodedBackup(
                createdAt = envelope.createdAt,
                formatVersion = envelope.formatVersion,
                appVersion = envelope.appVersion,
                data = normalize(envelope.data, envelope.formatVersion),
            ),
        )
    }

    /**
     * v1 has no archive state and neither v1 nor v2 has medications. A
     * deliberate path (rather than relying only on the defaults) keeps an
     * older file's semantics even if a hand-edited document carries fields
     * the version never defined.
     */
    private fun normalize(data: BackupData, formatVersion: Int): BackupData = when {
        formatVersion == 1 -> data.copy(
            recipients = data.recipients.map { it.copy(archivedAt = null) },
            medications = emptyList(),
            medicationIntakes = emptyList(),
        )
        formatVersion < FORMAT_VERSION -> data.copy(
            medications = emptyList(),
            medicationIntakes = emptyList(),
        )
        else -> data
    }

    private fun expectedDigest(data: BackupData, formatVersion: Int): String =
        if (formatVersion >= FORMAT_VERSION) {
            digestOf(data)
        } else {
            legacyDigestOf(data)
        }

    private fun digestOf(data: BackupData): String =
        sha256(canonical.encodeToString(data))

    private fun legacyDigestOf(data: BackupData): String =
        sha256(
            canonical.encodeToString(
                LegacyBackupData(
                    recipients = data.recipients,
                    notes = data.notes,
                    addenda = data.addenda,
                    plans = data.plans,
                ),
            ),
        )

    private fun sha256(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
