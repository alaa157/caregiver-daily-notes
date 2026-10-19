package com.caregiver.mobile.data.csv

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/** An existing recipient the import can attach notes to (stable id + display name). */
data class RecipientRef(val id: String, val name: String, val active: Boolean)

/**
 * The (recipientId, date) pair the domain treats as unique: the backend
 * rejects a second note for the same recipient and day with a 422, and the
 * standalone store follows the same product rule.
 */
data class ExistingNoteKey(val recipientId: String, val date: String)

/**
 * Where an [ImportPlan] is allowed to write. A Room-backed (standalone) store
 * preserves the date from the CSV; a server-backed store cannot, because
 * `POST /api/notes` assigns `LocalDate.now()` server-side.
 */
data class ImportPolicy(val preservesDates: Boolean, val today: String)

/** Why a row will not be imported; the UI maps these to localized copy. */
enum class ImportIssue {
    WRONG_FIELD_COUNT,
    MISSING_DATE,
    INVALID_DATE,
    DATE_NOT_SUPPORTED,
    INVALID_PAIN,
    INVALID_FALL,
    MISSING_MOOD,
    VALUE_TOO_LONG,
    EMPTY_TEXT,
    MISSING_RECIPIENT,
    UNMATCHED_RECIPIENT,
    DUPLICATE,
    UNKNOWN,
}

/** A rejected/skipped/duplicate CSV row. [column] is a stable header name, not note content. */
data class RejectedRow(val line: Int, val issue: ImportIssue, val column: String? = null)

/** A validated row ready to be written; not persisted until the user confirms. */
data class PlannedNote(
    val line: Int,
    val date: String,
    val recipientId: String,
    val mood: String,
    val appetite: String,
    val sleep: String,
    val mobility: String,
    val medicationTaken: String,
    val pain: Int,
    val fall: Boolean,
    val text: String,
)

/** A recipient identifier in the file that matched nothing, plus how many rows it blocks. */
data class UnmatchedRecipient(
    val key: String,
    val identifier: String,
    val byId: Boolean,
    val rowCount: Int,
)

/**
 * The complete, reviewable import plan. Nothing is written until the caller
 * commits it, and [withResolutions] re-plans with user-selected recipient
 * mappings so the preview counts always match what a commit would do.
 *
 * Duplicate policy: a row is a duplicate when a note already exists for the
 * same (recipient, date), or when an earlier valid row in the same file
 * already claimed that pair. Duplicates are always skipped — existing notes
 * are never overwritten.
 */
data class ImportPlan(
    val rows: List<CareNoteCsvRow>,
    val recipients: List<RecipientRef>,
    val existing: Set<ExistingNoteKey>,
    val policy: ImportPolicy,
    val extraHeaders: List<String>,
    val overrides: Map<String, String>,
    val valid: List<PlannedNote>,
    val duplicates: List<RejectedRow>,
    val invalid: List<RejectedRow>,
    val unmatched: List<UnmatchedRecipient>,
    val skipped: List<RejectedRow>,
) {
    val totalRows: Int get() = rows.size

    /** Recomputes the plan with an extra map of `unmatched key -> recipient id`. */
    fun withResolutions(resolutions: Map<String, String>): ImportPlan =
        CsvImportPlanner.plan(
            rows = rows,
            recipients = recipients,
            existing = existing,
            policy = policy,
            extraHeaders = extraHeaders,
            overrides = resolutions,
        )
}

/**
 * Validates decoded CSV rows against the app's real note model and builds an
 * [ImportPlan].
 *
 * Recipient matching rules:
 * - a non-empty `recipient_id` is matched against the stable ids first;
 * - a non-empty `recipient_name` is matched case-insensitively only when the
 *   id column is empty, and only when exactly one recipient has that name;
 * - anything else becomes an [UnmatchedRecipient] that the user must map to
 *   an existing recipient or leave skipped. No recipient is ever invented and
 *   a note is never attached to a different person silently.
 *
 * Required fields follow the editor/backend contract: mood and note text must
 * be non-empty and pain must be 0..10. Optional option groups may be empty and
 * are limited to the backend's 32-character columns.
 */
