package com.caregiver.mobile.data.accounts

import androidx.sqlite.SQLiteConstraintException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A typed [AccountError] carried through the manager's [Result] failures.
 * The six variants stay defined by #589; this wrapper only makes them
 * throwable so signUp/signIn can share the [Result] shape (#591).
 */
data class AccountException(val error: AccountError) : Exception(error.toString())

/**
 * A value tagged with the account that produced it (#619). AI results must
 * carry this tag; [AccountResultGuard] discards results requested under an
 * account that is no longer active, so A's summary can never be written into
 * B's database or shown on B's screen.
 */
data class AccountScopedResult<T>(val accountId: String?, val value: T)

/** Accepts an [AccountScopedResult] only while its account is still active. */
class AccountResultGuard(private val activeAccountId: Flow<String?>) {
    suspend fun <T> accept(result: AccountScopedResult<T>, onStale: () -> Unit = {}): T? {
        if (result.accountId == activeAccountId.first()) return result.value
        onStale()
        return null
    }
}

/**
 * Per-account cleanup hooks (epic #581, #595). The bodies are wired by later
 * issues; this layer only defines the contract and calls all three exactly
 * once after the credentials row has been deleted.
 */
interface AccountCleanup {
    suspend fun deleteDatabase(accountId: String)
    suspend fun deleteAiKeys(accountId: String)
    suspend fun deleteExports(accountId: String)
}

/** Default for callers with no cleanup wiring; never deletes real data. */
object NoOpAccountCleanup : AccountCleanup {
    override suspend fun deleteDatabase(accountId: String) = Unit
    override suspend fun deleteAiKeys(accountId: String) = Unit
    override suspend fun deleteExports(accountId: String) = Unit
}

/**
 * The display name failed the 1..80-after-trim rule. The register form (#599)
 * validates the same rule locally, so this is a defensive data-layer guard;
 * it is deliberately not an [AccountError] variant, which #589 fixes at six.
 */
class InvalidDisplayNameException : IllegalArgumentException(
    "display name must be 1..${AccountManager.MAX_DISPLAY_NAME_LENGTH} characters after trimming",
)

/**
 * Creation and lookup of local accounts (epic #581).
 *
 * Every method returns [Result] with a typed failure: [AccountException] for
 * the [AccountError] vocabulary, [InvalidDisplayNameException] for name
 * validation. Nothing here logs the password, the derived hash or the salt —
 * in particular [signIn] never logs the address next to a failure, so a real
 * account cannot be identified from the log.
 *
 * Active-account state lives in [sessionStore] (the shared settings DataStore)
 * and is observed through [activeAccountId]. When no store is injected the
 * app is treated as signed out; the unowned AppGraph/SettingsStore wiring must
 * pass the single shared DataStore in (see issue #592's integration note).
 *
 * Other collaborators without an implementation yet are injected as hooks:
 *  - [openAccountDatabase] / [closeAccountDatabase] — #601's per-account
 *    AppDatabase provider; [cancelAccountWork] — #612; no-op defaults.
 *  - [performDummyHash] / [updateCredentials] — the timing oracle defense and
 *    #586's rehash-on-login. Their defaults are real and self-contained here;
 *    #586 will replace the delete+insert persistence with a transactional
 *    UPDATE in [AccountDao] without needing to edit this lane's file.
 */
class AccountManager(
    private val dao: AccountDao,
    private val hasher: PasswordHasher = PasswordHasher(),
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sessionStore: SessionStore? = null,
    private val openAccountDatabase: suspend (accountId: String) -> Unit = {},
    private val closeAccountDatabase: suspend (accountId: String) -> Unit = {},
    private val cancelAccountWork: suspend () -> Unit = {},
    private val onActiveAccountChanged: suspend (String?) -> Unit = {},
    /**
     * Emits whenever the accounts table changes. Wire this to Room's
     * `InvalidationTracker` for the `accounts` table (owned by #601/#582's
     * database); empty by default so the flows still emit their initial value.
     */
    private val accountsChanged: Flow<Unit> = emptyFlow(),
    /** Cleanup hooks, called only after the credentials row is gone. */
    private val cleanup: AccountCleanup = NoOpAccountCleanup,
    /** Joins the cancelled work; the manager applies a timeout around it. */
    private val awaitAccountWork: suspend () -> Unit = {},
    /** Notified if cancellation did not finish within the timeout (no secrets). */
    private val onCancellationTimeout: () -> Unit = {},
    /** Called after a successful swap so account-scoped scopes can be recreated. */
    private val onSwapCompleted: () -> Unit = {},
    private val swapTimeoutMs: Long = 2_000L,
    /** The cost a rehash upgrades to; defaults to the current KDF cost. */
    private val currentIterations: Int = PasswordHasher.DEFAULT_ITERATIONS,
    /** One real PBKDF2 against a throwaway salt, to keep unknown-email timing equal. */
    private val performDummyHash: (password: String) -> Unit = { password ->
        PasswordHasher(currentIterations).hash(password, salt = DUMMY_SALT)
        Unit
    },
    /** Persists a rehashed credential; override in #586 with a transactional UPDATE. */
    private val updateCredentials: suspend (row: AccountEntity, fresh: HashedPassword) -> Unit = { row, fresh ->
        dao.deleteById(row.id)
        dao.insert(row.copy(
            passwordHash = fresh.hash,
            salt = fresh.salt,
            kdfIterations = fresh.iterations,
        ))
    },
) {
    init {
        require(currentIterations > 0) { "currentIterations must be positive" }
    }

    /**
     * Re-emission signal for [activeAccount] and [listAccounts]. Combines Room
     * invalidations with mutations made through this manager, and fires once
     * on collection so readers always get an initial value.
     */
    private val localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    private val changes: Flow<Unit> = merge(accountsChanged, localChanges).onStart { emit(Unit) }

    private fun notifyChanged() {
        localChanges.tryEmit(Unit)
    }

    @Volatile
    private var swapInProgress = false

    private val _swapEpoch = MutableStateFlow(0)

    /** True while the close → persist → open window is in flight. */
    val isSwapping: Boolean get() = swapInProgress

    /** Advances on every completed swap; observers re-fetch when it changes. */
    val epoch: kotlinx.coroutines.flow.StateFlow<Int> get() = _swapEpoch

    /** Database writers call this so a swap window never writes to a wrong file. */
    fun assertWritable() {
        check(!swapInProgress) { "Account swap in progress: database writes are temporarily rejected" }
    }

    /**
     * Creates one local account and returns its credential-free [Account].
     * Validation order is fixed: name, email, password, duplicate check,
     * hash (off the main thread), insert, set active, open database.
     */
    suspend fun signUp(displayName: String, email: String, password: String): Result<Account> {
        val name = displayName.trim()
        if (name.isEmpty() || name.length > MAX_DISPLAY_NAME_LENGTH) {
            return Result.failure(InvalidDisplayNameException())
        }

        val normalized = EmailNormalizer.normalize(email)
            ?: return Result.failure(AccountException(AccountError.InvalidEmail))

        PasswordPolicy.validate(password)?.let { problem ->
            return Result.failure(AccountException(AccountError.WeakPassword(problem)))
        }

        if (dao.findByEmail(normalized) != null) {
            return Result.failure(AccountException(AccountError.EmailTaken))
        }

        // PBKDF2 at the default cost is hundreds of ms: never on Main.
        val hashed = withContext(hashDispatcher) { hasher.hash(password) }

        val id = UUID.randomUUID().toString()
        val timestamp = clock()
        val entity = AccountEntity(
            id = id,
            email = normalized,
            displayName = name,
            passwordHash = hashed.hash,
            salt = hashed.salt,
            kdfIterations = hashed.iterations,
            createdAt = timestamp,
            lastUsedAt = timestamp,
        )

        assertWritable()
        try {
            dao.insert(entity)
        } catch (_: SQLiteConstraintException) {
            // A concurrent sign-up won the unique-index race.
            return Result.failure(AccountException(AccountError.EmailTaken))
        }
        notifyChanged()

        setActiveAccount(id)
        openAccountDatabase(id)
        return Result.success(entity.toAccount())
    }

    /**
     * Verifies a local password and opens the account session.
     *
     * Unknown email, malformed email and wrong password all return the exact
     * same [AccountException] carrying [AccountError.InvalidCredentials]. An
     * unknown or malformed address still performs one PBKDF2 (via
     * [performDummyHash]) so the response cannot be used to enumerate which
     * addresses are registered. Verification always runs on [hashDispatcher].
     * Only a successful sign-in touches `lastUsedAt`, rehashes, sets the
     * active account or opens its database.
     */
    suspend fun signIn(email: String, password: String): Result<Account> {
        val normalized = EmailNormalizer.normalize(email)
        if (normalized == null) {
            withContext(hashDispatcher) { performDummyHash(password) }
            return Result.failure(AccountException(AccountError.InvalidCredentials))
        }

        val row = dao.findByEmail(normalized)
        if (row == null) {
            withContext(hashDispatcher) { performDummyHash(password) }
            return Result.failure(AccountException(AccountError.InvalidCredentials))
        }

        val valid = withContext(hashDispatcher) {
            hasher.verify(password, row.passwordHash, row.salt, row.kdfIterations)
        }
        if (!valid) {
            return Result.failure(AccountException(AccountError.InvalidCredentials))
        }

        val timestamp = clock()
        assertWritable()
        dao.updateLastUsed(row.id, timestamp)

        // #586's lazy rehash: only after a successful verify, and a failed
        // write must never fail the login.
        if (row.kdfIterations < currentIterations) {
            withContext(hashDispatcher) {
                val fresh = PasswordHasher(currentIterations).hash(password)
                try {
                    // Pass the row with the just-updated lastUsedAt so the
                    // rehash write cannot roll that timestamp back.
                    updateCredentials(row.copy(lastUsedAt = timestamp), fresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep the old row and let the user in.
                }
            }
        }
        notifyChanged() // lastUsedAt and any rehash write just changed the row

        setActiveAccount(row.id)
        openAccountDatabase(row.id)
        return Result.success(row.copy(lastUsedAt = timestamp).toAccount())
    }

    /** The active id, or null when signed out. Backed by the shared DataStore. */
    val activeAccountId: Flow<String?> =
        sessionStore?.activeAccountId ?: flowOf(null)

    /**
     * The credential-free account for the active id, or null when no id is
     * stored or its row has gone. Re-emits on id changes and table changes.
     */
    val activeAccount: Flow<Account?> =
        combine(activeAccountId, changes, _swapEpoch) { id, _, epoch -> id to epoch }
            .transformLatest { (id, epoch) ->
                val account = id?.let { dao.findById(it)?.toAccount() }
                // Drop a value computed under an epoch that has since swapped:
                // a late emission from account A must never land as account B.
                if (_swapEpoch.value == epoch) emit(account)
            }
            // The id and the table can change in the same operation (signUp);
            // collapse the duplicate emission that combine would otherwise make.
            .distinctUntilChanged()

    /** All accounts, most recently used first, with no credential material. */
    fun listAccounts(): Flow<List<Account>> =
        changes.map { dao.listAll().map { entity -> entity.toAccount() } }

    /** Persists the active id — null clears it — and notifies observers. */
    suspend fun setActiveAccount(id: String?) {
        sessionStore?.setActiveAccountId(id)
        onActiveAccountChanged(id)
    }

    /**
     * Call on app start. A stored id whose account row no longer exists (a
     * partial wipe, or a removal through another path) is cleared rather than
     * crashing — the app simply comes up signed out.
     */
    suspend fun restoreActiveAccount() {
        val stored = sessionStore?.activeAccountId?.first() ?: return
        if (dao.findById(stored) == null) setActiveAccount(null)
    }

    /**
     * The single account-swap sequence used by both [signOut] and
     * [switchAccount] (#603), in this exact order:
     *  1. cancel account-scoped coroutines (Flows, ViewModels, workers)
     *  2. join them, with a timeout; a timeout is reported without credentials
     *  3. close the old account's database
     *  4. persist the new active id (null clears it)
     *  5. open the new database (rolling back on failure)
     *
     * [swapInProgress] gates database writes for the whole close/open window,
     * and a successful swap bumps the epoch so emissions computed under the
     * previous account are dropped rather than written into the new file.
     *
     * Returns the original open failure (if any) for the caller to map.
     */
    private suspend fun performSwap(newId: String?, previous: String?): Exception? {
        swapInProgress = true
        try {
            cancelAccountWork()
            if (withTimeoutOrNull(swapTimeoutMs) { awaitAccountWork() } == null) {
                onCancellationTimeout()
            }
            if (previous != null) closeAccountDatabase(previous)

            setActiveAccount(newId)

            if (newId != null) {
                try {
                    openAccountDatabase(newId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    try {
                        setActiveAccount(previous)
                        if (previous != null) openAccountDatabase(previous)
                    } catch (_: Exception) {
                        // Best effort: never mask the original open failure.
                    }
                    return e
                }
            }
            _swapEpoch.value++
            onSwapCompleted()
            return null
        } finally {
            swapInProgress = false
        }
    }

    /** Signs out by swapping to no account: clears the id and closes the DB. */
    suspend fun signOut() {
        performSwap(null, activeAccountId.first())
    }

    /**
     * Switches to [id] using the shared [performSwap] sequence. The target is
     * resolved before anything is torn down, and `lastUsedAt` is written only
     * after the new database is open.
     */
    suspend fun switchAccount(id: String): Result<Unit> {
        val previous = activeAccountId.first()
        if (previous == id) return Result.success(Unit) // no-op
        if (dao.findById(id) == null) {
            return Result.failure(AccountException(AccountError.AccountNotFound))
        }

        performSwap(id, previous)?.let { return Result.failure(it) }

        assertWritable()
        dao.updateLastUsed(id, clock())
        notifyChanged()
        return Result.success(Unit)
    }

    /**
     * Removes a local account permanently. The active account is never
     * removable from this layer — that is a hard rule, not just a UI guard.
     * The credentials row is deleted first, so a failing cleanup hook can
     * never leave a half-deleted account with a still-live password.
     */
    suspend fun removeAccount(id: String): Result<Unit> {
        if (activeAccountId.first() == id) {
            return Result.failure(AccountException(AccountError.CannotRemoveActive))
        }
        if (dao.findById(id) == null) {
            return Result.failure(AccountException(AccountError.AccountNotFound))
        }

        assertWritable()
        dao.deleteById(id)
        notifyChanged()

        // A hook failure must not resurrect the row. Attempt every hook and
        // report the first failure so callers know cleanup was incomplete.
        var firstFailure: Exception? = null
        listOf<suspend () -> Unit>(
            { cleanup.deleteDatabase(id) },
            { cleanup.deleteAiKeys(id) },
            { cleanup.deleteExports(id) },
        ).forEach { hook ->
            try {
                hook()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstFailure == null) firstFailure = e
            }
        }
        return firstFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 80
        /** Fixed throwaway salt for the dummy hash; never stored anywhere. */
        val DUMMY_SALT: ByteArray = "0123456789abcdef".toByteArray(Charsets.US_ASCII)
    }
}
