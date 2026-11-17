package com.caregiver.mobile

import android.app.Application

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.util.LanguagePrefs
import com.caregiver.mobile.core.util.LocaleHelper
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.CacheHealth
import com.caregiver.mobile.data.NoteRepository
import com.caregiver.mobile.data.OutboxDrain
import com.caregiver.mobile.data.OutboxSyncWorker
import com.caregiver.mobile.data.RecipientRepository
import com.caregiver.mobile.data.RoomBackedBackendApis
import com.caregiver.mobile.data.SampleDataLoader
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import com.caregiver.mobile.data.accounts.AccountManager
import com.caregiver.mobile.data.accounts.AccountsDatabase
import com.caregiver.mobile.data.accounts.PerAccountDatabaseProvider
import com.caregiver.mobile.data.accounts.SessionStore
import com.caregiver.mobile.data.backup.BackupData
import com.caregiver.mobile.data.backup.BackupReplaceResult
import com.caregiver.mobile.data.backup.BackupRestoreService
import com.caregiver.mobile.data.backup.BackupState
import com.caregiver.mobile.data.backup.BackupStore
import com.caregiver.mobile.data.backup.RoomBackupStore
import com.caregiver.mobile.data.api.AuthApi
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.RegisterRequest
import com.caregiver.mobile.data.api.RetrofitBackendApis
import com.caregiver.mobile.data.csv.ApiCsvNoteStore
import com.caregiver.mobile.data.csv.CsvInsertResult
import com.caregiver.mobile.data.csv.CsvNoteStore
import com.caregiver.mobile.data.csv.CsvSnapshot
import com.caregiver.mobile.data.csv.CsvTransferService
import com.caregiver.mobile.data.csv.PlannedNote
import com.caregiver.mobile.data.csv.RoomCsvNoteStore
import com.caregiver.mobile.data.demo.DemoBackendApis
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.LocalBackendApis
import com.caregiver.mobile.data.local.MedicationStore
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.local.RecipientDeletionCounts
import com.caregiver.mobile.data.recipient.CareDetails
import com.caregiver.mobile.data.summary.SavedSummaryStore
import com.caregiver.mobile.data.update.GitHubReleaseChecker
import com.caregiver.mobile.data.update.UpdateChecker
import com.caregiver.mobile.ai.LlmClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import retrofit2.Response

/** Log tag for language-restore diagnostics (Log.i/e survive the minified build). */
private const val TAG = "Language"

class CaregiverApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun attachBaseContext(base: Context) {
        // Apply the stored language before any resource loads. Language
        // changes are mirrored synchronously into SharedPreferences
        // (LanguagePrefs — plain commit(), no coroutines, no file parsing),
        // so the mirror is read first and any language once picked survives
        // process death. When the mirror is empty (first launch, or a device
        // whose earlier DataStore path silently failed), fall back to the
        // DataStore read through SettingsStore — this handle and
        // AppGraph.settings share the single DataStore for the file — and
        // self-heal that value back into the mirror for the next start.
        LanguagePrefs.attachHost(base)
        val language = LanguagePrefs.read()
            ?: try {
                runBlocking {
                    SettingsStore.create(base).language.first()
                }.also { Log.i(TAG, "mirror empty; DataStore holds $it") }
            } catch (e: Exception) {
                // A failed read has no other visible symptom than the
                // language dropping to the default on restart; surface it.
                Log.e(TAG, "DataStore language read failed at process start", e)
                SettingsStore.DEFAULT_LANGUAGE
            }
        if (LanguagePrefs.read() != language) LanguagePrefs.write(language)
        // Seed the process cache the activities wrap their base context
        // with; activities are created after this runs.
        Log.i(TAG, "cold start language=$language")
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

/**
 * Resolves the active account's database on every access (epic #581, #602).
 * It never captures an instance: a switch changes [activeAccountId], so the
 * next call opens the other account's file. With no active account it fails
 * loudly rather than reading or writing a wrong database.
 */
private class ActiveDatabase(
    private val provider: PerAccountDatabaseProvider,
    private val activeAccountId: StateFlow<String?>,
    private val isSwapping: () -> Boolean = { false },
) {
    fun get(): AppDatabase {
        if (isSwapping()) {
            throw IllegalStateException("Account swap in progress: database writes are temporarily rejected")
        }
        val id = activeAccountId.value
            ?: throw IllegalStateException("No active account: local database access is unavailable")
        return provider.open(id)
    }
}

/** Account-scoped collectors live here so a swap can cancel and join them. */
private class AccountWorkScope {
    private var scope: CoroutineScope = fresh()

