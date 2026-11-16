package com.caregiver.mobile.data.accounts

import android.content.Context
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What [SignInThrottle.checkAllowed] decided for one attempt. */
sealed interface ThrottleState {
    /** The attempt may proceed to password verification. */
    data object Allowed : ThrottleState

    /** The attempt is locked; [retryAfterMillis] is how long remains. */
    data class Locked(val retryAfterMillis: Long) : ThrottleState
}

/** One persisted throttle row: a counter and a timestamp, nothing else. */
data class ThrottleRecord(
    val consecutiveFailures: Int,
    val lockedUntilMillis: Long,
)

/**
 * Storage behind [SignInThrottle]. Only a counter and a timestamp per
 * normalized email are ever stored — never a password or derived value.
 * Production uses [SharedPreferencesThrottleStore]; tests use an in-memory
 * fake, which is also what makes "survives a process restart" testable
 * without Robolectric.
 */
interface ThrottleStore {
    suspend fun load(email: String): ThrottleRecord?
    suspend fun save(email: String, record: ThrottleRecord)
    suspend fun clear(email: String)
}

/**
 * SharedPreferences-backed [ThrottleStore]. Two keys per email under the
 * `signin_throttle.` prefix; values are plain Int/Long, no credential
 * material by construction.
 */
class SharedPreferencesThrottleStore(context: Context) : ThrottleStore {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(email: String): ThrottleRecord? {
        if (!prefs.contains(key(email, KEY_FAILURES))) return null
        return ThrottleRecord(
            consecutiveFailures = prefs.getInt(key(email, KEY_FAILURES), 0),
            lockedUntilMillis = prefs.getLong(key(email, KEY_LOCKED_UNTIL), 0L),
        )
    }

    override suspend fun save(email: String, record: ThrottleRecord) {
        prefs.edit()
            .putInt(key(email, KEY_FAILURES), record.consecutiveFailures)
            .putLong(key(email, KEY_LOCKED_UNTIL), record.lockedUntilMillis)
            .apply()
    }

    override suspend fun clear(email: String) {
        prefs.edit()
            .remove(key(email, KEY_FAILURES))
            .remove(key(email, KEY_LOCKED_UNTIL))
            .apply()
    }

    private fun key(email: String, field: String) = "signin_throttle.$field.$email"

    private companion object {
        const val PREFS_NAME = "signin_throttle"
        const val KEY_FAILURES = "failures"
        const val KEY_LOCKED_UNTIL = "locked_until"
    }
}

/**
 * Per-email sign-in failure throttle (#596, epic #581; P2).
 *
 * After [FAILURE_THRESHOLD] consecutive failures for one normalized email,
 * attempts are locked for a delay that grows along [LOCK_DELAYS_MILLIS] and
 * caps at five minutes. The counter and lock deadline persist through
 * [ThrottleStore], so a process restart does not reset the penalty. A
 * successful sign-in clears the record via [recordSuccess].
 *
 * `AccountManager.signIn` (lane c) must call [checkAllowed] **before** any
 * hashing so a locked account does not burn PBKDF2 CPU, then
 * [recordFailure] on a failed verify and [recordSuccess] on success. This
 * class is pure Kotlin apart from the [SharedPreferencesThrottleStore]
 * implementation detail; the policy itself is JVM-testable.
 */
class SignInThrottle(
    private val store: ThrottleStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    /**
     * Whether an attempt for [email] may proceed. A locked email returns
     * [ThrottleState.Locked] with the remaining delay; nothing is recorded —
     * the caller must not run the hash or count the attempt as a new failure.
     */
    suspend fun checkAllowed(email: String): ThrottleState = mutex.withLock {
        val key = throttleKey(email)
        val record = store.load(key) ?: return@withLock ThrottleState.Allowed
        val remaining = record.lockedUntilMillis - now()
        if (remaining > 0) ThrottleState.Locked(remaining) else ThrottleState.Allowed
    }

    /**
     * Records one failed verification for [email]. Once the consecutive
     * failure count reaches [FAILURE_THRESHOLD] the record is locked until
     * `now + delay`, where the delay climbs [LOCK_DELAYS_MILLIS] and caps at
     * the last entry (five minutes). While already locked, further failures
     * extend the lock along the same ladder.
     */
    suspend fun recordFailure(email: String) = mutex.withLock {
        val key = throttleKey(email)
        val record = store.load(key) ?: ThrottleRecord(0, 0L)
        val failures = record.consecutiveFailures + 1
        val lockedUntil = if (failures >= FAILURE_THRESHOLD) {
            now() + delayFor(failures)
        } else {
            record.lockedUntilMillis
        }
        store.save(key, ThrottleRecord(failures, lockedUntil))
    }

    /** Clears the failure record for [email] after a successful sign-in. */
    suspend fun recordSuccess(email: String) = mutex.withLock {
        store.clear(throttleKey(email))
    }

    private fun throttleKey(email: String): String =
        EmailNormalizer.normalize(email) ?: email.trim().lowercase(Locale.ROOT)

    private fun delayFor(consecutiveFailures: Int): Long {
        val index = (consecutiveFailures - FAILURE_THRESHOLD)
            .coerceIn(0, LOCK_DELAYS_MILLIS.size - 1)
        return LOCK_DELAYS_MILLIS[index]
    }

    companion object {
        const val FAILURE_THRESHOLD = 5

        /** Lock delay per failure beyond the threshold, capped at 5 minutes. */
        val LOCK_DELAYS_MILLIS = longArrayOf(5_000L, 15_000L, 60_000L, 300_000L)
    }
}
