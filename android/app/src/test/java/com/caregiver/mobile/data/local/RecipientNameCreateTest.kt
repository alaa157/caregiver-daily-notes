package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.CreateRecipientRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipientNameCreateTest {
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
    fun createTrimsAndCollapsesTheNameBeforeStoringAndReturningIt() = runTest {
        val created = apis.recipients().create(CreateRecipientRequest(" Mama Aisha "))

        assertEquals("Mama Aisha", created.name)
        assertEquals("Mama Aisha", db.recipientDao().byServerId(created.id)?.name)
    }

    @Test
    fun blankNameIsRejectedWithoutWriting() = runTest {
        assertInvalidNameWithoutWriting(" \t\n ")
    }

    @Test
    fun nameWith121CharactersIsRejectedWithoutWriting() = runTest {
        assertInvalidNameWithoutWriting("a".repeat(121))
    }

    @Test
    fun nameWith120CharactersIsAccepted() = runTest {
        val name = "a".repeat(120)

        val created = apis.recipients().create(CreateRecipientRequest(name))

        assertEquals(name, created.name)
        assertEquals(name, db.recipientDao().byServerId(created.id)?.name)
    }

    @Test
    fun duplicateNormalizedNamesAreNotRejected() = runTest {
        val first = apis.recipients().create(CreateRecipientRequest("Mama Aisha"))
        val second = apis.recipients().create(CreateRecipientRequest(" Mama  Aisha "))

        assertEquals("Mama Aisha", first.name)
        assertEquals("Mama Aisha", second.name)
        assertEquals(2, db.recipientDao().all().size)
    }

    private suspend fun assertInvalidNameWithoutWriting(name: String) {
        try {
            apis.recipients().create(CreateRecipientRequest(name))
            throw AssertionError("expected an invalid recipient name to be rejected")
        } catch (e: IllegalArgumentException) {
            assertEquals("recipient name", e.message)
        }

        assertTrue(db.recipientDao().all().isEmpty())
    }
}
