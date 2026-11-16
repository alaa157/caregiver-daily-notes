package com.caregiver.mobile.data.accounts

import androidx.sqlite.SQLiteConstraintException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * A typed [AccountError] carried through the manager's [Result] failures.
 * The six variants stay defined by #589; this wrapper only makes them
 * throwable so signUp/signIn can share the [Result] shape (#591).
 */
data class AccountException(val error: AccountError) : Exception(error.toString())

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
        combine(activeAccountId, changes) { id, _ -> id }
            .map { id -> id?.let { dao.findById(it)?.toAccount() } }
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
     * Clears the stored id, closes the active account's database and cancels
     * its scoped work. [activeAccountId] emits null once the key is cleared.
     */
    suspend fun signOut() {
        val id = activeAccountId.first()
        setActiveAccount(null)
        if (id != null) {
            closeAccountDatabase(id)
            cancelAccountWork()
        }
    }

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 80
        /** Fixed throwaway salt for the dummy hash; never stored anywhere. */
        val DUMMY_SALT: ByteArray = "0123456789abcdef".toByteArray(Charsets.US_ASCII)
    }
}
