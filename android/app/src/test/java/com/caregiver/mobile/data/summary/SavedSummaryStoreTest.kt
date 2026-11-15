package com.caregiver.mobile.data.summary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.ai.TrendDto
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.SavedSummaryEntity
import com.caregiver.mobile.presentation.summary.SummaryResultData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedSummaryStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: SavedSummaryStore
    private var idCounter = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = SavedSummaryStore(db, newId = { "id-${++idCounter}" })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun data(
        recipientPeriodDays: Int = 7,
        periodStart: String? = "2026-09-29",
        periodEnd: String? = "2026-10-05",
        generatedAt: Long? = 1_000L,
    ) = SummaryResultData(
        summary = SummaryDto(
            id = "", recipientId = "r1", periodDays = recipientPeriodDays,
            text = "ok", redFlags = emptyList(), evidence = emptyList(),
            uncertainties = emptyList(),
        ),
        trends = listOf(
            TrendDto(metric = "notes", previousCount = 1, currentCount = 2, delta = 1, previousSampleSize = 3, currentSampleSize = 3),
        ),
        periodStart = periodStart,
        periodEnd = periodEnd,
        generatedAt = generatedAt,
    )

    @Test
    fun saveThenGetRoundTrips() = runTest {
        val id = store.save("r1", data())

        val loaded = store.get(id)

        assertEquals("id-1", loaded?.savedId)
        assertEquals("2026-09-29", loaded?.periodStart)
        assertEquals("2026-10-05", loaded?.periodEnd)
        assertEquals(1_000L, loaded?.generatedAt)
        assertEquals("ok", loaded?.summary?.text)
        assertEquals(1, loaded?.trends?.size)
    }

    @Test
    fun twentyFirstSavePrunesTheOldest() = runTest {
        repeat(21) { i ->
            store.save("r1", data(generatedAt = 1_000L + i))
        }

        val items = store.list("r1")

        assertEquals(20, items.size)
        // Newest first: the very first save (generatedAt 1000) is gone.
        assertEquals(1_020L, items.first().createdAt)
        assertEquals(1_001L, items.last().createdAt)
    }

    @Test
    fun missingDatesThrow() = runTest {
        listOf(
            data(periodStart = null),
            data(periodEnd = null),
            data(generatedAt = null),
        ).forEach { bad ->
            try {
                store.save("r1", bad)
                fail("expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        assertTrue(store.list("r1").isEmpty())
    }

    @Test
    fun corruptPayloadIsSkipped() = runTest {
        store.save("r1", data())
        db.savedSummaryDao().insert(
            SavedSummaryEntity(
                id = "bad", recipientId = "r1", periodDays = 7,
                periodStart = "2026-09-29", periodEnd = "2026-10-05",
                createdAt = 2_000L, aiGenerated = false,
                provider = null, model = null, payload = "{not json",
            ),
        )

        val items = store.list("r1")

        assertEquals(1, items.size)
        assertEquals("id-1", items[0].id)
        assertNull(store.get("bad"))
        // latest() skips the corrupt row too — newest by createdAt is "bad".
        assertEquals("id-1", store.latest("r1")?.id)
    }

    @Test
    fun anotherRecipientsRowsAreUntouched() = runTest {
        store.save("r1", data())
        repeat(20) { store.save("r2", data(generatedAt = 2_000L + it)) }

        assertEquals(1, store.list("r1").size)
        assertEquals(20, store.list("r2").size)
    }
}
