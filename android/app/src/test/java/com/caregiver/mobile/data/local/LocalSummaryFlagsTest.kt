package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.SummaryRequest
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The local-mode fallback runs when no AI key is configured. It cannot
 * generate prose, but it must still return the deterministic safety flags so
 * a reported fall is never hidden behind an empty summary payload.
 */
@RunWith(RobolectricTestRunner::class)
class LocalSummaryFlagsTest {
    private lateinit var db: AppDatabase
    private lateinit var apis: LocalBackendApis

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        apis = LocalBackendApis(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun note(
        id: String,
        recipientId: String,
        date: String,
        fall: Boolean = false,
        pain: Int = 1,
    ) = NoteEntity(
        serverId = id,
        recipientId = recipientId,
        date = date,
        mood = "good",
        appetite = "good",
        sleep = "good",
        mobility = "walks",
        medicationTaken = "taken",
        pain = pain,
        fall = fall,
        text = "note $id",
    )

    @Test
    fun fallInWindowIsReportedAndTheFallbackExplainsItself() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(
                note("n1", "r1", today.toString(), fall = true, pain = 8),
                note("n2", "r2", today.toString(), fall = true),
                note("n3", "r1", today.minusDays(10).toString(), fall = true),
            ),
        )

        val summary = apis.summaries().summarize(SummaryRequest("r1", 7))

        assertEquals(listOf("FALL_REPORTED", "HIGH_PAIN"), summary.redFlags)
        assertTrue(summary.uncertainties.any { it.topic == "ai_not_configured" })
    }

    @Test
    fun flagsNeverLeakFromAnotherRecipientOrOutsideTheWindow() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(
                note("n1", "r1", today.toString()),
                note("n2", "r2", today.toString(), fall = true, pain = 8),
                note("n3", "r1", today.minusDays(10).toString(), fall = true, pain = 9),
            ),
        )

        val summary = apis.summaries().summarize(SummaryRequest("r1", 7))

        assertTrue(summary.redFlags.isEmpty())
        assertTrue(summary.uncertainties.any { it.topic == "ai_not_configured" })
    }
}
