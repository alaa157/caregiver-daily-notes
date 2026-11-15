package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.recipient.CareDetails
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `RecipientAdmin.updateProfile` / `updateCareDetails`: same archived and
 * missing guards as every other write, shared name/relationship/care
 * validation, normalized storage, and `null` clears a care field.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientProfileUpdateTest {
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
    fun updateProfileStoresNormalizedNameAndRelationship() = runTest {
        seedRecipient("r1", name = "Old", relationship = null)

        val updated = apis.updateProfile("r1", "  Mama\tAisha  ", " Mother ")

        assertEquals("Mama Aisha", updated.name)
        assertEquals("Mother", updated.relationship)
        val row = db.recipientDao().byServerId("r1")
        assertEquals("Mama Aisha", row?.name)
        assertEquals("Mother", row?.relationship)
    }

    @Test
    fun updateCareDetailsStoresNormalizedValues() = runTest {
        seedRecipient("r1", name = "Aisha")

        val updated = apis.updateCareDetails(
            "r1",
            CareDetails(
                dateOfBirth = " 1950-04-12 ",
                allergies = " penicillin ",
                conditions = "hypertension",
                emergencyContactName = " Omar ",
                emergencyContactPhone = "+966 50 000 0000",
            ),
        )

        assertEquals("1950-04-12", updated.dateOfBirth)
        assertEquals("penicillin", updated.allergies)
        assertEquals("hypertension", updated.conditions)
        assertEquals("Omar", updated.emergencyContactName)
        assertEquals("+966 50 000 0000", updated.emergencyContactPhone)
    }

    @Test
    fun archivedRecipientIsRefusedForBothUpdates() = runTest {
        seedRecipient("r1", name = "Aisha", archivedAt = 111L)

        for (block in listOf<suspend () -> Unit>(
            { apis.updateProfile("r1", "New", null) },
            { apis.updateCareDetails("r1", CareDetails(allergies = "x")) },
        )) {
            try {
                block()
                error("expected ArchivedRecipientException")
            } catch (e: ArchivedRecipientException) {
                assertEquals("r1", e.recipientId)
            }
        }
        assertEquals("Aisha", db.recipientDao().byServerId("r1")?.name)
    }

    @Test
    fun missingRecipientThrowsForBothUpdates() = runTest {
        for (block in listOf<suspend () -> Unit>(
            { apis.updateProfile("missing", "New", null) },
            { apis.updateCareDetails("missing", CareDetails(allergies = "x")) },
        )) {
            try {
                block()
                error("expected NoSuchElementException")
            } catch (e: NoSuchElementException) {
                // expected
            }
        }
    }

    @Test
    fun invalidDateOfBirthThrows() = runTest {
        seedRecipient("r1", name = "Aisha")

        try {
            apis.updateCareDetails("r1", CareDetails(dateOfBirth = "not-a-date"))
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        assertNull(db.recipientDao().byServerId("r1")?.dateOfBirth)
    }

    @Test
    fun blankCareDetailClearsTheField() = runTest {
        seedRecipient(
            "r1",
            name = "Aisha",
            allergies = "penicillin",
            conditions = "hypertension",
        )

        apis.updateCareDetails("r1", CareDetails(allergies = "  ", conditions = "asthma"))

        val row = db.recipientDao().byServerId("r1")
        assertNull(row?.allergies)
        assertEquals("asthma", row?.conditions)
    }

    @Test
    fun eachUpdateLeavesTheOtherFieldsAlone() = runTest {
        seedRecipient(
            "r1",
            name = "Aisha",
            relationship = "MOTHER",
            allergies = "penicillin",
        )

        apis.updateProfile("r1", "Aisha B", null)
        var row = db.recipientDao().byServerId("r1")
        assertEquals("Aisha B", row?.name)
        assertNull(row?.relationship)
        assertEquals("penicillin", row?.allergies)

        // updateCareDetails replaces all five care fields, so allergies clears.
        apis.updateCareDetails("r1", CareDetails(conditions = "asthma"))
        row = db.recipientDao().byServerId("r1")
        assertEquals("Aisha B", row?.name)
        assertNull(row?.relationship)
        assertNull(row?.allergies)
        assertEquals("asthma", row?.conditions)
    }

    private suspend fun seedRecipient(
        id: String,
        name: String,
        archivedAt: Long? = null,
        relationship: String? = null,
        allergies: String? = null,
        conditions: String? = null,
    ) {
        db.recipientDao().insertAll(
            listOf(
                RecipientEntity(
                    serverId = id,
                    name = name,
                    active = true,
                    archivedAt = archivedAt,
                    relationship = relationship,
                    allergies = allergies,
                    conditions = conditions,
                ),
            ),
        )
    }
}
