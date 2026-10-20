package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Doses a day: a twice-daily medication needs two marks, and taking one
 * dose must not touch the other.
 */
@RunWith(RobolectricTestRunner::class)
class MedicationDosesPerDayTest {
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

    private suspend fun dosesTaken(id: String, day: LocalDate): Set<Int> =
        store.observeIntakes(day.toString()).first()
            .filter { it.medicationId == id && it.times > 0 }
            .mapNotNull { it.doseIndex() }
            .toSet()

    @Test
    fun aTwiceDailyMedicationHoldsTwoMarks() = runTest {
        val id = store.add("r1", "Amlodipine", dosesPerDay = 2)
        val day = LocalDate.of(2026, 10, 20)

        store.setTaken(id, day, 0, true)
        store.setTaken(id, day, 1, true)

        assertEquals(setOf(0, 1), dosesTaken(id, day))
    }

    @Test
    fun takingTheSecondDoseLeavesTheFirstAlone() = runTest {
        val id = store.add("r1", "Amlodipine", dosesPerDay = 2)
        val day = LocalDate.of(2026, 10, 20)

        store.setTaken(id, day, 0, true)
        store.setTaken(id, day, 1, true)
        // Un-taking the second dose must not clear the first.
        store.setTaken(id, day, 1, false)

        assertEquals(setOf(0), dosesTaken(id, day))
    }

    @Test
    fun aDoseCanBeTakenWithoutTheEarlierOne() = runTest {
        val id = store.add("r1", "Amlodipine", dosesPerDay = 2)
        val day = LocalDate.of(2026, 10, 20)

        store.setTaken(id, day, 1, true)

        assertEquals(setOf(1), dosesTaken(id, day))
    }

    @Test
    fun dosesAreIsolatedByDay() = runTest {
        val id = store.add("r1", "Amlodipine", dosesPerDay = 2)
        val d1 = LocalDate.of(2026, 10, 19)
        val d2 = LocalDate.of(2026, 10, 20)

        store.setTaken(id, d1, 0, true)
        store.setTaken(id, d2, 0, true)

        assertEquals(setOf(0), dosesTaken(id, d1))
        assertEquals(setOf(0), dosesTaken(id, d2))
    }

    @Test
    fun aMedicationKeepsItsDosesWhenEdited() = runTest {
        val id = store.add("r1", "Amlodipine", dosesPerDay = 2)
        val day = LocalDate.of(2026, 10, 20)
        store.setTaken(id, day, 0, true)

        val med = db.medicationDao().observeActive().first().first { it.id == id }
        store.update(med.copy(name = "Amlodipine 5 mg"))

        assertEquals(2, store.observeActive().first().first { it.id == id }.dosesPerDay)
        assertEquals(setOf(0), dosesTaken(id, day))
    }

    @Test
    fun theDosesPerDayStayWithinTheAllowedRange() = runTest {
        val id = store.add("r1", "Amlodipine", dosesPerDay = 0)
        assertEquals(1, store.observeActive().first().first { it.id == id }.dosesPerDay)

        val med = store.observeActive().first().first { it.id == id }
        store.update(med.copy(dosesPerDay = 99))
        assertEquals(
            MedicationStore.MAX_DOSES_PER_DAY,
            store.observeActive().first().first { it.id == id }.dosesPerDay,
        )
    }
}
