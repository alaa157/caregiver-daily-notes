package com.caregiver.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.RecipientDto
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
 * Task 8a: a successful refresh writes recipients into Room, and a failing
 * network call still serves the last data from Room.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientRepositoryTest {
    private lateinit var db: AppDatabase
    private val recipientApi = FakeRecipientApi()
    private val apis = FakeBackendApis(FakeAuthApi()).also { it.recipientApi = recipientApi }

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

    @Test
    fun networkRefreshWritesRoomAndNetworkFailureServesIt() = runTest {
        recipientApi.listHandler = {
            listOf(
                RecipientDto(id = "r1", name = "Aisha", active = true),
                RecipientDto(id = "r2", name = "Karim", active = false),
            )
        }
        val repo = RecipientRepository(apis, db.recipientDao())

        // Online: the live result is returned and cached in server order.
        assertEquals(listOf("r1", "r2"), repo.list().map { it.id })

        // Offline: the cached copy is served, not an error.
        recipientApi.listHandler = { throw IOException("offline") }
        val offline = repo.list()
        assertEquals(listOf("r1", "r2"), offline.map { it.id })
        assertEquals(listOf(true, false), offline.map { it.active })
    }

    @Test
    fun offlineWithoutCachePropagates() = runTest {
        recipientApi.listHandler = { throw IOException("offline") }
        val repo = RecipientRepository(apis, db.recipientDao())

        try {
            repo.list()
            fail("expected IOException")
        } catch (e: IOException) {
            // Expected: nothing cached, so the ViewModel keeps its error state.
        }
    }

    @Test
    fun demoModeWithoutDaoIsPurePassthrough() = runTest {
        recipientApi.listHandler = {
            listOf(RecipientDto(id = "r1", name = "Aisha", active = true))
        }
        // Demo graphs inject no DAO: calls go straight to the backend, no disk.
        val repo = RecipientRepository(apis, dao = null)

        assertEquals(listOf("r1"), repo.list().map { it.id })
        assertEquals(0, repo.observeAll().first().size)
    }
}