    val current: CoroutineScope get() = scope

    fun cancel() {
        scope.cancel()
    }

    suspend fun join() {
        scope.coroutineContext[Job]?.join()
    }

    fun reset() {
        scope = fresh()
    }

    private fun fresh() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/**
 * Real local authentication backed by AccountManager (#597).
 *
 * The token is a stable opaque `"local:<accountId>"` handle — no server
 * exists to authenticate against, so it is not a secret and is never the
 * password or derived hash. Failures use the same HTTP-shaped exception the
 * Retrofit path produces, so screens need no local-specific branch.
 */
private class LocalAccountAuthApi(
    private val manager: AccountManager,
    private val tokens: TokenHolder,
) : AuthApi {
    override suspend fun login(body: LoginRequest): AuthResponse =
        manager.signIn(body.email, body.password).fold(
            onSuccess = { issue(it.id) },
            onFailure = { throw it.asHttpError() },
        )

    override suspend fun register(body: RegisterRequest): AuthResponse =
        manager.signUp(AuthRepository.deriveName(body.email), body.email, body.password).fold(
            onSuccess = { issue(it.id) },
            onFailure = { throw it.asHttpError() },
        )

    private fun issue(accountId: String): AuthResponse =
        AuthResponse(AuthRepository.localToken(accountId)).also { tokens.token = it.token }

    private fun Throwable?.asHttpError(): HttpException {
        val error = (this as? AccountException)?.error
        val code = when (error) {
            AccountError.InvalidCredentials -> 401
            AccountError.EmailTaken -> 409
            is AccountError.WeakPassword, AccountError.InvalidEmail -> 400
            else -> 400
        }
        val errorCode = when (error) {
            AccountError.InvalidCredentials -> "INVALID_CREDENTIALS"
            AccountError.EmailTaken -> "EMAIL_TAKEN"
            is AccountError.WeakPassword -> "WEAK_PASSWORD"
            AccountError.InvalidEmail -> "INVALID_EMAIL"
            else -> "REQUEST_FAILED"
        }
        val body = """{"code":"$errorCode","message":"$errorCode"}"""
            .toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }
}

/** Delegates every call to a LocalBackendApis built on the current database. */
private class ActiveLocalApis(
    private val database: ActiveDatabase,
    private val clock: () -> Long,
    private val authApi: () -> AuthApi,
) : BackendApis, RecipientAdmin {
    private fun api(): LocalBackendApis = LocalBackendApis(database.get(), clock)

    override suspend fun auth(): AuthApi = authApi()
    override suspend fun recipients() = api().recipients()
    override suspend fun notes() = api().notes()
    override suspend fun summaries() = api().summaries()
    override suspend fun plans() = api().plans()

    override suspend fun rename(id: String, name: String) = api().rename(id, name)
    override suspend fun updateProfile(id: String, name: String, relationship: String?) =
        api().updateProfile(id, name, relationship)
    override suspend fun updateCareDetails(id: String, details: CareDetails) =
        api().updateCareDetails(id, details)
    override suspend fun archive(id: String) = api().archive(id)
    override suspend fun unarchive(id: String) = api().unarchive(id)
    override suspend fun deleteArchived(id: String) = api().deleteArchived(id)
    override suspend fun counts(id: String): RecipientDeletionCounts = api().counts(id)
}

/** CSV import/export over the active account's database. */
private class ActiveCsvNoteStore(private val database: ActiveDatabase) : CsvNoteStore {
    override val preservesDates: Boolean = true
    private fun store(): CsvNoteStore = RoomCsvNoteStore(database.get())

    override suspend fun snapshot(): CsvSnapshot = store().snapshot()
    override suspend fun insertAll(rows: List<PlannedNote>): List<CsvInsertResult> = store().insertAll(rows)
}

/** Full backup/restore over the active account's database. */
private class ActiveBackupStore(private val database: ActiveDatabase) : BackupStore {
    private fun store(): BackupStore = RoomBackupStore(database.get())

