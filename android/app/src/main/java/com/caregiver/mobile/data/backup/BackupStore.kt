package com.caregiver.mobile.data.backup

import androidx.room.withTransaction
import com.caregiver.mobile.data.local.AddendumEntity
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.MedicationEntity
import com.caregiver.mobile.data.local.MedicationIntakeEntity
import com.caregiver.mobile.data.local.NoteEntity
import com.caregiver.mobile.data.local.PlanEntity
import com.caregiver.mobile.data.local.RecipientEntity

/** Outcome of an atomic replace; [Conflict] means nothing was written. */
sealed interface BackupReplaceResult {
    data object Replaced : BackupReplaceResult

    data object Conflict : BackupReplaceResult
}

/**
 * The database seam for backup and restore.
 *
 * Only the standalone graph has an implementation: where Room is a server
 * cache (or absent), restoring would fight the sync contract — cached rows
 * would be re-uploaded, refreshed away, or duplicated — so the service is
 * created without a store and the UI disables both actions with an
 * explanation ([BackupRestoreService.available]).
 */
interface BackupStore {
    /** Reads every restorable record as one consistent snapshot. */
    suspend fun snapshot(): BackupData

    /** Current record counts; used to detect changes while the preview is open. */
    suspend fun counts(): BackupCounts

    /**
     * Replaces every restorable table inside one Room transaction.
     *
     * Refuses with [BackupReplaceResult.Conflict] when the current counts no
     * longer match [expected] (data changed after the preview was built), so a
     * concurrent change is never silently discarded. The outbox table is never
     * read or written here: pending sync operations are excluded from backups
     * and no restore can replay, delete, or duplicate them.
     */
    suspend fun replaceAll(data: BackupData, expected: BackupCounts): BackupReplaceResult
}

/**
 * Standalone storage: the same Room database the local API serves, so a
 * restored backup appears on every screen immediately.
 *
 * Replace semantics (documented and previewed before the user confirms):
 * recipients, notes, addenda, plans, medications and medication intakes are
 * cleared and re-inserted from the file in one transaction — all-or-nothing.
 * Ids are preserved (they are the relationship keys); row ids are not (the
 * file does not carry Room implementation details) but order is, so list and
 * same-day ordering are identical after a restore.
 */
class RoomBackupStore(private val db: AppDatabase) : BackupStore {

    override suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(
            recipients = db.recipientDao().all().map { it.toBackup() },
            notes = db.noteDao().query(null, null, null).map { it.toBackup() },
            addenda = db.addendumDao().all().map { it.toBackup() },
            plans = db.planDao().all().sortedBy { it.id }.map { it.toBackup() },
            medications = db.medicationDao().all().map { it.toBackup() },
            medicationIntakes = db.medicationDao().allIntakes().map { it.toBackup() },
        )
    }

    override suspend fun counts(): BackupCounts = readCounts()

    override suspend fun replaceAll(data: BackupData, expected: BackupCounts): BackupReplaceResult =
        db.withTransaction {
            if (readCounts() != expected) {
                return@withTransaction BackupReplaceResult.Conflict
            }
            db.medicationDao().deleteAllIntakes()
            db.medicationDao().deleteAll()
            db.addendumDao().deleteAll()
            db.planDao().deleteAll()
            db.noteDao().deleteAll()
            db.recipientDao().clear()
            db.recipientDao().insertImported(data.recipients.map { it.toEntity() })
            db.noteDao().insertImported(data.notes.map { it.toEntity() })
            db.addendumDao().insertImported(data.addenda.map { it.toEntity() })
            db.planDao().insertImported(data.plans.map { it.toEntity() })
            db.medicationDao().insertImported(data.medications.map { it.toEntity() })
            db.medicationDao().insertImportedIntakes(data.medicationIntakes.map { it.toEntity() })
            BackupReplaceResult.Replaced
        }

    private suspend fun readCounts(): BackupCounts = db.withTransaction {
        BackupCounts(
            recipients = db.recipientDao().all().size,
            notes = db.noteDao().query(null, null, null).size,
            addenda = db.addendumDao().all().size,
            plans = db.planDao().all().size,
            medications = db.medicationDao().all().size,
            intakes = db.medicationDao().allIntakes().size,
        )
    }
}

/**
 * Entity -> backup mappings. `localId` (the outbox sync mapping) is dropped in
 * both directions: it never belongs in a file, and a restored row is
 * unsynced-by-construction (the standalone store has no outbox).
 */
fun RecipientEntity.toBackup(): BackupRecipient =
    BackupRecipient(
        serverId = serverId,
        name = name,
        active = active,
        archivedAt = archivedAt,
    )

fun BackupRecipient.toEntity(): RecipientEntity =
    RecipientEntity(
        serverId = serverId,
        name = name,
        active = active,
        archivedAt = archivedAt,
    )

fun NoteEntity.toBackup(): BackupNote = BackupNote(
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

fun BackupNote.toEntity(): NoteEntity = NoteEntity(
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

fun AddendumEntity.toBackup(): BackupAddendum =
    BackupAddendum(id = id, noteId = noteId, createdAt = createdAt, text = text)

fun BackupAddendum.toEntity(): AddendumEntity =
    AddendumEntity(id = id, noteId = noteId, createdAt = createdAt, text = text)

fun PlanEntity.toBackup(): BackupPlan =
    BackupPlan(id = id, recipientId = recipientId, versionsJson = versionsJson, updatedAt = updatedAt)

fun BackupPlan.toEntity(): PlanEntity =
    PlanEntity(id = id, recipientId = recipientId, versionsJson = versionsJson, updatedAt = updatedAt)

fun MedicationEntity.toBackup(): BackupMedication =
    BackupMedication(
        id = id,
        recipientId = recipientId,
        name = name,
        dose = dose,
        schedule = schedule,
        active = active,
        createdAt = createdAt,
        dosesPerDay = dosesPerDay,
    )

fun BackupMedication.toEntity(): MedicationEntity =
    MedicationEntity(
        id = id,
        recipientId = recipientId,
        name = name,
        dose = dose,
        schedule = schedule,
        active = active,
        createdAt = createdAt,
        dosesPerDay = dosesPerDay,
    )

fun MedicationIntakeEntity.toBackup(): BackupMedicationIntake =
    BackupMedicationIntake(id = id, medicationId = medicationId, date = date, times = times)

fun BackupMedicationIntake.toEntity(): MedicationIntakeEntity =
    MedicationIntakeEntity(id = id, medicationId = medicationId, date = date, times = times)
