package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.SummaryRequest
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        painRecorded: Boolean = true,
        fallRecorded: Boolean = true,
        appetite: String = "good",
        text: String = "note $id",
    ) = NoteEntity(
        serverId = id,
        recipientId = recipientId,
        date = date,
        mood = "good",
        appetite = appetite,
        sleep = "good",
        mobility = "walks",
        medicationTaken = "taken",
        pain = pain,
        fall = fall,
        text = text,
        painRecorded = painRecorded,
        fallRecorded = fallRecorded,
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

    @Test
    fun unansweredPainProducesANullSignalPain() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(note("n1", "r1", today.toString(), pain = 0, painRecorded = false)),
        )

        val signal = apis.summaries().signals("r1", null, null).single()

        assertEquals(null, signal.pain)
        assertFalse(signal.painRecorded)
    }

    @Test
    fun recordedZeroPainProducesAZeroSignalPain() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(note("n1", "r1", today.toString(), pain = 0, painRecorded = true)),
        )

        val signal = apis.summaries().signals("r1", null, null).single()

        assertEquals(0, signal.pain)
    }

    @Test
    fun unansweredFallProducesAnUnrecordedFallSignal() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(note("n1", "r1", today.toString(), fall = false, fallRecorded = false)),
        )

        val signal = apis.summaries().signals("r1", null, null).single()

        assertEquals(false, signal.fallReported)
        assertFalse(signal.fallRecorded)
    }

    @Test
    fun blankAppetiteProducesAnUnrecordedAppetiteSignal() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(note("n1", "r1", today.toString(), appetite = "")),
        )

        val signal = apis.summaries().signals("r1", null, null).single()

        assertFalse(signal.appetiteRecorded)
    }

    @Test
    fun aFallMentionedInTextStillRaisesTheFlagWhenTheCheckboxWasUnanswered() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(
                note(
                    "n1", "r1", today.toString(),
                    fall = false, fallRecorded = false,
                    text = "She fell in the bathroom this morning.",
                ),
            ),
        )

        val summary = apis.summaries().summarize(SummaryRequest("r1", 7))

        assertTrue(summary.redFlags.contains("FALL_REPORTED"))
    }

    @Test
    fun threeDaysOfRefusedAppetiteRaisePoorAppetiteDays() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(
                note("n1", "r1", today.toString(), appetite = "refused"),
                note("n2", "r1", today.minusDays(1).toString(), appetite = "refused"),
                note("n3", "r1", today.minusDays(2).toString(), appetite = "refused"),
            ),
        )

        val summary = apis.summaries().summarize(SummaryRequest("r1", 7))

        assertTrue(summary.redFlags.contains("POOR_APPETITE_DAYS"))
    }

    @Test
    fun threeDaysOfReducedAppetiteDoNotRaisePoorAppetiteDays() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(
                note("n1", "r1", today.toString(), appetite = "reduced"),
                note("n2", "r1", today.minusDays(1).toString(), appetite = "reduced"),
                note("n3", "r1", today.minusDays(2).toString(), appetite = "reduced"),
            ),
        )

        val summary = apis.summaries().summarize(SummaryRequest("r1", 7))

        assertFalse(summary.redFlags.contains("POOR_APPETITE_DAYS"))
    }

    @Test
    fun threeDaysOfPoorAppetiteStillRaisePoorAppetiteDays() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(
                note("n1", "r1", today.toString(), appetite = "poor"),
                note("n2", "r1", today.minusDays(1).toString(), appetite = "poor"),
                note("n3", "r1", today.minusDays(2).toString(), appetite = "poor"),
            ),
        )

        val summary = apis.summaries().summarize(SummaryRequest("r1", 7))

        assertTrue(summary.redFlags.contains("POOR_APPETITE_DAYS"))
    }

    @Test
    fun recordedHighPainStillRaisesTheFlag() = runTest {
        val today = LocalDate.now()
        db.noteDao().insertAll(
            listOf(note("n1", "r1", today.toString(), pain = 8, painRecorded = true)),
        )

        val summary = apis.summaries().summarize(SummaryRequest("r1", 7))

        assertTrue(summary.redFlags.contains("HIGH_PAIN"))
    }
}
