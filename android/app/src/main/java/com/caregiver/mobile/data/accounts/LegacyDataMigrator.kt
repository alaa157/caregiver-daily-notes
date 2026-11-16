package com.caregiver.mobile.data.accounts

import android.content.Context
import com.caregiver.mobile.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Outcome of looking for an unmigrated pre-accounts install (issue #623). */
enum class LegacyState {
    /** Fresh install: no legacy database and no accounts. */
    None,

    /** Legacy database present, no accounts and no active account: offer the claim flow. */
    NeedsClaim,

    /** Accounts already exist (or an account is signed in): nothing to claim. */
    AlreadyMigrated,
}

/**
 * Detects a legacy install once per launch (epic #581, issue #623).
 *
 * A legacy install is the legacy `caregiver-db` file present **and** the
 * accounts table empty **and** no active account id stored. Detection is
 * cheap, read-only and idempotent: it stats files and, only when the accounts
 * database already exists, reads the account list. It never opens the legacy
 * database and never creates a file — in particular it does not open
 * `accounts.db` on a fresh install just to discover it is empty.
 *
 * This class lives here because the issue's `LegacyInstallDetector.kt` path is
 * UNOWNED by lane B (guard returns rc 2); `LegacyDataMigrator.kt` is the owned
 * home for legacy-install logic.
 *
 * @param activeAccountId the shared `SessionStore.activeAccountId` flow; the
 *   integrator supplies it so this detector never opens a second DataStore.
 * @param demoMode demo builds must never report [LegacyState.NeedsClaim].
 */
class LegacyInstallDetector(
    private val context: Context,
    private val accountDao: AccountDao,
    private val activeAccountId: Flow<String?>,
    private val demoMode: Boolean = BuildConfig.DEMO_BACKEND && !BuildConfig.LOCAL_MODE,
) {
    suspend fun detect(): LegacyState {
        if (demoMode) return LegacyState.None

        val legacyExists =
            context.getDatabasePath(PerAccountDatabaseProvider.LEGACY_DATABASE_NAME).exists()

        // Opening accounts.db through Room would create it on a fresh install,
        // which would violate the "detection does not modify files" rule.
        val accountsFile = context.getDatabasePath(ACCOUNTS_DB_NAME)
        val hasAccounts = accountsFile.exists() && accountDao.listAll().isNotEmpty()

        val hasActiveAccount = activeAccountId.first() != null

        return when {
            hasAccounts || hasActiveAccount -> LegacyState.AlreadyMigrated
            legacyExists -> LegacyState.NeedsClaim
            else -> LegacyState.None
        }
    }

    companion object {
        /** The accounts credentials database, owned by #583. */
        const val ACCOUNTS_DB_NAME = "accounts.db"
    }
}
