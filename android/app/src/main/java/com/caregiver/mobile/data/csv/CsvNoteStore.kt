package com.caregiver.mobile.data.csv

import androidx.room.withTransaction
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.ArchivedRecipientException
import com.caregiver.mobile.data.local.NoteEntity
import com.caregiver.mobile.data.local.toDto
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** Everything the export/import planner needs to read from the active source. */
data class CsvSnapshot(val notes: List<NoteDto>, val recipients: List<RecipientDto>)

sealed interface CsvInsertResult {
    data object Inserted : CsvInsertResult

    /** The store found a note for the same (recipient, date) between preview and commit. */
    data object SkippedDuplicate : CsvInsertResult

    data class Rejected(val failure: CsvFailure) : CsvInsertResult
}

/**
 * The data-source seam for CSV import/export: a Room-backed store in
 * standalone builds and the API/repository seam in server-backed builds.
 *
 * [preservesDates] is the one behavioural difference the planner must know
 * about: Room stores the CSV date verbatim, while `POST /api/notes` assigns
 * the server's current date, so remote imports are limited to today's date
 * instead of silently writing a different day.
 */
interface CsvNoteStore {
    val preservesDates: Boolean

    suspend fun snapshot(): CsvSnapshot

    /**
     * Writes validated rows. A Room implementation runs every row in one
     * transaction (all-or-nothing); an API implementation reports one result
     * per row because the server cannot transact across creates.
     */
    suspend fun insertAll(rows: List<PlannedNote>): List<CsvInsertResult>
}

/**
 * Standalone storage: reads and writes the same Room database the local API
 * uses, so imported notes appear in every screen immediately.
 *
 * The all-or-nothing insert intentionally does not reuse `POST /api/notes`
 * semantics from `LocalBackendApis.create` (which stamps `LocalDate.now()`):
 * importing a spreadsheet must keep the CSV's date. Ids are freshly generated
 * local UUIDs — the CSV carries no id column, so an imported server id can
 * never collide with an existing one.
 */
class RoomCsvNoteStore(
    private val db: AppDatabase,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : CsvNoteStore {
    override val preservesDates: Boolean = true

    override suspend fun snapshot(): CsvSnapshot = CsvSnapshot(
        notes = db.noteDao().query(null, null, null).map { it.toDto() },
        recipients = db.recipientDao().all().map { it.toDto() },
    )

    override suspend fun insertAll(rows: List<PlannedNote>): List<CsvInsertResult> {
        if (rows.isEmpty()) return emptyList()
        return db.withTransaction {
            rows.map { row ->
                // The plan excluded archived recipients when it was built, but
                // the person can be archived while the preview is open. Re-check
                // inside the write transaction so an import can never write
                // into a read-only history.
                val recipient = db.recipientDao().byServerId(row.recipientId)
                val existing = if (recipient == null || recipient.archivedAt != null) {
                    emptyList()
                } else {
                    db.noteDao().query(row.recipientId, row.date, row.date)
                }
                when {
                    recipient == null || recipient.archivedAt != null ->
                        CsvInsertResult.Rejected(CsvFailure(CsvFailureReason.INSERT_FAILED))
                    existing.isNotEmpty() -> CsvInsertResult.SkippedDuplicate
                    else -> {
                        db.noteDao().insertImported(
                            listOf(
                                NoteEntity(
                                    serverId = idGenerator(),
                                    recipientId = row.recipientId,
                                    date = row.date,
                                    mood = row.mood,
                                    appetite = row.appetite,
                                    sleep = row.sleep,
                                    mobility = row.mobility,
                                    medicationTaken = row.medicationTaken,
                                    pain = row.pain,
                                    fall = row.fall,
                                    text = row.text,
                                ),
                            ),
                        )
                        CsvInsertResult.Inserted
                    }
                }
            }
        }
    }
}

/**
 * Server/demo storage: reads through the existing repositories (which merge
 * the cached copy and the offline outbox) and creates through `NoteApi`, so
 * imported notes follow the normal cache/outbox path.
 *
 * `preservesDates` is false because the API has no date field on create; the
 * planner rejects rows whose CSV date differs from today, so a note can never
 * silently land on the wrong day. Per-row failures are reported, not thrown,
 * so a partial import is counted and explained.
 */
class ApiCsvNoteStore(private val apis: BackendApis) : CsvNoteStore {
    override val preservesDates: Boolean = false

    override suspend fun snapshot(): CsvSnapshot = CsvSnapshot(
        notes = apis.notes().history(null, null, null),
        recipients = apis.recipients().list(),
    )

    override suspend fun insertAll(rows: List<PlannedNote>): List<CsvInsertResult> =
        rows.map { row ->
            try {
                val created = apis.notes().create(
                    CreateNoteRequest(
                        recipientId = row.recipientId,
                        mood = row.mood,
                        appetite = row.appetite,
                        sleep = row.sleep,
                        mobility = row.mobility,
                        medicationTaken = row.medicationTaken,
                        pain = row.pain,
                        fall = row.fall,
                        text = row.text,
                    ),
                )
                // The repository resolves a duplicate-day 422 to the note
                // that already exists; only a returned note whose content
                // matches the request is the row we just created. Anything
                // else was an existing note, so it is reported as skipped,
                // never as imported and never overwritten.
                if (sameContent(created, row)) {
                    CsvInsertResult.Inserted
                } else {
                    CsvInsertResult.SkippedDuplicate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArchivedRecipientException) {
                // The recipient was archived after the preview was built.
                CsvInsertResult.Rejected(CsvFailure(CsvFailureReason.INSERT_FAILED))
            } catch (e: HttpException) {
                CsvInsertResult.Rejected(CsvFailure(CsvFailureReason.INSERT_FAILED))
            } catch (e: IOException) {
                CsvInsertResult.Rejected(CsvFailure(CsvFailureReason.INSERT_FAILED))
            }
        }

    private fun sameContent(note: NoteDto, row: PlannedNote): Boolean =
        note.recipientId == row.recipientId &&
            note.mood == row.mood &&
            note.appetite == row.appetite &&
            note.sleep == row.sleep &&
            note.mobility == row.mobility &&
            note.medicationTaken == row.medicationTaken &&
            note.pain == row.pain &&
            note.fall == row.fall &&
            note.text == row.text
}
