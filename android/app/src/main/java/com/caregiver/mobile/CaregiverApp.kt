package com.caregiver.mobile

import android.app.Application

import android.content.Context
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.util.LocaleHelper
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.NoteRepository
import com.caregiver.mobile.data.OutboxDrain
import com.caregiver.mobile.data.OutboxSyncWorker
import com.caregiver.mobile.data.RecipientRepository
import com.caregiver.mobile.data.RoomBackedBackendApis
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.backup.BackupRestoreService
import com.caregiver.mobile.data.backup.RoomBackupStore
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.RetrofitBackendApis
import com.caregiver.mobile.data.csv.ApiCsvNoteStore
import com.caregiver.mobile.data.csv.CsvTransferService
import com.caregiver.mobile.data.csv.RoomCsvNoteStore
import com.caregiver.mobile.data.demo.DemoBackendApis
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.LocalBackendApis
import com.caregiver.mobile.ai.LlmClientFactory
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
        // Seed the process cache the activities wrap their base context
        // with; activities are created after this runs.
        LocaleHelper.cache(language)
        super.attachBaseContext(LocaleHelper.wrap(base, language))
    }


    override fun onCreate() {
        super.onCreate()
        // Task 8c: drain anything a previous run left in the outbox as soon
        // as connectivity returns. Demo graphs have no outbox — and demo
        // data must never upload — so they never schedule the worker.
        if (!graph.isDemo && !graph.isLocalMode) OutboxSyncWorker.schedule(this)
    }
}

/** Manual composition root: settings, token mirror, API provider, Room cache, auth. */
class AppGraph(val context: Context, val forceProduction: Boolean = false) {
    val tokens = TokenHolder()
    val settings: SettingsStore by lazy { SettingsStore.create(context) }

    /** True when this graph serves the offline demo backend. */
    private val demoBackend: Boolean
        get() = !forceProduction && BuildConfig.DEMO_BACKEND

    /** True when running standalone with no backend. */
    val isLocalMode: Boolean
        get() = !forceProduction && BuildConfig.LOCAL_MODE

    val llmClientFactory: LlmClientFactory by lazy { LlmClientFactory() }

    /**
     * Offline demo switch: demo builds (debug + minified) serve
     * [DemoBackendApis] (canned, in-memory, no network) so the app can be
     * installed and clicked through with no backend. Release always uses
     * Retrofit.
     * [forceProduction] keeps MockWebServer instrumentation tests on the
     * real Retrofit path regardless of build type. No screen, ViewModel,
     * navigation or copy changes are needed — both providers implement
     * [BackendApis].
     */
    private val localMode: Boolean
        get() = isLocalMode

    /**
     * Standalone storage for LOCAL_MODE, shared by the local API and CSV
     * import/export so both read and write the same database. Null in
     * demo/server graphs, which use their own (or no) cache.
     */
    private val localDatabase: AppDatabase? by lazy {
        if (localMode) AppDatabase.create(context) else null
    }

    private val live: BackendApis by lazy {
        when {
            localMode -> LocalBackendApis(checkNotNull(localDatabase))
            else -> selectApis(
                demoBackend,
                DemoBackendApis(),
                RetrofitBackendApis(settings, tokens),
            )
        }
    }

    /** Task 8a cache; production-only so demo data never reaches disk. */
    private val db: AppDatabase? by lazy {
        when {
            localMode -> null
            demoBackend -> null
            else -> AppDatabase.create(context)
        }
    }

    /**
     * Notes with the offline outbox (Task 8b) and its drain (Task 8c).
     * [NoteRepository.scheduleOutboxSync] is only reachable once the
     * outbox exists (production): a demo save never schedules work.
     */
    val noteRepository: NoteRepository by lazy {
        if (localMode) {
            NoteRepository(apis = live, dao = null, outboxDao = null)
        } else {
            NoteRepository(
                apis = live,
                dao = db?.noteDao(),
                outboxDao = db?.outboxDao(),
                scheduleOutboxSync = { OutboxSyncWorker.schedule(context) },
            )
        }
    }

    val apis: BackendApis by lazy {
        if (localMode) {
            live
        } else {
            RoomBackedBackendApis(
                live = live,
                recipients = RecipientRepository(live, db?.recipientDao()),
                notes = noteRepository,
            )
        }
    }

    /**
     * Data & Backup (CSV import/export) over the active data source. In
     * LOCAL_MODE the store is the same Room database the local API serves,
     * and a CSV date is preserved verbatim; otherwise creates go through
     * `apis.notes().create()` (repository/outbox path), where the server
     * assigns the date, so the planner limits imports to today. Files are
     * never sent anywhere: the user picks one document via the system picker.
     */
    val csvTransferService: CsvTransferService by lazy {
        CsvTransferService(
            store = if (localMode) {
                RoomCsvNoteStore(checkNotNull(localDatabase))
            } else {
                ApiCsvNoteStore(apis)
            },
        )
    }

    /**
     * Data & Backup (full backup/restore) over the standalone store only.
     * [localDatabase] is null exactly when this graph has no authoritative
     * local data (demo graphs have none; server graphs use Room as a cache),
     * and restoring into a cache would fight the sync contract — so the
     * service is created without a store there and the settings actions are
     * disabled with an explanation. Files never leave the device.
     */
    val backupRestoreService: BackupRestoreService by lazy {
        BackupRestoreService(
            store = localDatabase?.let { RoomBackupStore(it) },
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }

    /**
     * True when this graph serves the offline demo backend (and therefore
     * has no Room cache). Screens use this — never the bare build type — to
     * label illustrative demo content, so production summaries are never
     * mislabeled and `forceProduction` test graphs never claim demo data.
     */
    val isDemo: Boolean
        get() = demoBackend

    /** One outbox drain pass; the WorkManager worker (Task 8c) calls this. */
    suspend fun drainOutbox(): OutboxDrain = noteRepository.drainOutbox()

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
