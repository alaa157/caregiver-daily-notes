package com.caregiver.mobile.presentation.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 8: the offline demo entry is gated on the actual backend-selection
 * flag — not merely on a wired callback. Release builds (DEMO_BACKEND
 * false) never expose it, even if a handler were passed; demo-capable
 * builds show it only when the handler is wired.
 */
class DemoEntryVisibilityTest {

    @Test
    fun demoBuildWithHandlerShowsEntry() {
        assertTrue(shouldShowDemoEntry(demoBackendEnabled = true, hasDemoHandler = true))
    }

    @Test
    fun releaseBuildNeverShowsEntryEvenWithHandler() {
        assertFalse(shouldShowDemoEntry(demoBackendEnabled = false, hasDemoHandler = true))
    }

    @Test
    fun demoBuildWithoutHandlerShowsNothing() {
        assertFalse(shouldShowDemoEntry(demoBackendEnabled = true, hasDemoHandler = false))
    }

    @Test
    fun releaseBuildWithoutHandlerShowsNothing() {
        assertFalse(shouldShowDemoEntry(demoBackendEnabled = false, hasDemoHandler = false))
    }
}
