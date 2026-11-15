package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.RecipientDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The recipient mappers carry relationship and care details in both
 * directions, and the local create normalizes and length-checks the
 * relationship before writing.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientFieldsTest {
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

    @Test
    fun createNormalizesRelationship() = runTest {
        val created = apis.recipients().create(
            CreateRecipientRequest(name = "Aisha", relationship = " Mother "),
        )

        assertEquals("Mother", created.relationship)
        assertEquals("Mother", db.recipientDao().byServerId(created.id)?.relationship)
    }

    @Test
    fun createBlankRelationshipStoresNull() = runTest {
        val created = apis.recipients().create(
            CreateRecipientRequest(name = "Aisha", relationship = "   "),
        )

        assertNull(created.relationship)
        assertNull(db.recipientDao().byServerId(created.id)?.relationship)
    }

    @Test
    fun createTooLongRelationshipThrowsAndWritesNothing() = runTest {
        try {
            apis.recipients().create(
                CreateRecipientRequest(name = "Aisha", relationship = "r".repeat(81)),
            )
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("relationship", e.message)
        }
        assertTrue(db.recipientDao().all().isEmpty())
    }

    @Test
    fun mappersRoundTripAllSixFields() {
        val dto = RecipientDto(
            id = "r1",
            name = "Aisha",
            active = true,
            archivedAt = 1_700_000_000_000L,
            relationship = "MOTHER",
            dateOfBirth = "1950-04-12",
            allergies = "penicillin",
            conditions = "hypertension",
            emergencyContactName = "Omar",
            emergencyContactPhone = "+966500000000",
        )

        val roundTripped = dto.toEntity().toDto()

        assertEquals(dto, roundTripped)
    }
}
