package com.caregiver.mobile.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.caregiver.mobile.CaregiverApp
import java.util.concurrent.TimeUnit

/**
 * Drains the offline outbox (Task 8c) as one unique connected-work request,
 * so a note saved offline uploads as soon as connectivity returns:
 * [schedule] is called by [CaregiverApp] at startup and by
 * [NoteRepository.create] every time a save lands in the outbox.
 *
 * A transport failure (or a 5xx) asks WorkManager to retry with exponential
 * backoff; a rejected note (other 4xx) is left FAILED for the next trigger
 * (app start, a new offline save, or the Task 8d retry tap); a logged-out
 * session stops the pass without burning retries.
 *
 * Demo builds never upload: their graph has no outbox at all and [doWork]
 * bails before touching anything, even if a request is ever enqueued.
 */
class OutboxSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as? CaregiverApp)?.graph ?: return Result.success()
        if (graph.isDemo) return Result.success() // Never upload demo data.
        return when (graph.drainOutbox()) {
            OutboxDrain.DONE, OutboxDrain.STOPPED -> Result.success()
            OutboxDrain.RETRY -> Result.retry()
        }
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "outbox-sync"

        /** WorkManager's floor; exponential from there up to the platform cap. */
        private const val BACKOFF_SECONDS = 30L

        /**
         * Enqueues the drain. [ExistingWorkPolicy.KEEP] never disturbs a
         * queued or running pass — the drain reads the live table, and rows
         * saved while a pass runs are picked up by that same pass — so
         * re-scheduling from every offline save is harmless.
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
