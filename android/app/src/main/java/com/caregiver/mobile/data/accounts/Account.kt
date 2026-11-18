package com.caregiver.mobile.data.accounts

/**
 * The domain view of a local account (#589, epic #581): what the UI and
 * ViewModels see. Deliberately carries no credential material — no hash,
 * no salt, no KDF parameters — so nothing downstream can leak or misuse
 * them. Persisted rows are [AccountEntity]; convert with [toAccount].
 */
data class Account(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: Long,
    val lastUsedAt: Long,
)

/**
 * Typed failures from the account layer, sealed so `when` blocks in the
 * UI are compiler-checked for exhaustiveness. [WeakPassword] carries the
 * [PasswordProblem] so the caller can localize the specific rule that
 * failed.
 */
sealed interface AccountError {
    /** Sign-up or e-mail check found the address already registered. */
    data object EmailTaken : AccountError

    /** Sign-in failed: unknown e-mail or wrong password (never distinguished). */
    data object InvalidCredentials : AccountError

    /** The password failed [PasswordPolicy]; [problem] says which rule. */
    data class WeakPassword(val problem: PasswordProblem) : AccountError

    /** No account exists for the given id or e-mail. */
    data object AccountNotFound : AccountError

    /** Removal was refused because the account is the active one. */
    data object CannotRemoveActive : AccountError

    /** The e-mail address is not a valid address. */
    data object InvalidEmail : AccountError
}

/** Maps a persisted row to the credential-free domain type. */
fun AccountEntity.toAccount(): Account = Account(
    id = id,
    email = email,
    displayName = displayName,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
)
