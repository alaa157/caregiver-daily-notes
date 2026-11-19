package com.caregiver.mobile.test

import android.app.Application
import androidx.work.Configuration

/**
 * Robolectric application for tests that construct their own `AppGraph`.
 *
 * The production [com.caregiver.mobile.CaregiverApp] boots a graph in
 * `onCreate`; a second graph on the same application context would open a
 * second DataStore on the same session/settings files, which DataStore
 * forbids. This application skips that boot but still supplies on-demand
 * WorkManager initialization (the production process gets it from
 * androidx.startup), because account swaps cancel WorkManager jobs.
 */
class TestGraphApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
