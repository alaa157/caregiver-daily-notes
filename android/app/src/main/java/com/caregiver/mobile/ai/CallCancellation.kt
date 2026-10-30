package com.caregiver.mobile.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
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
    @OptIn(InternalCoroutinesApi::class)
    suspend fun install() {
        val job = currentCoroutineContext()[Job] ?: return
        // onCancelling = true is the important part: the handler runs when
        // cancellation is REQUESTED, not when the job finally completes. A
        // coroutine blocked in Call.execute() would otherwise never reach a
        // completion handler, and the request would keep running (and being
        // billed) after the user left the screen or the deadline fired.
        //
        // The onCancelling overload is marked internal, but it is the only
        // hook that runs before a blocking call returns. Disposing the handle
        // in close() keeps it from outliving the request.
        handle = job.invokeOnCompletion(onCancelling = true) { cause ->
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
