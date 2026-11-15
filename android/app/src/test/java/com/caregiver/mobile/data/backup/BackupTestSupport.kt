package com.caregiver.mobile.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A recipient fixture with valid defaults (an id notes can reference). */
fun backupRecipient(
    serverId: String = "r1",
    name: String = "Mona",
    active: Boolean = true,
    archivedAt: Long? = null,
    relationship: String? = null,
    dateOfBirth: String? = null,
    allergies: String? = null,
    conditions: String? = null,
    emergencyContactName: String? = null,
    emergencyContactPhone: String? = null,
): BackupRecipient = BackupRecipient(
    serverId = serverId,
    name = name,
    active = active,
    archivedAt = archivedAt,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    allergies = allergies,
    conditions = conditions,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone,
)

/** A note fixture with valid defaults. */
fun backupNote(
    serverId: String = "n1",
    recipientId: String = "r1",
    date: String = "2026-10-01",
    mood: String = "good",
    appetite: String = "good",
    sleep: String = "ok",
    mobility: String = "walks",
    medicationTaken: String = "taken",
    pain: Int = 2,
    fall: Boolean = false,
    text: String = "note",
    painRecorded: Boolean = true,
    fallRecorded: Boolean = true,
): BackupNote = BackupNote(
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
    painRecorded = painRecorded,
    fallRecorded = fallRecorded,
)

/** An addendum fixture referencing [noteId]; defaults to [backupNote]'s id. */
fun backupAddendum(
    id: String = "a1",
    noteId: String = "n1",
    createdAt: String = "2026-10-02T10:15:30",
    text: String = "addendum",
): BackupAddendum = BackupAddendum(id = id, noteId = noteId, createdAt = createdAt, text = text)

/** A plan fixture referencing [recipientId]; the version list is a JSON array. */
fun backupPlan(
    id: String = "p1",
    recipientId: String = "r1",
    versionsJson: String = """[{"version":1,"status":"Suggested","items":[]}]""",
    updatedAt: Long = 1_700_000_000_000,
): BackupPlan = BackupPlan(
    id = id,
    recipientId = recipientId,
    versionsJson = versionsJson,
    updatedAt = updatedAt,
)

/** A medication fixture referencing [recipientId]. */
fun backupMedication(
    id: String = "m1",
    recipientId: String = "r1",
    name: String = "Amlodipine",
    dose: String? = "5 mg",
    schedule: String? = "morning",
    active: Boolean = true,
    createdAt: Long = 1_700_000_000_000,
    dosesPerDay: Int = 1,
): BackupMedication = BackupMedication(
    id = id,
    recipientId = recipientId,
    name = name,
    dose = dose,
    schedule = schedule,
    active = active,
    createdAt = createdAt,
    dosesPerDay = dosesPerDay,
)

/** A taken dose for [medicationId] with the local key format. */
fun backupIntake(
    medicationId: String = "m1",
    date: String = "2026-10-01",
    doseIndex: Int = 0,
    times: Int = 1,
): BackupMedicationIntake = BackupMedicationIntake(
    id = "$medicationId:$date:$doseIndex",
    medicationId = medicationId,
    date = date,
    times = times,
)

/** A saved summary fixture referencing [recipientId] with a valid payload. */
fun backupSavedSummary(
    id: String = "s1",
    recipientId: String = "r1",
    periodDays: Int = 7,
    periodStart: String = "2026-09-24",
    periodEnd: String = "2026-09-30",
    createdAt: Long = 1_700_000_000_000,
    aiGenerated: Boolean = true,
    provider: String? = "openrouter",
    model: String? = "openai/gpt-4o-mini",
    payload: String = """{"summary":{"text":"ok"},"trends":[]}""",
): BackupSavedSummary = BackupSavedSummary(
    id = id,
    recipientId = recipientId,
    periodDays = periodDays,
    periodStart = periodStart,
    periodEnd = periodEnd,
    createdAt = createdAt,
    aiGenerated = aiGenerated,
    provider = provider,
    model = model,
    payload = payload,
)

/** A complete payload with one of each record; all lists can be overridden. */
fun backupData(
    recipients: List<BackupRecipient> = listOf(backupRecipient()),
    notes: List<BackupNote> = listOf(backupNote()),
    addenda: List<BackupAddendum> = listOf(backupAddendum()),
    plans: List<BackupPlan> = listOf(backupPlan()),
    medications: List<BackupMedication> = emptyList(),
    medicationIntakes: List<BackupMedicationIntake> = emptyList(),
    savedSummaries: List<BackupSavedSummary> = emptyList(),
): BackupData = BackupData(
    recipients = recipients,
    notes = notes,
    addenda = addenda,
    plans = plans,
    medications = medications,
    medicationIntakes = medicationIntakes,
    savedSummaries = savedSummaries,
)

/** Encodes [data] the way the app writes a real file. */
fun encodeBackup(
    data: BackupData = backupData(),
    createdAt: String = "2026-10-19T09:00:00Z",
    appVersion: String = "test (1)",
): String = CareBackupCodec.encode(createdAt = createdAt, appVersion = appVersion, data = data)

