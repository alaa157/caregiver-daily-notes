package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.AppendVersionRequest
import com.caregiver.mobile.data.api.SuggestPlanRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlanAppendVersionTest {
    private lateinit var db: AppDatabase

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
    fun appendVersionPreservesItemsWhenOmitted() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1", "item2")))
        val updated = apis.plans().appendVersion(plan.id, AppendVersionRequest(status = "Accepted", items = null))

        val latest = updated.versions.maxByOrNull { it.version }!!
        assertEquals(listOf("item1", "item2"), latest.items)
        assertEquals("Accepted", latest.status)
    }
}
