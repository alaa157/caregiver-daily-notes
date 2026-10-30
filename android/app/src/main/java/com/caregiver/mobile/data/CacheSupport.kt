package com.caregiver.mobile.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide health of the best-effort Room cache (Task 8a), so a cache
 * write failure is no longer silent.
 *
 * The fallback is a documented degraded mode rather than an error on the
 * network call: the live response stays authoritative and is returned to
 * the caller, but [failures] reports the last write failure (count, type,
 * message) and stays non-null until a later cache write succeeds. Screens
 * or telemetry can observe it, and offline reads may serve stale or missing
 * rows until a refresh writes successfully.
 */
object CacheHealth {
    data class Failure(val count: Long, val type: String, val message: String?)

    private val _failures = MutableStateFlow<Failure?>(null)
    val failures: StateFlow<Failure?> = _failures.asStateFlow()

    internal fun recordFailure(error: Exception) {
        _failures.update { current ->
            Failure(
                count = (current?.count ?: 0) + 1,
                type = error.javaClass.simpleName,
                message = error.message,
            )
        }
    }

    internal fun recordSuccess() {
        _failures.value = null
    }

    /** Test seam: clears process-wide state between cases. */
    internal fun resetForTest() {
        _failures.value = null
    }
}

/**
 * Best-effort cache write (Task 8a): the network result is already valid,
 * so a failing disk write must never flip a successful load into an error.
 * The failure is recorded in [CacheHealth] (and cleared by the next
 * successful write) instead of being discarded.
 */
internal suspend fun cacheWrite(block: suspend () -> Unit) {
    try {
        block()
        CacheHealth.recordSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Cache only; the live response stays authoritative.
        CacheHealth.recordFailure(e)
    }
}

/**
 * Fallback read after a transport failure (Task 8a). Serves the cached
 * rows when the query has a cached copy; without one the original failure
 * is rethrown so screens keep their existing error/retry behavior. A null
 * cache (demo mode) always rethrows: there is no disk to read from.
 */
internal suspend fun <T> serveCached(cause: IOException, read: suspend () -> List<T>?): List<T> =
    read() ?: throw cause