/**
 * The note shape v1–v3 writers emitted: no recorded flags. Older digests
 * must be computed over exactly this shape.
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

/**
 * The canonical payload a v1/v2 writer produced: only recipients, notes,
 * addenda and plans. Used to build hand-made older documents whose digest the
 * current codec still verifies.
 */
@Serializable
private data class LegacyBackupRecipient(
    val serverId: String,
    val name: String,
    val active: Boolean,
    val archivedAt: Long? = null,
)

private fun BackupRecipient.toLegacyRecipient(): LegacyBackupRecipient = LegacyBackupRecipient(
    serverId = serverId,
    name = name,
    active = active,
    archivedAt = archivedAt,
)

@Serializable
private data class LegacyPayload(
    val recipients: List<LegacyBackupRecipient>,
    val notes: List<LegacyBackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
)

fun legacyCanonicalJson(data: BackupData): String =
    Json { encodeDefaults = true; explicitNulls = false }.encodeToString(
        LegacyPayload(
            recipients = data.recipients.map { it.toLegacyRecipient() },
            notes = data.notes.map { it.toLegacyNote() },
            addenda = data.addenda,
            plans = data.plans,
        ),
    )

/**
 * The canonical payload a v3 writer produced: the four legacy lists plus
 * the medication tables, and notes without the recorded flags.
 */
@Serializable
private data class V3Payload(
    val recipients: List<LegacyBackupRecipient>,
    val notes: List<LegacyBackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
    val medications: List<BackupMedication> = emptyList(),
    val medicationIntakes: List<BackupMedicationIntake> = emptyList(),
)

fun v3CanonicalJson(data: BackupData): String =
    Json { encodeDefaults = true; explicitNulls = false }.encodeToString(
        V3Payload(
            recipients = data.recipients.map { it.toLegacyRecipient() },
            notes = data.notes.map { it.toLegacyNote() },
            addenda = data.addenda,
            plans = data.plans,
            medications = data.medications,
            medicationIntakes = data.medicationIntakes,
        ),
    )

/** The canonical payload v4 writers produced, before recipient care details. */
@Serializable
private data class V4Payload(
    val recipients: List<LegacyBackupRecipient>,
    val notes: List<BackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
    val medications: List<BackupMedication> = emptyList(),
    val medicationIntakes: List<BackupMedicationIntake> = emptyList(),
)

fun v4CanonicalJson(data: BackupData): String =
    Json { encodeDefaults = true; explicitNulls = false }.encodeToString(
        V4Payload(
            recipients = data.recipients.map { it.toLegacyRecipient() },
            notes = data.notes,
            addenda = data.addenda,
            plans = data.plans,
            medications = data.medications,
            medicationIntakes = data.medicationIntakes,
        ),
    )

/**
 * The canonical payload a v5 writer produced: the full pre-v6 shape with
 * care details but no saved summaries. Used to build hand-made v5
 * documents whose digest the current codec still verifies.
 */
@Serializable
private data class V5Payload(
    val recipients: List<BackupRecipient>,
    val notes: List<BackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
    val medications: List<BackupMedication> = emptyList(),
    val medicationIntakes: List<BackupMedicationIntake> = emptyList(),
)

fun v5CanonicalJson(data: BackupData): String =
    Json { encodeDefaults = true; explicitNulls = false }.encodeToString(
        V5Payload(
            recipients = data.recipients,
            notes = data.notes,
            addenda = data.addenda,
            plans = data.plans,
            medications = data.medications,
            medicationIntakes = data.medicationIntakes,
        ),
    )

fun sha256(text: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Convenience: the decode failure a text produces, or a failed assertion. */
fun decodeFailure(text: String): BackupFailure =
    when (val result = CareBackupCodec.decode(text)) {
        is BackupDecodeResult.Failure -> result.failure
        is BackupDecodeResult.Success -> error("expected a decode failure, got ${result.backup}")
    }

/**
 * In-memory [BackupStore] for service/ViewModel tests. Records exactly what a
 * commit would write and lets a test force a conflict or failure.
 */
class FakeBackupStore(
    var data: BackupData = backupData(),
    var conflict: Boolean = false,
    var throwOnReplace: Boolean = false,
    var throwOnState: Boolean = false,
) : BackupStore {
    val replaced = mutableListOf<BackupData>()
    var stateCalls: Int = 0

    override suspend fun snapshot(): BackupData = data

    override suspend fun state(): BackupState {
        stateCalls += 1
        if (throwOnState) throw IllegalStateException("store unavailable")
        return BackupState(data.counts, CareBackupCodec.stateDigest(data))
    }

    override suspend fun replaceAll(data: BackupData, expected: BackupState): BackupReplaceResult {
        if (throwOnReplace) throw IllegalStateException("store unavailable")
        if (conflict) return BackupReplaceResult.Conflict
        replaced += data
        this.data = data
        return BackupReplaceResult.Replaced
    }
}
