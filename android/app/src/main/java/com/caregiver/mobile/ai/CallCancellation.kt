package com.caregiver.mobile.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.Call

/**
 * Cancels the in-flight HTTP call when the calling coroutine is cancelled.
 *
 * One handler per request, disposed when the request ends. A handler per
 * retry would pile up on the job — one per attempt, each holding a call that
 * is long gone — and none of them would ever be removed.
 */
internal class CallCancellation : AutoCloseable {
    @Volatile
    private var call: Call? = null

    private var handle: DisposableHandle? = null

    /** Registers the cancellation handler on the calling coroutine's job. */
    suspend fun install() {
        val job = currentCoroutineContext()[Job] ?: return
        handle = job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call?.cancel()
        }
    }

    /** The call to cancel when the coroutine is cancelled. */
    fun track(call: Call) {
        this.call = call
    }

    override fun close() {
        handle?.dispose()
        handle = null
        call = null
    }
}
