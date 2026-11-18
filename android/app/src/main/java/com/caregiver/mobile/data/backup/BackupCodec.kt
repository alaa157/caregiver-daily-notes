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
 * The note shape before the recorded flags (v1–v3). Digest verification
 * re-encodes older payloads with exactly the fields those writers emitted;
 * adding fields to [BackupNote] changes its canonical JSON even when the
 * new values are defaults (`encodeDefaults`), so old digests must not see
 * them.
 */
@Serializable
private data class LegacyBackupNote(
    val serverId: String,
    val recipientId: String,
    val date: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
)

@Serializable
private data class LegacyBackupRecipient(
    val serverId: String,
    val name: String,
    val active: Boolean,
    val archivedAt: Long? = null,
)

/**
 * The v1/v2 payload shape: no archive provenance handling beyond defaults,
 * no medication tables, no recorded flags.
 */
@Serializable
private data class LegacyBackupData(
    val recipients: List<LegacyBackupRecipient>,
    val notes: List<LegacyBackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
)

/**
 * The v3 payload shape: medication tables present, recorded flags absent.
 */
@Serializable
private data class BackupDataV3(
    val recipients: List<LegacyBackupRecipient>,
    val notes: List<LegacyBackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
    val medications: List<BackupMedication> = emptyList(),
    val medicationIntakes: List<BackupMedicationIntake> = emptyList(),
)

/** The v4 payload shape: note flags exist, but recipient details do not. */
@Serializable
private data class BackupDataV4(
    val recipients: List<LegacyBackupRecipient>,
    val notes: List<BackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
    val medications: List<BackupMedication> = emptyList(),
    val medicationIntakes: List<BackupMedicationIntake> = emptyList(),
)

/** The v5 payload shape: care details exist, saved summaries do not. */
@Serializable
private data class BackupDataV5(
    val recipients: List<BackupRecipient>,
    val notes: List<BackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
    val medications: List<BackupMedication> = emptyList(),
    val medicationIntakes: List<BackupMedicationIntake> = emptyList(),
)

/**
 * Reads and writes the documented `.carebackup.json` shape.
 *
 * The envelope carries a stable `format` id and an integer `formatVersion`;
 * this codec writes [FORMAT_VERSION] (6), still reads v1–v5, and rejects
 * newer files with [BackupFailureReason.UNSUPPORTED_VERSION] (a future
 * version must be migrated deliberately — see the migration notes in the
 * docs).
 *
 * Integrity: `integrity.digest` is the SHA-256 of the **canonical compact
 * JSON of `data`**, re-encoded with this app's serializer on both sides. It
 * catches corruption and hand-edits; it does not claim authenticity (anyone
 * can recompute a digest), so import safety relies on semantic validation and
 * the restore policy, never on the digest alone. v1/v2 files predate the
 * medication tables, v1–v3 files predate the recorded flags, v1–v4
 * files predate recipient care details, and v1–v5 files predate saved
 * summaries, so their digests cover only the
 * payload shapes those versions wrote.
 *
 * The parser is strict (unknown keys are rejected) and never touches the
 * database; callers validate the payload before anything is written.
 */
object CareBackupCodec {
    const val FORMAT_ID = "caregiver-daily-notes.backup"
    const val FORMAT_VERSION = 6
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

    /**
     * Content digest of [data] using the same canonical encoding as the file
     * integrity check. Backup restore uses it to detect any change between
     * preview and confirmation, including an in-place edit that leaves every
     * row count unchanged.
     */
    fun stateDigest(data: BackupData): String = digestOf(data)

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
     * v1 has no archive state, neither v1 nor v2 has medications, versions
     * before 4 have no recorded flags, versions before 5 have no care
     * details, and versions before 6 have no saved summaries. A deliberate
     * path (rather
     * than relying only on the defaults) keeps an older file's semantics
     * even if a hand-edited document carries fields the version never
     * defined.
     */
    private fun normalize(data: BackupData, formatVersion: Int): BackupData {
        val withRecordedFlags = if (formatVersion < 4) {
            data.copy(
                notes = data.notes.map {
                    it.copy(painRecorded = true, fallRecorded = true)
                },
            )
        } else {
            data
        }
        val base = if (formatVersion < 5) {
            withRecordedFlags.copy(
                recipients = withRecordedFlags.recipients.map {
                    it.copy(
                        relationship = null,
                        dateOfBirth = null,
                        allergies = null,
                        conditions = null,
                        emergencyContactName = null,
                        emergencyContactPhone = null,
                    )
                },
            )
        } else {
            withRecordedFlags
        }
        val withoutSummaries = if (formatVersion < 6) {
            base.copy(savedSummaries = emptyList())
        } else {
            base
        }
        return when {
            formatVersion == 1 -> withoutSummaries.copy(
                recipients = withoutSummaries.recipients.map { it.copy(archivedAt = null) },
                medications = emptyList(),
                medicationIntakes = emptyList(),
            )
            formatVersion == 2 -> withoutSummaries.copy(
                medications = emptyList(),
                medicationIntakes = emptyList(),
            )
            else -> withoutSummaries
        }
    }

    private fun expectedDigest(data: BackupData, formatVersion: Int): String = when {
        formatVersion >= FORMAT_VERSION -> digestOf(data)
        formatVersion == 5 -> digestOfV5(data)
        formatVersion == 4 -> digestOfV4(data)
        formatVersion == 3 -> digestOfV3(data)
        else -> legacyDigestOf(data)
    }

    private fun digestOf(data: BackupData): String =
        sha256(canonical.encodeToString(data))

    private fun digestOfV5(data: BackupData): String =
        sha256(
            canonical.encodeToString(
                BackupDataV5(
                    recipients = data.recipients,
                    notes = data.notes,
                    addenda = data.addenda,
                    plans = data.plans,
                    medications = data.medications,
                    medicationIntakes = data.medicationIntakes,
                ),
            ),
        )

    private fun digestOfV4(data: BackupData): String =
        sha256(
            canonical.encodeToString(
                BackupDataV4(
                    recipients = data.recipients.map { it.toLegacyRecipient() },
                    notes = data.notes,
                    addenda = data.addenda,
                    plans = data.plans,
                    medications = data.medications,
                    medicationIntakes = data.medicationIntakes,
                ),
            ),
        )

    private fun digestOfV3(data: BackupData): String =
        sha256(
            canonical.encodeToString(
                BackupDataV3(
                    recipients = data.recipients.map { it.toLegacyRecipient() },
                    notes = data.notes.map { it.toLegacyNote() },
                    addenda = data.addenda,
                    plans = data.plans,
                    medications = data.medications,
                    medicationIntakes = data.medicationIntakes,
                ),
            ),
        )

    private fun legacyDigestOf(data: BackupData): String =
        sha256(
            canonical.encodeToString(
                LegacyBackupData(
                    recipients = data.recipients.map { it.toLegacyRecipient() },
                    notes = data.notes.map { it.toLegacyNote() },
                    addenda = data.addenda,
                    plans = data.plans,
                ),
            ),
        )

    private fun BackupNote.toLegacyNote(): LegacyBackupNote = LegacyBackupNote(
        serverId = serverId,
        recipientId = recipientId,
        date = date,
        mood = mood,
        appetite = appetite,
        sleep = sleep,
        mobility = mobility,
        medicationTaken = medicationTaken,
        pain = pain,
        fall = fall,
        text = text,
    )

    private fun BackupRecipient.toLegacyRecipient(): LegacyBackupRecipient = LegacyBackupRecipient(
        serverId = serverId,
        name = name,
        active = active,
        archivedAt = archivedAt,
    )

    private fun sha256(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
