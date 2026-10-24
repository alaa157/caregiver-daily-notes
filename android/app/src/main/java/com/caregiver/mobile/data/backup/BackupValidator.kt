package com.caregiver.mobile.data.backup

import com.caregiver.mobile.data.local.MedicationStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** What is wrong with a record; the UI reports counts, tests assert kinds. */
enum class BackupIssueKind {
    MISSING_ID,
    DUPLICATE_ID,
    MISSING_VALUE,
    INVALID_OPTION,
    INVALID_DATE,
    INVALID_PAIN,
    INVALID_COUNT,
    BROKEN_REFERENCE,
    INVALID_PLAN_JSON,
}

/** Which entity a validation issue belongs to. */
enum class BackupEntity { RECIPIENT, NOTE, ADDENDUM, PLAN, MEDICATION, MEDICATION_INTAKE }

/** One rejected record. Never carries note text or recipient names. */
data class BackupIssue(val entity: BackupEntity, val kind: BackupIssueKind)

/**
 * Semantic validation of a decoded, integrity-checked payload — deterministic
 * and read-only, so a rejected file leaves the database untouched.
 *
 * Rules follow the app's real model (the same contracts the CSV planner
 * enforces): ids must be present and unique per entity type, notes and plans
 * must reference an existing recipient, addenda must reference an existing
 * note, dates must be ISO values, pain is 0..10, mood must be non-empty, and
 * option groups respect the backend's 32-character columns. Everything else
 * (note text, names, Arabic content) is preserved verbatim, not restricted.
 */
object BackupValidator {
    /** The backend's option-column width (same cap the CSV import enforces). */
    private const val MAX_OPTION_LENGTH = 32

    /** Plan version JSON is small; the cap keeps a hostile file from deep recursion. */
    private const val MAX_PLAN_JSON_LENGTH = 200_000

    /** Enough to diagnose any real file; a hostile file cannot flood the report. */
    private const val MAX_ISSUES = 100

    private val json = Json

