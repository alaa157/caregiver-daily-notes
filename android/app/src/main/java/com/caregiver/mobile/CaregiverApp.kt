package com.caregiver.mobile

import android.app.Application
import android.content.Context

class CaregiverApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/**
 * Manual composition root. Task 1 exposes only the application context;
 * Task 2 adds settings, the token store, and the API client.
 */
class AppGraph(val context: Context)
