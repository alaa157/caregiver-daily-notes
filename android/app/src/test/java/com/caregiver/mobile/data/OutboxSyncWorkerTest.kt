package com.caregiver.mobile.data

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 8c: the drain is scheduled as one unique request constrained to
 * connectivity (KEEP: re-scheduling never duplicates it), and demo builds
 * never upload.
 */
@RunWith(RobolectricTestRunner::class)
class OutboxSyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun scheduleEnqueuesOneUniqueConnectedDrain() {
        OutboxSyncWorker.schedule(context)
        OutboxSyncWorker.schedule(context) // KEEP: no duplicate request.

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(OutboxSyncWorker.UNIQUE_WORK_NAME)
            .get()

        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
    }

    @Test
    fun demoGraphsDrainNothingAndSucceed() = runTest {
        // The app graph under unit tests is the demo graph: the worker must
        // bail before touching the outbox or the network.
        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
