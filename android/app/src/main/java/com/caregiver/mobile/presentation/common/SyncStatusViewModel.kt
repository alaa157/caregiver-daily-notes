package com.caregiver.mobile.presentation.common

import androidx.lifecycle.ViewModel
import com.caregiver.mobile.data.local.OutboxStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * What the notes saved on this device are doing, as shown in the top-bar
 * end slot (Task 8d).
 */
enum class SyncStatus {
    /** No row is waiting in the outbox: everything reached the server. */
    SYNCED,

    /** Rows are waiting and the device is online: the drain is running or queued. */
    SYNCING,

    /** Rows are waiting while the device has no connection. */
    OFFLINE,

    /** The server rejected at least one note; tap the indicator to retry. */
    FAILED,
}

/**
 * Derives the top-bar status from the outbox rows' statuses ([OutboxStatus]
 * values, one per queued note) and the current connectivity. The drain
 * itself lives in NoteRepository/OutboxSyncWorker (Task 8c); this VM only
 * labels it and forwards [retry], which re-schedules that drain — failed
 * rows are attempted again by the next pass.
 *
 * [status] is cold: every collector recomputes from the current rows and
 * connectivity, so the top bar collects it once via
 * `collectAsState(initial = SyncStatus.SYNCED)`. Demo builds have no
 * outbox, so their statuses flow is empty and the indicator always reads
 * [SyncStatus.SYNCED].
 */
class SyncStatusViewModel(
    statuses: Flow<List<String>>,
    connected: Flow<Boolean>,
    private val onRetry: () -> Unit = {},
) : ViewModel() {

    val status: Flow<SyncStatus> =
        combine(statuses, connected) { rows, online ->
            when {
                // An upload in flight wins over everything: it is progress.
                rows.any { it == OutboxStatus.SYNCING } -> SyncStatus.SYNCING
                // A rejection needs the user's attention even while offline.
                rows.any { it == OutboxStatus.FAILED } -> SyncStatus.FAILED
                rows.isEmpty() -> SyncStatus.SYNCED
                !online -> SyncStatus.OFFLINE
                // Waiting rows with a connection: the WorkManager drain is
                // scheduled and runs as soon as its constraint allows.
                else -> SyncStatus.SYNCING
            }
        }

    /** Tap-to-retry from the top bar (Task 8d). */
    fun retry() = onRetry()
}
