package com.caregiver.mobile

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Demo labelling contract (Finding 4). The debug and minified variants ship
 * DEMO_BACKEND=true together with LOCAL_MODE=true; local mode takes priority
 * and serves the real Room store, so their summaries are real output. The
 * illustrative-demo card and the "Hide Share with doctor" behaviour must key
 * off actual canned data (demo backend without local mode), never off the
 * bare build flag — previously this unit-test variant labelled its own real
 * Room data as demo and hid sharing.
 */
@RunWith(RobolectricTestRunner::class)
class AppGraphDemoLabellingTest {

    @Test
    fun localModePlusDemoBackendIsNotLabelledDemo() {
        // Pins the shipping configuration of this unit-test variant.
        assertEquals(true, BuildConfig.DEMO_BACKEND)
        assertEquals(true, BuildConfig.LOCAL_MODE)

        val graph = AppGraph(ApplicationProvider.getApplicationContext())

        assertTrue(graph.isLocalMode)
        assertFalse(graph.isDemo)
    }
}
