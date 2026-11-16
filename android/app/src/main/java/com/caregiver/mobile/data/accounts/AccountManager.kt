package com.caregiver.mobile.data.accounts

import androidx.sqlite.SQLiteConstraintException
import java.util.UUID
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
 * validation. Nothing here logs the password, the derived hash or the salt.
 *
 * The two collaborators for "the new account is now active" and "open its
 * database" are injected as suspend hooks. Their real implementations are
 * #592 (persist the active id) and #601 (the per-account AppDatabase
 * provider); neither exists yet, so the defaults are no-ops and the wiring
 * belongs to those issues. signUp calls them in the documented order and
 * the tests assert the calls with recording fakes.
 */
class AccountManager(
    private val dao: AccountDao,
    private val hasher: PasswordHasher = PasswordHasher(),
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val setActiveAccount: suspend (accountId: String) -> Unit = {},
    private val openAccountDatabase: suspend (accountId: String) -> Unit = {},
) {
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

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 80
    }
}
