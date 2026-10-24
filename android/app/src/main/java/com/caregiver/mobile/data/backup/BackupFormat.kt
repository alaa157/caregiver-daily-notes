package com.caregiver.mobile.data.backup

import kotlinx.serialization.Serializable

/**
 * The versioned backup payload (`.carebackup.json`, schema v2 — documented in
 * `docs/android/backup-restore.md`). Version 2 adds recipient archive state;
 * v1 files still decode and restore as fully unarchived.
 *
 * Room surrogate keys (`rowId`) are deliberately absent: list order is the
 * contract that preserves them. Recipients keep their list order, and notes
 * are written in the order the app reads them (`date DESC, rowId`), so
 * same-day ordering survives a restore. Sync mappings (`localId`), outbox
 * rows, AI keys, settings and tokens never appear in a backup.
 */
@Serializable
data class BackupData(
    val recipients: List<BackupRecipient>,
    val notes: List<BackupNote>,
    val addenda: List<BackupAddendum>,
    val plans: List<BackupPlan>,
) {
    val counts: BackupCounts
        get() = BackupCounts(
            recipients = recipients.size,
            notes = notes.size,
            addenda = addenda.size,
            plans = plans.size,
        )
}

/**
 * A recipient as stored locally; [serverId] is the stable id notes reference.
 * [archivedAt] is absent from v1 files and defaults to unarchived.
 */
@Serializable
data class BackupRecipient(
    val serverId: String,
    val name: String,
    val active: Boolean,
    val archivedAt: Long? = null,
)

/** A daily note; values mirror the local entity, minus the outbox sync mapping. */
@Serializable
data class BackupNote(
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

/** An addendum; [noteId] references [BackupNote.serverId]. */
@Serializable
data class BackupAddendum(
    val id: String,
    val noteId: String,
    val createdAt: String,
    val text: String,
)

/** A care plan; [versionsJson] is the plan's own version list, kept verbatim. */
@Serializable
data class BackupPlan(
    val id: String,
    val recipientId: String,
    val versionsJson: String,
    val updatedAt: Long,
)

/**
 * The document envelope: stable identifiers, provenance and the payload.
 * [integrity] is nullable only so a missing block fails with its own clear
 * error instead of a generic parse error; files this app writes always carry it.
 */
@Serializable
data class BackupEnvelope(
    val format: String,
    val formatVersion: Int,
    val appName: String,
    val appVersion: String,
    val createdAt: String,
    val integrity: BackupIntegrity? = null,
    val data: BackupData,
)

/**
 * Digest over the canonical compact JSON of [BackupEnvelope.data]. It detects
 * corruption and accidental edits, not forgery (the algorithm is public).
 */
@Serializable
data class BackupIntegrity(
    val algorithm: String,
    val digest: String,
)

/** Record counts per entity type; what the preview and result notices report. */
data class BackupCounts(
    val recipients: Int,
    val notes: Int,
    val addenda: Int,
    val plans: Int,
) {
    val total: Int get() = recipients + notes + addenda + plans
}
