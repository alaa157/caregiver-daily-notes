package com.caregiver.mobile.data.csv

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

/** Outcome of parsing an imported file: either a reviewable plan or a fatal failure. */
sealed interface ImportPreparation {
    data class Ready(val plan: ImportPlan) : ImportPreparation
    data class Failed(val failure: CsvFailure) : ImportPreparation
}

/**
 * What actually happened when a plan was committed. Counters add up:
 * `imported + skipped + duplicates + rejected == total`.
 *
 * `failure` is set when the whole write failed (for example the database was
 * unavailable) or when the store rejected individual rows. Individual
 * rejections are still counted in `rejected`, so a partial import is always
 * visible to the user.
 */
data class ImportSummary(
    val imported: Int,
    val skipped: Int,
    val duplicates: Int,
    val rejected: Int,
    val total: Int,
    val failure: CsvFailure? = null,
)

/**
 * Application service behind the Data & Backup settings actions.
 *
 * Export snapshots the active data source and serializes every note — the
 * whole history, not a visible range — into the documented CSV shape.
 *
 * Import is split into [prepare] (parse, validate, plan) and [commit]
 * (write), so the UI can show an exact preview before anything changes, and
 * a commit can never write a row the user did not review. All work happens
 * on-device; nothing here touches the network unless the active store is the
 * API-backed one, which uses the same repository path as the editor.
 */
class CsvTransferService(
    private val store: CsvNoteStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun export(): CsvExportDocument {
        val snapshot = store.snapshot()
        val records = CsvExport.buildRecords(snapshot.notes, snapshot.recipients)
        return CsvExportDocument(content = CareNoteCsv.encode(records), recordCount = records.size)
    }

    suspend fun prepare(text: String): ImportPreparation {
        val decoded = when (val result = CareNoteCsv.decode(text)) {
            is CsvDecodeResult.Failure -> return ImportPreparation.Failed(result.failure)
            is CsvDecodeResult.Success -> result
        }
        if (decoded.rows.isEmpty()) {
            return ImportPreparation.Failed(CsvFailure(CsvFailureReason.NO_ROWS))
        }

        val snapshot = store.snapshot()
        val plan = CsvImportPlanner.plan(
            rows = decoded.rows,
            recipients = snapshot.recipients.map {
                RecipientRef(it.id, it.name, it.active, archived = it.archivedAt != null)
            },
            existing = snapshot.notes.mapTo(HashSet()) { ExistingNoteKey(it.recipientId, it.date) },
            policy = ImportPolicy(
                preservesDates = store.preservesDates,
                today = LocalDate.now(clock).toString(),
            ),
            extraHeaders = decoded.extraHeaders,
        )
        return ImportPreparation.Ready(plan)
    }

    /**
     * Writes the plan's valid rows. Duplicates are skipped (never overwritten)
     * and unmapped rows stay skipped; the caller receives exact counts.
     *
     * A Room store writes all rows in one transaction, so a mid-import
     * failure leaves the database exactly as it was and every valid row is
     * reported rejected at once. An API store reports per-row outcomes and
     * can therefore partially import; the summary keeps that visible.
     */
    suspend fun commit(plan: ImportPlan): ImportSummary {
        val invalid = plan.invalid.size
        val skipped = plan.skipped.size
        if (plan.valid.isEmpty()) {
            return ImportSummary(
                imported = 0,
                skipped = skipped,
                duplicates = plan.duplicates.size,
                rejected = invalid,
                total = plan.totalRows,
            )
        }

        val results = try {
            store.insertAll(plan.valid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportSummary(
                imported = 0,
                skipped = skipped,
                duplicates = plan.duplicates.size,
                rejected = invalid + plan.valid.size,
                total = plan.totalRows,
                failure = CsvFailure(CsvFailureReason.INSERT_FAILED),
            )
        }

        var imported = 0
        var duplicateCount = plan.duplicates.size
        var rejectedCount = invalid
        var rowFailure: CsvFailure? = null
        plan.valid.forEachIndexed { index, _ ->
            when (val result = results.getOrNull(index) ?: CsvInsertResult.Rejected(
                CsvFailure(CsvFailureReason.INSERT_FAILED),
            )) {
                CsvInsertResult.Inserted -> imported += 1
                CsvInsertResult.SkippedDuplicate -> duplicateCount += 1
                is CsvInsertResult.Rejected -> {
                    rejectedCount += 1
                    if (rowFailure == null) rowFailure = result.failure
                }
            }
        }

        return ImportSummary(
            imported = imported,
            skipped = skipped,
            duplicates = duplicateCount,
            rejected = rejectedCount,
            total = plan.totalRows,
            failure = rowFailure,
        )
    }

    /** The filename offered in the system save dialog, dated for easy sorting. */
    fun exportFileName(): String = "caregiver-notes-${LocalDate.now(clock)}.csv"
}
