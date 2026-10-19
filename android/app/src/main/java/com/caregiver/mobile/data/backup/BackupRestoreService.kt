package com.caregiver.mobile.data.backup

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException

/** A serialized backup ready to be written to the user's document. */
data class BackupPackage(val content: String, val counts: BackupCounts)

/** Result of reading and validating a picked file. */
sealed interface RestorePreparation {
    data class Ready(val preview: RestorePreview) : RestorePreparation

    data class Failed(val failure: BackupFailure) : RestorePreparation
}

/**
 * Everything the confirmation dialog shows, plus the validated payload the
 * commit will write. [data] is carried in memory only and is never rendered;
 * the preview shows counts, provenance and the replacement warning.
 *
 * [current] is the local state the preview was built against; the commit
 * refuses to run when it no longer matches, so data created between preview
 * and confirmation is never silently discarded.
 */
data class RestorePreview(
    val createdAt: String,
    val formatVersion: Int,
    val appVersion: String,
    val backup: BackupCounts,
    val current: BackupCounts,
    val data: BackupData,
)

/** Terminal outcome of a confirmed restore. */
sealed interface RestoreOutcome {
    data class Restored(val counts: BackupCounts) : RestoreOutcome

    data class Failed(val failure: BackupFailure) : RestoreOutcome
}

/**
 * Application service behind the Data & Backup backup actions.
 *
 * Create: snapshot the standalone store and serialize one versioned document.
 * Restore: [prepare] reads, decodes, integrity-checks and semantically
 * validates a picked file and returns a reviewable preview — the database is
 * only read. [commit] then replaces the local restorable data in one Room
 * transaction; a failure anywhere leaves the database exactly as it was.
 *
 * The service never touches the network, the outbox, settings, tokens or AI
 * keys: no restore can trigger a sync, replay a stale operation, change an
 * account identity or upload anything. When this graph has no authoritative
 * local store (demo/server variants) [available] is false and every action
 * fails cleanly with [BackupFailureReason.UNAVAILABLE] instead of guessing.
 */
class BackupRestoreService(
    private val store: BackupStore?,
    private val appVersion: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** True when this build has a local store to back up and restore. */
    val available: Boolean get() = store != null

    /** Serializes every restorable record into one document. Throws on I/O/DB failure. */
    suspend fun create(): BackupPackage {
        val store = requireStore()
        val data = store.snapshot()
        val createdAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString()
        return BackupPackage(
            content = CareBackupCodec.encode(createdAt = createdAt, appVersion = appVersion, data = data),
            counts = data.counts,
        )
    }

    /** Reads, validates and previews a picked file without writing anything. */
    suspend fun prepare(text: String): RestorePreparation {
        val store = requireStore()
        val decoded = when (val result = CareBackupCodec.decode(text)) {
            is BackupDecodeResult.Failure -> return RestorePreparation.Failed(result.failure)
            is BackupDecodeResult.Success -> result.backup
        }
        val issues = BackupValidator.validate(decoded.data)
        if (issues.isNotEmpty()) {
            return RestorePreparation.Failed(
                BackupFailure(BackupFailureReason.INVALID_DATA, issueCount = issues.size),
            )
        }
        val current = try {
            store.counts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RestorePreparation.Failed(BackupFailure(BackupFailureReason.SOURCE_FAILED))
        }
        return RestorePreparation.Ready(
            RestorePreview(
                createdAt = decoded.createdAt,
                formatVersion = decoded.formatVersion,
                appVersion = decoded.appVersion,
                backup = decoded.data.counts,
                current = current,
                data = decoded.data,
            ),
        )
    }

    /** Writes a confirmed preview; replaces the local restorable data atomically. */
    suspend fun commit(preview: RestorePreview): RestoreOutcome {
        val store = requireStore()
        val result = try {
            store.replaceAll(preview.data, preview.current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RestoreOutcome.Failed(BackupFailure(BackupFailureReason.RESTORE_FAILED))
        }
        return when (result) {
            BackupReplaceResult.Replaced -> RestoreOutcome.Restored(preview.backup)
            BackupReplaceResult.Conflict -> RestoreOutcome.Failed(
                BackupFailure(BackupFailureReason.RESTORE_CONFLICT),
            )
        }
    }

    /** The filename offered in the system save dialog, dated for easy sorting. */
    fun fileName(): String = "caregiver-backup-${LocalDate.now(clock)}.carebackup.json"

    private fun requireStore(): BackupStore =
        store ?: throw IllegalStateException("Backup is not available in this configuration")
}
