package com.caregiver.mobile.presentation.summary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.summary.SavedSummaryStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Saved-summaries list: items come straight from [SavedSummaryStore], a
 * confirmed delete reloads, and a failed delete keeps the visible list.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SavedSummariesViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var store: SavedSummaryStore
    private var idCounter = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = SavedSummaryStore(db, newId = { "saved-${++idCounter}" })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(text: String, flags: List<String> = emptyList()) {
        store.save(
            "r1",
            SummaryResultData(
                summary = SummaryDto(
                    id = "", recipientId = "r1", periodDays = 7,
                    text = text, redFlags = flags, evidence = emptyList(),
                    uncertainties = emptyList(),
                ),
                periodStart = "2026-10-04",
                periodEnd = "2026-10-10",
                generatedAt = 1_000L,
            ),
        )
    }

    private fun content(state: SavedSummariesState) =
        state as SavedSummariesState.Content

    @Test
    fun listViewModelMapsItems() = runTest {
        seed("first", flags = listOf("FALL_REPORTED"))
        seed("second")
        val vm = SavedSummariesViewModel("r1", store, TestScope(UnconfinedTestDispatcher()))

        val state = vm.state.first { it is SavedSummariesState.Content }

        val items = content(state).items
        assertEquals(2, items.size)
        assertEquals(setOf("saved-1", "saved-2"), items.map { it.id }.toSet())
        assertTrue(items.first { it.id == "saved-1" }.hasFlags)
        assertTrue(!items.first { it.id == "saved-2" }.hasFlags)
        assertEquals(7, items.first().periodDays)
        assertEquals("2026-10-04", items.first().periodStart)
        assertEquals("2026-10-10", items.first().periodEnd)
    }

    @Test
    fun deletingRemovesTheRow() = runTest {
        seed("first")
        seed("second")
        val vm = SavedSummariesViewModel("r1", store, TestScope(UnconfinedTestDispatcher()))
        vm.state.first { it is SavedSummariesState.Content }

        vm.delete("saved-1")

        val state = vm.state.first {
            it is SavedSummariesState.Content && it.items.size == 1
        }
        assertEquals("saved-2", content(state).items.single().id)
    }

    @Test
    fun deleteFailureKeepsTheList() = runTest {
        seed("first")
        seed("second")
        val vm = SavedSummariesViewModel(
            "r1", store, TestScope(UnconfinedTestDispatcher()),
            deleteRow = { throw java.io.IOException("disk full") },
        )
        vm.state.first { it is SavedSummariesState.Content }

        vm.delete("saved-1")

        vm.deleteError.first { it }
        val state = vm.state.value
        assertTrue(state is SavedSummariesState.Content)
        assertEquals(2, content(state).items.size)
    }
}
