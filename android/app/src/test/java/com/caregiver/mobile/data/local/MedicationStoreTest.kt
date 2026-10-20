package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Medication tracker store against in-memory Room: the home card's whole
 * contract in one place — actives listed in tracking order, day-isolated
 * take marks that toggle cleanly, edits keeping rows in place, and deletes
 * leaving nothing dangling.
 */
@RunWith(RobolectricTestRunner::class)
class MedicationStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: MedicationStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = MedicationStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun onlyActiveMedsAreListedInTrackingOrder() = runTest {
        store.add("r1", "Amlodipine", dose = "5 mg")
        store.add("r2", "Metformin")
        val id = store.add("r1", "Retired pill")

        // The row menu stops tracking without deleting.
        val retired = store.observeActive().first().first { it.id == id }
        store.update(retired.copy(active = false))

        val listed = store.observeActive().first()
        assertEquals(listOf("Amlodipine", "Metformin"), listed.map { it.name })
        assertTrue(listed.none { it.id == id })
    }

    @Test
    fun takenMarksIsolateByDayAndToggleCleanly() = runTest {
        val id = store.add("r1", "Amlodipine")
        val d1 = LocalDate.of(2026, 10, 19)
        val d2 = LocalDate.of(2026, 10, 20)

        store.setTaken(id, d1, true)
        store.setTaken(id, d2, true)

        fun takenIds(date: LocalDate) =
            store.observeIntakes(date.toString()).first()
                .filter { it.times > 0 }.map { it.medicationId }
        assertEquals(listOf(id), takenIds(d1))
        assertEquals(listOf(id), takenIds(d2))

        // Untoggling only clears that one day.
        store.setTaken(id, d1, false)
        assertEquals(emptyList<String>(), takenIds(d1))
        assertEquals(listOf(id), takenIds(d2))

        // A second medication keeps its own marks on the same day.
        val other = store.add("r1", "Metformin")
        store.setTaken(other, d2, true)
        assertEquals(setOf(id, other), takenIds(d2).toSet())
    }

    @Test
    fun updatesEditInPlaceWithoutDroppingTheRow() = runTest {
        val id = store.add("r1", "Amlodipine", dose = "5 mg")
        val before = store.observeActive().first().first { it.id == id }

        store.update(before.copy(name = "Amlodipine", dose = "10 mg", schedule = "Morning"))

        val after = store.observeActive().first().first { it.id == id }
        assertEquals("Amlodipine", after.name)
        assertEquals("10 mg", after.dose)
        assertEquals("Morning", after.schedule)
        assertEquals(true, after.active)
    }

    @Test
    fun deleteDropsTheHistoryWithIt() = runTest {
        val id = store.add("r1", "Amlodipine")
        val day = LocalDate.of(2026, 10, 20)
        store.setTaken(id, day, true)

        store.delete(id)

        assertTrue(store.observeActive().first().isEmpty())
        assertTrue(store.observeIntakes(day.toString()).first().isEmpty())
    }
}
