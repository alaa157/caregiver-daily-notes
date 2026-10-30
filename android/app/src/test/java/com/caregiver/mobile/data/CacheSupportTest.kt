package com.caregiver.mobile.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Cache writes stay best-effort, but their failures are observable through
 * [CacheHealth] instead of disappearing; cancellation is not swallowed.
 */
class CacheSupportTest {

    @Before
    fun setUp() {
        CacheHealth.resetForTest()
    }

    @After
    fun tearDown() {
        CacheHealth.resetForTest()
    }

    @Test
    fun aFailedWriteIsRecordedWithoutFailingTheCaller() = runTest {
        cacheWrite { throw IOException("disk full") }

        val failure = CacheHealth.failures.value
        assertNotNull(failure)
        assertEquals(1, failure!!.count)
        assertEquals("IOException", failure.type)
        assertEquals("disk full", failure.message)
    }

    @Test
    fun repeatedFailuresAccumulateInTheReportedCount() = runTest {
        cacheWrite { throw IOException("first") }
        cacheWrite { throw IOException("second") }

        val failure = CacheHealth.failures.value!!
        assertEquals(2, failure.count)
        assertEquals("second", failure.message)
    }

    @Test
    fun aSuccessfulWriteClearsTheDegradedState() = runTest {
        cacheWrite { throw IOException("disk full") }

        cacheWrite { }

        assertNull(CacheHealth.failures.value)
    }

    @Test
    fun cancellationIsNotTreatedAsACacheFailure() = runTest {
        try {
            cacheWrite { throw CancellationException("cancelled") }
            fail("expected cancellation to propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }

        assertNull(CacheHealth.failures.value)
    }
}
