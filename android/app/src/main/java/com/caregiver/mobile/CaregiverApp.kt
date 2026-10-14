package com.caregiver.mobile

import android.app.Application
import android.content.Context
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.util.LocaleHelper
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.RetrofitBackendApis
import com.caregiver.mobile.data.demo.DemoBackendApis
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
class AppGraph(val context: Context, val forceProduction: Boolean = false) {
    val tokens = TokenHolder()
    val settings: SettingsStore by lazy { SettingsStore.create(context) }

    /**
     * Offline demo switch: debug builds serve [DemoBackendApis] (canned,
     * in-memory, no network) so the frontend can be installed and clicked
     * through with no backend. Release builds always use Retrofit.
     * [forceProduction] keeps MockWebServer instrumentation tests on the
     * real Retrofit path regardless of build type. No screen, ViewModel,
     * navigation or copy changes are needed — both providers implement
     * [BackendApis].
     */
    val apis: BackendApis by lazy {
        selectApis(
            !forceProduction && BuildConfig.DEMO_BACKEND,
            DemoBackendApis(),
            RetrofitBackendApis(settings, tokens),
        )
    }
    val auth: AuthRepository by lazy { AuthRepository(apis, settings, tokens) }
}

/**
 * Pure provider selector, unit-tested per build variant in
 * `AppGraphProviderTest`. Kept outside [AppGraph] so no Context,
 * DataStore, or network is needed to prove the wiring.
 */
internal fun selectApis(
    demoBackend: Boolean,
    demo: BackendApis,
    production: BackendApis,
): BackendApis = if (demoBackend) demo else production
