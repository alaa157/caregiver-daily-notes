package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.NoteDto
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The local backend honours the recorded flags: an unanswered pain or fall
 * is stored as 0/false instead of leaking a phantom answer, and both flags
 * round-trip through detail and history.
 */
@RunWith(RobolectricTestRunner::class)
class NoteRecordedFlagsTest {
    private lateinit var db: AppDatabase
    private lateinit var apis: LocalBackendApis
    private lateinit var recipientId: String

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        apis = LocalBackendApis(db)
        runTest {
            recipientId = apis.recipients().create(CreateRecipientRequest("Aisha")).id
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun createNote(
        pain: Int,
        fall: Boolean,
        forRecipientId: String = recipientId,
        painRecorded: Boolean = true,
        fallRecorded: Boolean = true,
    ): NoteDto = apis.notes().create(
        CreateNoteRequest(
            recipientId = forRecipientId,
            mood = "good",
            appetite = "good",
            sleep = "good",
            mobility = "walks",
            medicationTaken = "taken",
            pain = pain,
            fall = fall,
            text = "flag test",
            painRecorded = painRecorded,
            fallRecorded = fallRecorded,
        ),
    )

    @Test
    fun unansweredPainIsStoredAsZeroWithTheFlagOff() = runTest {
        val created = createNote(pain = 9, fall = false, painRecorded = false)

        val stored = db.noteDao().byServerId(created.id)!!
        assertEquals(0, stored.pain)
        assertFalse(stored.painRecorded)
        assertTrue(stored.fallRecorded)
    }

    @Test
    fun recordedZeroPainKeepsTheFlagOn() = runTest {
        val created = createNote(pain = 0, fall = false, painRecorded = true)

        val stored = db.noteDao().byServerId(created.id)!!
        assertEquals(0, stored.pain)
        assertTrue(stored.painRecorded)
    }

    @Test
    fun unansweredFallIsStoredAsFalseWithTheFlagOff() = runTest {
        val created = createNote(pain = 1, fall = true, fallRecorded = false)

        val stored = db.noteDao().byServerId(created.id)!!
        assertFalse(stored.fall)
        assertFalse(stored.fallRecorded)
        assertTrue(stored.painRecorded)
    }

    @Test
    fun detailReturnsBothFlagsFromTheStoredEntity() = runTest {
        val created = createNote(pain = 9, fall = true, painRecorded = false, fallRecorded = false)

        val detail = apis.notes().detail(created.id)
        assertFalse(detail.painRecorded)
        assertFalse(detail.fallRecorded)
        assertEquals(0, detail.pain)
        assertFalse(detail.fall)
    }

    @Test
    fun historyReturnsBothFlagsOnEveryNote() = runTest {
        createNote(pain = 9, fall = true, painRecorded = false, fallRecorded = false)
        db.noteDao().insertAll(
            listOf(
                NoteDto(
                    id = "recorded-note",
                    recipientId = recipientId,
                    date = LocalDate.now().minusDays(1).toString(),
                    mood = "good",
                    appetite = "good",
                    sleep = "good",
                    mobility = "walks",
                    medicationTaken = "taken",
                    pain = 3,
                    fall = false,
                    text = "previous day",
                ).toEntity(),
            ),
        )

        val notes = apis.notes().history(recipientId, null, null)
        assertEquals(2, notes.size)
        val unanswered = notes.single { !it.painRecorded }
        assertFalse(unanswered.fallRecorded)
        assertEquals(0, unanswered.pain)
        assertFalse(unanswered.fall)
        val answered = notes.single { it.painRecorded }
        assertTrue(answered.fallRecorded)
        assertEquals(3, answered.pain)
    }

    @Test
    fun secondNoteForRecipientOnSameDayReturnsExistingNoteId() = runTest {
        val first = createNote(pain = 1, fall = false)

        val error = try {
            createNote(pain = 2, fall = true)
            null
        } catch (e: DuplicateNoteDayException) {
            e
        }

        assertEquals(recipientId, error?.recipientId)
        assertEquals(first.id, error?.existingNoteId)
    }

    @Test
    fun differentRecipientCanHaveNoteOnSameDay() = runTest {
        val otherRecipientId = apis.recipients().create(CreateRecipientRequest("Mariam")).id
        val first = createNote(pain = 1, fall = false)

        val second = createNote(pain = 2, fall = true, forRecipientId = otherRecipientId)

        assertEquals(otherRecipientId, second.recipientId)
        assertEquals(first.date, second.date)
    }

    @Test
    fun sameRecipientCanHaveNoteOnDifferentDay() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        db.noteDao().insertAll(
            listOf(
                NoteDto(
                    id = "yesterday-note",
                    recipientId = recipientId,
                    date = yesterday,
                    mood = "good",
                    appetite = "good",
                    sleep = "good",
                    mobility = "walks",
                    medicationTaken = "taken",
                    pain = 0,
                    fall = false,
                    text = "previous day",
                ).toEntity(),
            ),
        )

        val created = createNote(pain = 2, fall = true)

        assertEquals(LocalDate.now().toString(), created.date)
        assertEquals(recipientId, created.recipientId)
    }
}
