package com.caregiver.mobile.presentation.common

import com.caregiver.mobile.data.local.OutboxStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 8d: the top-bar status matrix — outbox rows' statuses plus
 * connectivity in, synced/syncing/offline/failed out, retry forwarded.
 */
class SyncStatusViewModelTest {

    @Test
    fun emptyOutboxReadsSyncedOnlineAndOffline() = runTest {
        val connected = MutableStateFlow(true)
        val vm = SyncStatusViewModel(flowOf(emptyList()), connected)
        assertEquals(SyncStatus.SYNCED, vm.status.first())

        connected.value = false
        // Nothing is waiting, so everything already reached the server.
        assertEquals(SyncStatus.SYNCED, vm.status.first())
    }

    @Test
    fun waitingNotesFollowConnectivity() = runTest {
        val statuses = MutableStateFlow(listOf(OutboxStatus.PENDING))
        val connected = MutableStateFlow(true)
        val vm = SyncStatusViewModel(statuses, connected)

        assertEquals(SyncStatus.SYNCING, vm.status.first())

        connected.value = false
        assertEquals(SyncStatus.OFFLINE, vm.status.first())

        connected.value = true
        assertEquals(SyncStatus.SYNCING, vm.status.first())
    }

    @Test
    fun inFlightUploadOutranksFailedRows() = runTest {
        val statuses = MutableStateFlow(listOf(OutboxStatus.FAILED, OutboxStatus.SYNCING))
        val vm = SyncStatusViewModel(statuses, MutableStateFlow(false))

        // The pass is moving rows; the retry affordance can wait for it.
        assertEquals(SyncStatus.SYNCING, vm.status.first())

        statuses.value = listOf(OutboxStatus.FAILED)
        assertEquals(SyncStatus.FAILED, vm.status.first())
    }

    @Test
    fun failedRowsAreReportedOfflineTooAndRetryIsForwarded() = runTest {
        var retried = 0
        val vm = SyncStatusViewModel(
            flowOf(listOf(OutboxStatus.FAILED, OutboxStatus.PENDING)),
            flowOf(false),
        ) { retried++ }

        // A rejection needs the user's attention even while offline.
        assertEquals(SyncStatus.FAILED, vm.status.first())

        vm.retry()
        assertEquals(1, retried)
    }
}
