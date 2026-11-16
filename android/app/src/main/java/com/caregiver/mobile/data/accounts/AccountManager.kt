package com.caregiver.mobile.data.accounts

import androidx.sqlite.SQLiteConstraintException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
 * Collaborators without an implementation yet are injected as hooks:
 *  - [setActiveAccount] / [openAccountDatabase] — #592 (persist active id) and
 *    #601 (per-account AppDatabase provider); no-op defaults for now.
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
    private val setActiveAccount: suspend (accountId: String) -> Unit = {},
    private val openAccountDatabase: suspend (accountId: String) -> Unit = {},
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

        setActiveAccount(row.id)
        openAccountDatabase(row.id)
        return Result.success(row.copy(lastUsedAt = timestamp).toAccount())
    }

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 80
        /** Fixed throwaway salt for the dummy hash; never stored anywhere. */
        val DUMMY_SALT: ByteArray = "0123456789abcdef".toByteArray(Charsets.US_ASCII)
    }
}