    fun validate(data: BackupData): List<BackupIssue> {
        val issues = mutableListOf<BackupIssue>()
        fun report(entity: BackupEntity, kind: BackupIssueKind) {
            if (issues.size < MAX_ISSUES) issues += BackupIssue(entity, kind)
        }

        val recipientIds = HashSet<String>()
        data.recipients.forEach { recipient ->
            if (recipient.serverId.isBlank()) {
                report(BackupEntity.RECIPIENT, BackupIssueKind.MISSING_ID)
            } else if (!recipientIds.add(recipient.serverId)) {
                report(BackupEntity.RECIPIENT, BackupIssueKind.DUPLICATE_ID)
            }
            if (recipient.name.isBlank()) {
                report(BackupEntity.RECIPIENT, BackupIssueKind.MISSING_VALUE)
            }
        }

        val noteIds = HashSet<String>()
        data.notes.forEach { note ->
            if (note.serverId.isBlank()) {
                report(BackupEntity.NOTE, BackupIssueKind.MISSING_ID)
            } else if (!noteIds.add(note.serverId)) {
                report(BackupEntity.NOTE, BackupIssueKind.DUPLICATE_ID)
            }
            if (note.recipientId.isBlank() || note.recipientId !in recipientIds) {
                report(BackupEntity.NOTE, BackupIssueKind.BROKEN_REFERENCE)
            }
            if (parseIsoDate(note.date) == null) {
                report(BackupEntity.NOTE, BackupIssueKind.INVALID_DATE)
            }
            if (note.mood.isBlank()) {
                report(BackupEntity.NOTE, BackupIssueKind.MISSING_VALUE)
            }
            val options = listOf(
                note.mood,
                note.appetite,
                note.sleep,
                note.mobility,
                note.medicationTaken,
            )
            if (options.any { it.length > MAX_OPTION_LENGTH }) {
                report(BackupEntity.NOTE, BackupIssueKind.INVALID_OPTION)
            }
            if (note.pain !in 0..10) {
                report(BackupEntity.NOTE, BackupIssueKind.INVALID_PAIN)
            }
        }

        val addendumIds = HashSet<String>()
        data.addenda.forEach { addendum ->
            if (addendum.id.isBlank()) {
                report(BackupEntity.ADDENDUM, BackupIssueKind.MISSING_ID)
            } else if (!addendumIds.add(addendum.id)) {
                report(BackupEntity.ADDENDUM, BackupIssueKind.DUPLICATE_ID)
            }
            if (addendum.noteId.isBlank() || addendum.noteId !in noteIds) {
                report(BackupEntity.ADDENDUM, BackupIssueKind.BROKEN_REFERENCE)
            }
            if (parseDateTime(addendum.createdAt) == null) {
                report(BackupEntity.ADDENDUM, BackupIssueKind.INVALID_DATE)
            }
        }

        val planIds = HashSet<String>()
        data.plans.forEach { plan ->
            if (plan.id.isBlank()) {
                report(BackupEntity.PLAN, BackupIssueKind.MISSING_ID)
            } else if (!planIds.add(plan.id)) {
                report(BackupEntity.PLAN, BackupIssueKind.DUPLICATE_ID)
            }
            if (plan.recipientId.isBlank() || plan.recipientId !in recipientIds) {
                report(BackupEntity.PLAN, BackupIssueKind.BROKEN_REFERENCE)
            }
            if (!isJsonArray(plan.versionsJson)) {
                report(BackupEntity.PLAN, BackupIssueKind.INVALID_PLAN_JSON)
            }
        }

        val medicationIds = HashSet<String>()
        data.medications.forEach { medication ->
            if (medication.id.isBlank()) {
                report(BackupEntity.MEDICATION, BackupIssueKind.MISSING_ID)
            } else if (!medicationIds.add(medication.id)) {
                report(BackupEntity.MEDICATION, BackupIssueKind.DUPLICATE_ID)
            }
            if (medication.recipientId.isBlank() || medication.recipientId !in recipientIds) {
                report(BackupEntity.MEDICATION, BackupIssueKind.BROKEN_REFERENCE)
            }
            if (medication.name.isBlank()) {
                report(BackupEntity.MEDICATION, BackupIssueKind.MISSING_VALUE)
            }
            if (medication.dosesPerDay !in 1..MedicationStore.MAX_DOSES_PER_DAY) {
                report(BackupEntity.MEDICATION, BackupIssueKind.INVALID_COUNT)
            }
        }

        val intakeIds = HashSet<String>()
        data.medicationIntakes.forEach { intake ->
            if (intake.id.isBlank()) {
                report(BackupEntity.MEDICATION_INTAKE, BackupIssueKind.MISSING_ID)
            } else if (!intakeIds.add(intake.id)) {
                report(BackupEntity.MEDICATION_INTAKE, BackupIssueKind.DUPLICATE_ID)
            }
            if (intake.medicationId.isBlank() || intake.medicationId !in medicationIds) {
                report(BackupEntity.MEDICATION_INTAKE, BackupIssueKind.BROKEN_REFERENCE)
            }
            if (parseIsoDate(intake.date) == null) {
                report(BackupEntity.MEDICATION_INTAKE, BackupIssueKind.INVALID_DATE)
            }
            if (intake.times < 1) {
                report(BackupEntity.MEDICATION_INTAKE, BackupIssueKind.INVALID_COUNT)
            }
        }

        return issues
    }

    private fun isJsonArray(text: String): Boolean {
        if (text.length > MAX_PLAN_JSON_LENGTH) return false
        return try {
            json.parseToJsonElement(text) is JsonArray
        } catch (e: Exception) {
            false
        }
    }

    private fun parseIsoDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }

    private fun parseDateTime(value: String): LocalDateTime? =
        try {
            LocalDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
}
