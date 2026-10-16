package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Task 8a: recipient cache DAO against in-memory Room (insert, query, replace on refresh). */
@RunWith(RobolectricTestRunner::class)
class RecipientDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: RecipientDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.recipientDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(serverId: String, name: String, active: Boolean = true) =
        RecipientEntity(serverId = serverId, name = name, active = active)

    @Test
    fun insertAndQuery() = runTest {
        dao.insertAll(listOf(row("r1", "Aisha"), row("r2", "Karim", active = false)))

        assertEquals(listOf("Aisha", "Karim"), dao.all().map { it.name })
        assertEquals("Karim", dao.byServerId("r2")?.name)
        assertEquals(1, dao.observeAll().first().count { it.active })
    }

    @Test
    fun replaceOnRefreshDropsStaleRows() = runTest {
        dao.replaceAll(listOf(row("r1", "Aisha"), row("r2", "Karim")))

        // The next refresh no longer lists r2 and renames r1.
        dao.replaceAll(listOf(row("r1", "Aisha Hassan"), row("r3", "Noor")))

        val rows = dao.all()
        assertEquals(listOf("Aisha Hassan", "Noor"), rows.map { it.name })
        assertNull(rows.firstOrNull { it.serverId == "r2" })
    }

    @Test
    fun insertReplacesTheRowWithTheSameServerId() = runTest {
        dao.insertAll(listOf(row("r1", "Aisha")))
        dao.insertAll(listOf(row("r1", "Aisha Hassan", active = false)))

        assertEquals(1, dao.all().size)
        assertEquals("Aisha Hassan", dao.byServerId("r1")?.name)
    }
}
