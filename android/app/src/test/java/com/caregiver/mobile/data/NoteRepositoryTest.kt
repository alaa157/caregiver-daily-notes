package com.caregiver.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.local.AppDatabase
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 8a: a successful refresh writes notes into Room, and a failing
 * network call still serves the last data from Room.
 */
@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {
    private lateinit var db: AppDatabase
    private val noteApi = FakeNoteApi()
    private val apis = FakeBackendApis(FakeAuthApi()).also { it.noteApi = noteApi }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun expectIOException(block: suspend () -> Unit) {
        try {
            block()
            fail("expected the transport failure to propagate")
        } catch (e: IOException) {
            // Expected: nothing is cached, so the failure surfaces as before Task 8a.
        }
    }

    @Test
    fun networkRefreshWritesRoomAndNetworkFailureServesIt() = runTest {
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n1", text = "Alpha note")) }
        val repo = NoteRepository(apis, db.noteDao())

        // Online: the live result is returned and cached.
        assertEquals(listOf("n1"), repo.history(null, null, null).map { it.id })
        assertEquals(listOf("n1"), repo.observeAll().first().map { it.id })

        // Offline: the same query is served from Room, not an error.
        noteApi.historyHandler = { _, _, _ -> throw IOException("network down") }
        val offline = repo.history(null, null, null)
        assertEquals(listOf("n1"), offline.map { it.id })
        assertEquals("Alpha note", offline.single().text)
    }

    @Test
    fun fallbackHonorsTheQueryScopeAndThrowsWithoutCache() = runTest {
        noteApi.historyHandler = { recipientId, _, _ ->
            if (recipientId == "r1") listOf(testNote("n1", recipientId = "r1")) else emptyList()
        }
        val repo = NoteRepository(apis, db.noteDao())
        repo.history("r1", null, null)

        noteApi.historyHandler = { _, _, _ -> throw IOException("network down") }
        assertEquals(listOf("n1"), repo.history("r1", null, null).map { it.id })
        // Another scope has no cached copy: the failure propagates as before.
        expectIOException { repo.history("r2", null, null) }
    }

    @Test
    fun refreshReplacesTheQueriedScope() = runTest {
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n1"), testNote("n2")) }
        val repo = NoteRepository(apis, db.noteDao())
        repo.history(null, null, null)

        // The server no longer returns n1: the refresh replaces the scope.
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n2")) }
        repo.history(null, null, null)
        assertEquals(listOf("n2"), repo.observeAll().first().map { it.id })

        // And the offline path serves that replaced copy.
        noteApi.historyHandler = { _, _, _ -> throw IOException("network down") }
        assertEquals(listOf("n2"), repo.history(null, null, null).map { it.id })
    }

    @Test
    fun demoModeWithoutDaoIsPurePassthrough() = runTest {
        noteApi.historyHandler = { _, _, _ -> listOf(testNote("n1")) }
        // Demo graphs inject no DAO: calls go straight to the backend, no disk.
        val repo = NoteRepository(apis, dao = null)

        assertEquals(listOf("n1"), repo.history(null, null, null).map { it.id })
        assertEquals(0, repo.observeAll().first().size)

        noteApi.historyHandler = { _, _, _ -> throw IOException("offline") }
        expectIOException { repo.history(null, null, null) }
    }
}
