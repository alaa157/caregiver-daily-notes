package com.caregiver.mobile.core.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot "scroll to top" events for the tab shell.
 *
 * The bottom bar re-taps the ACTIVE tab and calls [emit] with that tab's
 * route (e.g. "home"); each tab screen collects [events] and scrolls its
 * list back to the first item. The screens are wired to collect this in a
 * later task — this object only defines the contract.
 *
 * No replay history is kept (replay = 0) and the buffer drops the oldest
 * event on overflow: the latest re-tap is what matters, and a screen that
 * subscribes later never sees a stale event. [emit] is non-suspending so it
 * is safe to call straight from the bottom bar's onClick.
 */
object ScrollToTop {
    private val _events = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits the route of the tab whose re-tap should scroll to top. */
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Non-suspending publish ([MutableSharedFlow.tryEmit]); never blocks UI. */
    fun emit(route: String) {
        _events.tryEmit(route)
    }
}
