package com.caregiver.mobile

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.util.LocaleHelper
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.RetrofitBackendApis
import com.caregiver.mobile.data.demo.DemoDatabase
import com.caregiver.mobile.data.demo.DemoNoteRepository
import com.caregiver.mobile.data.demo.DemoPlanStub
import com.caregiver.mobile.data.demo.DemoRecipientRepository
import com.caregiver.mobile.data.demo.DemoSession
import com.caregiver.mobile.data.demo.DemoSummaryStub
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CaregiverApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun attachBaseContext(base: Context) {
        // Apply the stored language before any resource loads. A small
        // blocking prefs read at startup; the settings screen recreates
        // the activity so this re-runs on language change. Single small
        // read only — if StrictMode ever flags it, move to a splash-gated
        // async load with the default locale as fallback. The read goes
        // through SettingsStores, so this handle and AppGraph.settings
        // share the single DataStore for the file.
        val language = try {
            runBlocking {
                SettingsStore.create(base).language.first()
            }
        } catch (e: Exception) {
            SettingsStore.DEFAULT_LANGUAGE
        }
        super.attachBaseContext(LocaleHelper.wrap(base, language))
    }
}

/** Manual composition root: settings, token mirror, API provider, auth. */
class AppGraph(val context: Context) {
    val tokens = TokenHolder()
    val settings: SettingsStore by lazy { SettingsStore.create(context) }
    val apis: BackendApis by lazy { RetrofitBackendApis(settings, tokens) }
    val auth: AuthRepository by lazy { AuthRepository(apis, settings, tokens) }

    // Offline demo path (Steps 5-6). The network fields above stay for the
    // backend flavor (Step 7); nothing in the demo screens uses them.
    val demoDb: DemoDatabase by lazy {
        Room.databaseBuilder(context, DemoDatabase::class.java, "demo.db").build()
    }
    val demoRecipients: DemoRecipientRepository by lazy { DemoRecipientRepository(demoDb) }
    val demoNotes: DemoNoteRepository by lazy { DemoNoteRepository(demoDb) }
    val demoSession: DemoSession by lazy { DemoSession(context) }
    val demoSummary: DemoSummaryStub by lazy { DemoSummaryStub() }
    val demoPlans: DemoPlanStub by lazy { DemoPlanStub() }

    /** Seed on first launch; no-op once recipients exist. */
    suspend fun ensureSeeded() {
        if (demoRecipients.isEmpty()) {
            demoNotes.resetToSeed()
        }
    }
}
