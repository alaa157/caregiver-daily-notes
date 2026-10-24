package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 8b: the outbox stores offline notes, filters them with the same
 * scope as history (null = wildcard), and supports the status/delete
 * writes Task 8c will drive.
 */
@RunWith(RobolectricTestRunner::class)
class OutboxDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: OutboxDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.outboxDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        localId: String,
        recipientId: String = "r1",
        date: String = "2026-10-07",
        createdAt: Long = 1L,
    ) = OutboxEntity(
        localId = localId,
        recipientId = recipientId,
        mood = "good",
        appetite = "good",
        sleep = "ok",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 2,
        fall = false,
        text = "note $localId",
        date = date,
        createdAt = createdAt,
    )

    @Test
    fun queryHonorsTheHistoryScope() = runTest {
        dao.insert(row("o1", recipientId = "r1", date = "2026-10-01"))
        dao.insert(row("o2", recipientId = "r2", date = "2026-10-07"))
        dao.insert(row("o3", recipientId = "r1", date = "2026-10-15", createdAt = 2L))

        assertEquals(listOf("o3", "o1"), dao.query("r1", null, null).map { it.localId })
        assertEquals(listOf("o2"), dao.query(null, "2026-10-05", "2026-10-10").map { it.localId })
        assertEquals(listOf("o3"), dao.query("r1", "2026-10-10", null).map { it.localId })
    }

    @Test
    fun queryReturnsNewestFirstWithinADate() = runTest {
        dao.insert(row("older", createdAt = 5L))
        dao.insert(row("newer", createdAt = 9L))

        assertEquals(
            listOf("newer", "older"),
            dao.query(null, null, null).map { it.localId },
        )
    }

    @Test
    fun statusUpdatesAndDeleteMatchThe8cContract() = runTest {
        dao.insert(row("o1"))
        assertEquals(OutboxStatus.PENDING, dao.byLocalId("o1")?.status)

        dao.updateStatus("o1", OutboxStatus.SYNCING)
        assertEquals(OutboxStatus.SYNCING, dao.byLocalId("o1")?.status)

        dao.updateStatus("o1", OutboxStatus.FAILED)
        assertEquals(OutboxStatus.FAILED, dao.byLocalId("o1")?.status)

        dao.delete("o1")
        assertNull(dao.byLocalId("o1"))
    }

    @Test
    fun recipientScopedCountAndDeleteLeaveOtherQueues() = runTest {
        dao.insert(row("o1", recipientId = "r1"))
        dao.insert(row("o2", recipientId = "r1"))
        dao.insert(row("o3", recipientId = "r2"))

        assertEquals(2, dao.countByRecipient("r1"))

        dao.deleteByRecipient("r1")
        assertEquals(0, dao.countByRecipient("r1"))
        assertEquals(listOf("o3"), dao.query(null, null, null).map { it.localId })

        dao.deleteByRecipient("r1")
        assertEquals(listOf("o3"), dao.query(null, null, null).map { it.localId })
    }
}
