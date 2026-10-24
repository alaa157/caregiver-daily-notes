package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Task 4: plan counts and recipient deletes stay scoped to one recipient. */
@RunWith(RobolectricTestRunner::class)
class PlanDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PlanDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.planDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun plan(id: String, recipientId: String) = PlanEntity(
        id = id,
        recipientId = recipientId,
        versionsJson = "[]",
        updatedAt = 1L,
    )

    @Test
    fun recipientScopedCountAndDeleteLeaveOtherPlans() = runTest {
        dao.upsert(plan("p1", "r1"))
        dao.upsert(plan("p2", "r1"))
        dao.upsert(plan("p3", "r2"))

        assertEquals(2, dao.countByRecipient("r1"))
        assertEquals(1, dao.countByRecipient("r2"))
        assertEquals(0, dao.countByRecipient("missing"))

        dao.deleteByRecipient("r1")
        assertEquals(0, dao.countByRecipient("r1"))
        assertEquals(listOf("p3"), dao.all().map { it.id })

        dao.deleteByRecipient("r1")
        assertEquals(listOf("p3"), dao.all().map { it.id })
    }
}
