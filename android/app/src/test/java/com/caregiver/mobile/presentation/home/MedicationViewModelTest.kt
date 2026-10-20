package com.caregiver.mobile.presentation.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.MedicationStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A clock the test can move: the view model reads the date and computes
 * the midnight delay through it, so the test walks the card across midnight
 * without sleeping a real second.
 */
private class MutableClock(
    private var now: Instant,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now

    fun advanceTo(other: Instant) {
        now = other
    }
}

/**
 * The view-model contract behind the home card: the marks the card shows
 * and the date taps write to share one resolved day, and that day rolls
 * over past midnight without a restart. Also: adding a medication without
 * any person in the household is refused, not orphaned.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MedicationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: AppDatabase
    private lateinit var store: MedicationStore
    private lateinit var clock: MutableClock

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = MedicationStore(db)
        clock = MutableClock(Instant.parse("2026-10-20T10:00:00Z"))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun takenMarksRollOverAtMidnightAndTapsFollowTheNewDay() = runTest(dispatcher) {
        val vm = MedicationViewModel(store, clock)
        // stateIn(WhileSubscribed) only computes while collected; the
        // ticker flow lives inside the same subscription chain, so ending
        // the test (backgroundScope is cancelled) stops it cleanly.
        backgroundScope.launch(dispatcher) { vm.state.collect { } }
        advanceTimeBy(1_000)

        vm.add("r1", "Amlodipine", null, null)
        advanceTimeBy(1_000)
        val id = vm.state.value.meds.first().id

        vm.setTaken(id, true)
        advanceTimeBy(1_000)
        assertTrue(id in vm.state.value.takenToday)

        // The app stays open across midnight: the card flips to the new
        // day by itself. From the fixed start (2026-10-20T10:00Z), the
        // pending midnight wake sits ~50.401m of virtual time out; the
        // window below passes it and deliberately stops short of the
        // next midnight (86.4m later), so the bounded advance cannot loop.
        clock.advanceTo(Instant.parse("2026-10-21T01:00:00Z"))
        advanceTimeBy(50_500_000)
        assertFalse(id in vm.state.value.takenToday)
        assertEquals(
            emptyList<String>(),
            store.observeIntakes("2026-10-21").first()
                .filter { it.times > 0 }.map { it.medicationId },
        )

        // Taps after midnight write to the day the card is showing now.
        vm.setTaken(id, true)
        advanceTimeBy(1_000)
        assertTrue(id in vm.state.value.takenToday)
        val newRow = store.observeIntakes("2026-10-21").first().first()
        assertEquals(id, newRow.medicationId)
        assertEquals("2026-10-21", newRow.date)
    }

    @Test
    fun addingWithoutAPersonIsRefusedInsteadOfOrphaning() = runTest(dispatcher) {
        val vm = MedicationViewModel(store, clock)
        backgroundScope.launch(dispatcher) { vm.state.collect { } }
        advanceTimeBy(1_000)

        // No people in the household: the card cannot attribute the
        // medication to anyone, so the add must not write a row.
        vm.add("   ", "Ghost pill", null, null)
        advanceTimeBy(1_000)
        assertTrue(vm.state.value.meds.isEmpty())

        // A real recipient still lands.
        vm.add("r1", "Amlodipine", null, null)
        advanceTimeBy(1_000)
        assertEquals(listOf("Amlodipine"), vm.state.value.meds.map { it.name })
    }
}
