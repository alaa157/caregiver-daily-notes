package com.caregiver.mobile.data

import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Best-effort cache write (Task 8a): the network result is already valid,
 * so a failing disk write must never flip a successful load into an error.
 */
internal suspend fun cacheWrite(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Cache only; the live response stays authoritative.
    }
}

/**
 * Fallback read after a transport failure (Task 8a). Serves the cached
 * rows when the query has a cached copy; without one the original failure
 * is rethrown so screens keep their existing error/retry behavior. A null
 * cache (demo mode) always rethrows: there is no disk to read from.
 */
internal suspend fun <T> serveCached(cause: IOException, read: suspend () -> List<T>?): List<T> =
    read()?.takeIf { it.isNotEmpty() } ?: throw cause
