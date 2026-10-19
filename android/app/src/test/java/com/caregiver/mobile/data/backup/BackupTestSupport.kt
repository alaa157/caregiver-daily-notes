package com.caregiver.mobile.data.backup

/** A recipient fixture with valid defaults (an id notes can reference). */
fun backupRecipient(
    serverId: String = "r1",
    name: String = "Mona",
    active: Boolean = true,
): BackupRecipient = BackupRecipient(serverId = serverId, name = name, active = active)

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

/** A complete payload with one of each record; all lists can be overridden. */
fun backupData(
    recipients: List<BackupRecipient> = listOf(backupRecipient()),
    notes: List<BackupNote> = listOf(backupNote()),
    addenda: List<BackupAddendum> = listOf(backupAddendum()),
    plans: List<BackupPlan> = listOf(backupPlan()),
): BackupData = BackupData(recipients = recipients, notes = notes, addenda = addenda, plans = plans)

/** Encodes [data] the way the app writes a real file. */
fun encodeBackup(
    data: BackupData = backupData(),
    createdAt: String = "2026-10-19T09:00:00Z",
    appVersion: String = "test (1)",
): String = CareBackupCodec.encode(createdAt = createdAt, appVersion = appVersion, data = data)

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
    var throwOnCounts: Boolean = false,
) : BackupStore {
    val replaced = mutableListOf<BackupData>()
    var countsCalls: Int = 0

    override suspend fun snapshot(): BackupData = data

    override suspend fun counts(): BackupCounts {
        countsCalls += 1
        if (throwOnCounts) throw IllegalStateException("store unavailable")
        return data.counts
    }

    override suspend fun replaceAll(data: BackupData, expected: BackupCounts): BackupReplaceResult {
        if (throwOnReplace) throw IllegalStateException("store unavailable")
        if (conflict) return BackupReplaceResult.Conflict
        replaced += data
        this.data = data
        return BackupReplaceResult.Replaced
    }
}
