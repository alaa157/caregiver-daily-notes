package com.caregiver.mobile.core.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the contract the bottom bar and the tab screens rely on: re-tap
 * events travel on [ScrollToTop.events] with the tab route as payload, and
 * [ScrollToTop.emit] never suspends (safe from UI callbacks).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollToTopTest {

    @Test
    fun emitDeliversRouteToActiveCollector() = runTest {
        var received: String? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            received = ScrollToTop.events.first()
        }

        ScrollToTop.emit(MainTab.Notes.route)

        assertEquals(MainTab.Notes.route, received)
    }

    @Test
    fun emitWithoutCollectorsIsANonSuspendingNoOp() {
        // tryEmit-based: must not throw (or suspend) when no screen collects,
        // e.g. while a re-tap races the next screen's subscription.
        ScrollToTop.emit(MainTab.Home.route)
    }
}
