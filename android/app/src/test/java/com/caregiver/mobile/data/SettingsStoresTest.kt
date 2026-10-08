package com.caregiver.mobile.data

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The holder returns one store per file name no matter how many callers
 * resolve it — this is what keeps `attachBaseContext` and `AppGraph` from
 * opening two DataStores on the same file.
 */
class SettingsStoresTest {

    private fun name() = "holder-${System.nanoTime()}"

    @Test
    fun sameNameReturnsSameInstanceWithoutRerunningProducer() = runTest {
        val dir = createTempDir()
        var productions = 0
        val key = name()
        val first = SettingsStores.getOrCreate(key) {
            productions++
            File(dir, "s.preferences_pb")
        }
        // The file producer runs lazily on first store use, not on creation.
        first.language.first()
        val second = SettingsStores.getOrCreate(key) {
            error("producer must run once")
        }

        assertSame(first, second)
        assertEquals(1, productions)
    }

    @Test
    fun differentNamesReturnDifferentInstances() {
        val dir = createTempDir()
        val first = SettingsStores.getOrCreate(name()) { File(dir, "a.preferences_pb") }
        val second = SettingsStores.getOrCreate(name()) { File(dir, "b.preferences_pb") }

        assertNotSame(first, second)
    }

    @Test
    fun writesThroughOneHandleReadThroughTheOther() = runTest {
        val dir = createTempDir()
        val key = name()
        val first = SettingsStores.getOrCreate(key) { File(dir, "s.preferences_pb") }
        val second = SettingsStores.getOrCreate(key) { File(dir, "s.preferences_pb") }

        first.setLanguage("en")

        assertEquals("en", second.language.first())
    }

    private fun createTempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "stores-test-${System.nanoTime()}").apply {
            mkdirs()
        }
}