    override suspend fun snapshot(): BackupData = store().snapshot()
    override suspend fun state(): BackupState = store().state()
    override suspend fun replaceAll(data: BackupData, expected: BackupState): BackupReplaceResult =
        store().replaceAll(data, expected)
}

/** Manual composition root: settings, token mirror, API provider, Room cache, auth. */
class AppGraph(
    val context: Context,
    val forceProduction: Boolean = false,
    /** UI hooks for #618/#620; the graph stays usable without them. */
    private val onResetAccountViewModels: () -> Unit = {},
    private val onNavigateHome: () -> Unit = {},
) {
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
     * One note database file per account (#601/#602) — never a singleton.
     * The active id lives in [sessionStore]; every consumer resolves the
     * current database through [activeDatabase], so switching accounts can
     * never leave a stale connection pointing at the previous account.
     */
    private val accountDatabaseProvider: PerAccountDatabaseProvider by lazy {
        PerAccountDatabaseProvider(context)
    }

    private val sessionStore: SessionStore by lazy { SessionStore.create(context) }

    private val activeAccountId = MutableStateFlow<String?>(null)

    private val noAccountEpoch = MutableStateFlow(0)

    /** Advances on every local swap; account-scoped observers re-fetch on change. */
    val accountEpoch: StateFlow<Int> get() = localAccountManager?.epoch ?: noAccountEpoch

    /** The active account id; AI callers tag results with it before returning. */
    val activeAccountIdFlow: StateFlow<String?> get() = activeAccountId

    /** AI/summary work must be launched here so a swap cancels and joins it. */
    val accountWorkScope: CoroutineScope get() = accountWork.current

    /** Collectors for the current account; cancelled and joined before close. */
    private val accountWork = AccountWorkScope()

    private val activeDatabase: ActiveDatabase by lazy {
        ActiveDatabase(
            provider = accountDatabaseProvider,
            activeAccountId = activeAccountId,
            isSwapping = { localAccountManager?.isSwapping == true },
        )
    }

    /** The current account's database, or null when signed out / not local. */
    private val localDatabase: AppDatabase? get() =
        if (localMode) accountDatabaseProvider.current() else null

    init {
        if (localMode) {
            // Restore the active id before any screen asks for a DAO.
            activeAccountId.value = runBlocking { sessionStore.activeAccountId.first() }
            activeAccountId.value?.let { accountDatabaseProvider.open(it) }
        }
    }

    /**
     * The single local [AccountManager] in this graph — the same instance
     * handed to [auth] and the local auth API. Exposed so the register screen
     * can pass its `signUp` as `registerAccount` (#704); server/demo graphs
     * have no manager, so their register path stays unchanged.
     */
    val accountManager: AccountManager? get() = localAccountManager

    /** Local sign-in/up keep the active-id flow in sync with the session store. */
    private val localAccountManager: AccountManager? by lazy {
        if (!localMode) null else AccountManager(
            dao = AccountsDatabase.create(context).accountDao(),
            sessionStore = sessionStore,
            onActiveAccountChanged = { id -> activeAccountId.value = id },
            openAccountDatabase = { id -> accountDatabaseProvider.open(id) },
            closeAccountDatabase = { accountDatabaseProvider.close() },
            cancelAccountWork = {
                accountWork.cancel()
                // WorkManager jobs are cancelled by unique name; the
                // outbox drain and any per-account-named work for the
                // outgoing account must not run after the swap (#619).
                val workManager = WorkManager.getInstance(context.applicationContext)
                workManager.cancelUniqueWork(OutboxSyncWorker.UNIQUE_WORK_NAME)
                activeAccountId.value?.let { workManager.cancelUniqueWork("outbox-sync-$it") }
            },
            awaitAccountWork = { accountWork.join() },
            onSwapCompleted = {
                accountWork.reset()
                onResetAccountViewModels()
                onNavigateHome()
            },
        )
    }

    private val live: BackendApis by lazy {
        when {
            localMode -> ActiveLocalApis(
                database = activeDatabase,
                clock = System::currentTimeMillis,
                authApi = { LocalAccountAuthApi(checkNotNull(localAccountManager), tokens) },
            )
            else -> selectApis(
                demoBackend,
                DemoBackendApis(),
                RetrofitBackendApis(settings, tokens),
            )
        }
    }

    /**
     * Local-only recipient archive/delete admin. Null when this graph has no
     * local database (server-cache or demo), so the UI can hide the actions
     * instead of faking success.
     */
    val recipientAdmin: RecipientAdmin? by lazy { live as? RecipientAdmin }

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
     * Observable cache-write health for server-backed graphs. It is null in
     * local/demo mode, where no fallback cache is written; a non-null value
     * means the last Room cache write failed and offline reads may be stale
     * until a later refresh succeeds.
     */
    val cacheHealth: StateFlow<CacheHealth.Failure?> = CacheHealth.failures

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
                ActiveCsvNoteStore(activeDatabase)
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
            store = if (localMode) ActiveBackupStore(activeDatabase) else null,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }

    /**
     * Medication card (home) over standalone local tables — see
     * [MedicationStore]. Null exactly when the graph has no local database
     * (forceProduction/server-only test graphs), and the home card hides
     * itself then, the same self-disabling as [backupRestoreService].
     */
    private var medicationsFor: Pair<String, MedicationStore>? = null

    val medications: MedicationStore? get() {
        if (!localMode) return null
        val id = activeAccountId.value ?: return null
        val cached = medicationsFor
        if (cached?.first == id) return cached.second
        return MedicationStore(accountDatabaseProvider.databaseFor(id)).also { medicationsFor = id to it }
    }

    /**
     * On-device store for generated summaries, shared by the summary result
     * screen. Local mode writes through the shared local database; production
     * uses the cache database. Null in demo graphs — illustrative summaries
     * are never persisted — so callers pass it straight through.
     */
    private var summariesFor: Pair<String, SavedSummaryStore>? = null

    val savedSummaryStore: SavedSummaryStore? get() {
        if (isDemo) return null
        if (localMode) {
            val id = activeAccountId.value ?: return null
            val cached = summariesFor
            if (cached?.first == id) return cached.second
            return SavedSummaryStore(accountDatabaseProvider.databaseFor(id)).also { summariesFor = id to it }
        }
        return db?.let { SavedSummaryStore(it) }
    }

    /**
     * Settings' "load sample data" action. It writes through
     * [localDatabase], the same Room instance the local API, CSV transfer
     * and backup services use, so observers are invalidated by this write
     * instead of by a second connection opened from a composable.
     */
    suspend fun loadSampleData() {
        localDatabase?.let { SampleDataLoader.load(it) }
    }

    /**
     * True when this graph genuinely serves canned demo data: the demo
     * backend **and** not local-first mode. The debug and minified variants
     * ship with BOTH DEMO_BACKEND=true and LOCAL_MODE=true — local mode
     * takes priority and serves the real Room store, so those summaries
     * are real output: no illustrative-demo card, Share with doctor and
     * PDF export enabled. Labelling by the bare DEMO_BACKEND hidden real
     * content behind the demo card (Finding 4).
     * Screens use this — never the bare build type — to label
     * illustrative demo content, so production summaries are never
     * mislabeled and `forceProduction` test graphs never claim demo data.
     */
    val isDemo: Boolean
        get() = demoBackend && !isLocalMode

    /** One outbox drain pass; the WorkManager worker (Task 8c) calls this. */
    suspend fun drainOutbox(): OutboxDrain = noteRepository.drainOutbox()

    /**
     * Auth over local accounts in LOCAL_MODE; the manager writes the active
     * id to [sessionStore] and opens/closes the per-account database. Server
     * and demo graphs pass no manager, so their paths are unchanged.
     */
    val auth: AuthRepository by lazy {
        AuthRepository(
            apis = apis,
            settings = settings,
            tokens = tokens,
            accountManager = localAccountManager,
            localApisFor = { if (localMode) live else apis },
        )
    }

    /**
     * Public GitHub Releases check for the About page. It reads only the
     * app's own release feed and never sends caregiver or note data.
     */
    val updateChecker: UpdateChecker by lazy { GitHubReleaseChecker() }
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