object CsvImportPlanner {
    fun plan(
        rows: List<CareNoteCsvRow>,
        recipients: List<RecipientRef>,
        existing: Set<ExistingNoteKey>,
        policy: ImportPolicy,
        extraHeaders: List<String> = emptyList(),
        overrides: Map<String, String> = emptyMap(),
    ): ImportPlan {
        val byId = recipients.associateBy { it.id }
        val byName = recipients.groupBy { nameKey(it.name) }
        val seen = existing.toMutableSet()

        val valid = mutableListOf<PlannedNote>()
        val duplicates = mutableListOf<RejectedRow>()
        val invalid = mutableListOf<RejectedRow>()
        val skipped = mutableListOf<RejectedRow>()
        val unmatched = linkedMapOf<String, UnmatchedAccumulator>()

        rows.forEach { row ->
            fun reject(issue: ImportIssue, column: String? = null) {
                invalid += RejectedRow(row.line, issue, column)
            }

            if (row.fieldCount != row.expectedFieldCount) {
                reject(ImportIssue.WRONG_FIELD_COUNT)
                return@forEach
            }

            val date = row.values[CareNoteCsv.COL_DATE].orEmpty().trim()
            if (date.isEmpty()) {
                reject(ImportIssue.MISSING_DATE, CareNoteCsv.COL_DATE)
                return@forEach
            }
            if (parseIsoDate(date) == null) {
                reject(ImportIssue.INVALID_DATE, CareNoteCsv.COL_DATE)
                return@forEach
            }
            if (!policy.preservesDates && date != policy.today) {
                reject(ImportIssue.DATE_NOT_SUPPORTED, CareNoteCsv.COL_DATE)
                return@forEach
            }

            val pain = row.values[CareNoteCsv.COL_PAIN].orEmpty().trim().toIntOrNull()
            if (pain == null || pain !in 0..10) {
                reject(ImportIssue.INVALID_PAIN, CareNoteCsv.COL_PAIN)
                return@forEach
            }

            val fall = CareNoteCsv.booleanOf(row.values[CareNoteCsv.COL_FALL].orEmpty())
            if (fall == null) {
                reject(ImportIssue.INVALID_FALL, CareNoteCsv.COL_FALL)
                return@forEach
            }

            val mood = row.values[CareNoteCsv.COL_MOOD].orEmpty().trim()
            if (mood.isEmpty()) {
                reject(ImportIssue.MISSING_MOOD, CareNoteCsv.COL_MOOD)
                return@forEach
            }

            val optionColumns = listOf(
                CareNoteCsv.COL_MOOD,
                CareNoteCsv.COL_APPETITE,
                CareNoteCsv.COL_SLEEP,
                CareNoteCsv.COL_MOBILITY,
                CareNoteCsv.COL_MEDICATION_TAKEN,
            )
            val tooLong = optionColumns.firstOrNull { column ->
                row.values[column].orEmpty().trim().length > MAX_OPTION_LENGTH
            }
            if (tooLong != null) {
                reject(ImportIssue.VALUE_TOO_LONG, tooLong)
                return@forEach
            }

            val text = row.values[CareNoteCsv.COL_NOTE_TEXT].orEmpty()
            if (text.isBlank()) {
                reject(ImportIssue.EMPTY_TEXT, CareNoteCsv.COL_NOTE_TEXT)
                return@forEach
            }

            val rawId = row.values[CareNoteCsv.COL_RECIPIENT_ID].orEmpty().trim()
            val rawName = row.values[CareNoteCsv.COL_RECIPIENT_NAME].orEmpty().trim()

            val recipientId: String?
            val unmatchedKey: UnmatchedKey?
            when {
                rawId.isNotEmpty() -> {
                    val match = byId[rawId]
                    if (match != null) {
                        recipientId = match.id
                        unmatchedKey = null
                    } else {
                        val key = idKey(rawId)
                        val override = overrides[key]
                        if (override != null && byId.containsKey(override)) {
                            recipientId = override
                            unmatchedKey = null
                        } else {
                            recipientId = null
                            unmatchedKey = UnmatchedKey(key, rawId, byId = true)
                        }
                    }
                }
                rawName.isNotEmpty() -> {
                    val key = nameKey(rawName)
                    val matches = byName[key].orEmpty()
                    if (matches.size == 1) {
                        recipientId = matches.single().id
                        unmatchedKey = null
                    } else {
                        val override = overrides[key]
                        if (override != null && byId.containsKey(override)) {
                            recipientId = override
                            unmatchedKey = null
                        } else {
                            recipientId = null
                            unmatchedKey = UnmatchedKey(key, rawName, byId = false)
                        }
                    }
                }
                else -> {
                    reject(
                        ImportIssue.MISSING_RECIPIENT,
                        "${CareNoteCsv.COL_RECIPIENT_ID}/${CareNoteCsv.COL_RECIPIENT_NAME}",
                    )
                    return@forEach
                }
            }

            if (recipientId == null && unmatchedKey != null) {
                val accumulator = unmatched[unmatchedKey.key]
                if (accumulator == null) {
                    unmatched[unmatchedKey.key] = UnmatchedAccumulator(unmatchedKey, count = 1)
                } else {
                    accumulator.count += 1
                }
                skipped += RejectedRow(row.line, ImportIssue.UNMATCHED_RECIPIENT)
                return@forEach
            }

            if (recipientId == null) {
                reject(ImportIssue.MISSING_RECIPIENT)
                return@forEach
            }

            val duplicateKey = ExistingNoteKey(recipientId, date)
            if (!seen.add(duplicateKey)) {
                duplicates += RejectedRow(row.line, ImportIssue.DUPLICATE)
                return@forEach
            }

            valid += PlannedNote(
                line = row.line,
                date = date,
                recipientId = recipientId,
                mood = mood.lowercase(Locale.ROOT),
                appetite = row.values[CareNoteCsv.COL_APPETITE].orEmpty().trim().lowercase(Locale.ROOT),
                sleep = row.values[CareNoteCsv.COL_SLEEP].orEmpty().trim().lowercase(Locale.ROOT),
                mobility = row.values[CareNoteCsv.COL_MOBILITY].orEmpty().trim().lowercase(Locale.ROOT),
                medicationTaken = row.values[CareNoteCsv.COL_MEDICATION_TAKEN].orEmpty().trim()
                    .lowercase(Locale.ROOT),
                pain = pain,
                fall = fall,
                text = text,
            )
        }

        val unmatchedList = unmatched.values
            .map { UnmatchedRecipient(it.key.key, it.key.identifier, it.key.byId, it.count) }
            .sortedWith(compareByDescending<UnmatchedRecipient> { it.rowCount }.thenBy { it.identifier })

        return ImportPlan(
            rows = rows,
            recipients = recipients,
            existing = existing,
            policy = policy,
            extraHeaders = extraHeaders,
            overrides = overrides,
            valid = valid,
            duplicates = duplicates,
            invalid = invalid,
            unmatched = unmatchedList,
            skipped = skipped,
        )
    }

    private const val MAX_OPTION_LENGTH = 32

    fun idKey(rawId: String): String = "id:$rawId"

    fun nameKey(rawName: String): String = "name:${normalizeName(rawName)}"

    private fun normalizeName(name: String): String = name.trim().lowercase(Locale.ROOT)

    private fun parseIsoDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }

    private data class UnmatchedKey(val key: String, val identifier: String, val byId: Boolean)

    private class UnmatchedAccumulator(val key: UnmatchedKey, var count: Int)
}
