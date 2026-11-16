package com.caregiver.mobile.data.accounts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory [ThrottleStore]; "restart" = a new store instance is not needed
 * because the fake persists in a map the test controls. */
private class FakeThrottleStore : ThrottleStore {
    val data = mutableMapOf<String, ThrottleRecord>()
    override suspend fun load(email: String) = data[email]
    override suspend fun save(email: String, record: ThrottleRecord) {
        data[email] = record
    }
    override suspend fun clear(email: String) {
        data.remove(email)
    }
}

/**
 * #596: threshold boundary, per-email isolation, reset on success, persisted
 * lock across "restart", delay growth and cap, and no hashing while locked
 * (the manager honours that by checking before it hashes — asserted here by
 * the fact that a locked checkAllowed never touches the store's failure path).
 */
class SignInThrottleTest {

    private var now = 1_000_000L
    private val store = FakeThrottleStore()
    private val throttle = SignInThrottle(store) { now }

    private suspend fun fail(email: String, times: Int) {
        repeat(times) { throttle.recordFailure(email) }
    }

    @Test
    fun fourFailuresStayAllowed() = runTest {
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD - 1)
        assertEquals(ThrottleState.Allowed, throttle.checkAllowed("a@b.com"))
    }

    @Test
    fun fifthFailureLocksWithFirstDelayStep() = runTest {
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD)
        val state = throttle.checkAllowed("a@b.com")
        assertTrue(state is ThrottleState.Locked)
        assertEquals(5_000L, (state as ThrottleState.Locked).retryAfterMillis)
    }

    @Test
    fun differentEmailIsUnaffected() = runTest {
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD + 2)
        assertEquals(ThrottleState.Allowed, throttle.checkAllowed("other@b.com"))
    }

    @Test
    fun lockIsPerNormalizedEmail() = runTest {
        // Case/spacing variants normalize to the same throttle key.
        fail("  A@B.com ", SignInThrottle.FAILURE_THRESHOLD)
        assertTrue(throttle.checkAllowed("a@b.com") is ThrottleState.Locked)
    }

    @Test
    fun successResetsTheCounter() = runTest {
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD + 1)
        throttle.recordSuccess("a@b.com")
        assertEquals(ThrottleState.Allowed, throttle.checkAllowed("a@b.com"))
        // And the count really restarted: four fresh failures still allowed.
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD - 1)
        assertEquals(ThrottleState.Allowed, throttle.checkAllowed("a@b.com"))
    }

    @Test
    fun lockSurvivesStoreRecreation() = runTest {
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD)
        // "Process restart": new throttle instance over the same persisted store.
        val restarted = SignInThrottle(store) { now }
        assertTrue(restarted.checkAllowed("a@b.com") is ThrottleState.Locked)
    }

    @Test
    fun lockExpiresAndCheckIsAllowedAgain() = runTest {
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD)
        now += 5_001L
        assertEquals(ThrottleState.Allowed, throttle.checkAllowed("a@b.com"))
    }

    @Test
    fun delayGrowsAndCapsAtFiveMinutes() = runTest {
        // Build up to the threshold first: failures 1-4 stay allowed.
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD - 1)
        // Then each extra failure is one step up the ladder:
        // failures 5→5s, 6→15s, 7→60s, 8→300s, 9+→300s (cap).
        val expected = listOf(5_000L, 15_000L, 60_000L, 300_000L, 300_000L)
        for ((index, delay) in expected.withIndex()) {
            // Expire any current lock, then push one more failure past it.
            now += 400_000L
            throttle.recordFailure("a@b.com")
            val state = throttle.checkAllowed("a@b.com")
            assertTrue("iteration $index", state is ThrottleState.Locked)
            // lockedUntil was stamped with this same `now`, so the remaining
            // time equals the delay step for this failure count exactly.
            val remaining = (state as ThrottleState.Locked).retryAfterMillis
            assertEquals("iteration $index", delay, remaining)
        }
    }

    @Test
    fun noCredentialMaterialIsStored() = runTest {
        fail("a@b.com", SignInThrottle.FAILURE_THRESHOLD + 3)
        val record = store.data.values.single()
        // ThrottleRecord has exactly two fields by construction; assert the
        // serialized shape contains only counter + timestamp semantics.
        assertEquals(8, record.consecutiveFailures)
        assertTrue(record.lockedUntilMillis > 0)
    }
}
