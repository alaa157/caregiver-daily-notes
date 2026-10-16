package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Task 8a: note cache DAO against in-memory Room (insert, filtered query, scoped replace on refresh). */
@RunWith(RobolectricTestRunner::class)
class NoteDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.noteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(serverId: String, recipientId: String, date: String, text: String = "t-$serverId") =
        NoteEntity(
            serverId = serverId,
            recipientId = recipientId,
            date = date,
            mood = "good",
            appetite = "good",
            sleep = "good",
            mobility = "walks",
            medicationTaken = "taken",
            pain = 1,
            fall = false,
            text = text,
        )

    @Test
    fun insertAndQueryHonorsTheApiFilters() = runTest {
        dao.insertAll(
            listOf(
                row("n1", "r1", "2026-10-05"),
                row("n2", "r2", "2026-10-06"),
                row("n3", "r1", "2026-10-07"),
            ),
        )

        // Null = wildcard, like the history endpoint; newest first.
        assertEquals(listOf("n3", "n1"), dao.query("r1", null, null).map { it.serverId })
        assertEquals(listOf("n2"), dao.query(null, "2026-10-06", "2026-10-06").map { it.serverId })
        assertEquals(listOf("n2", "n1"), dao.query(null, "2026-10-05", "2026-10-06").map { it.serverId })
        assertEquals(3, dao.observeAll().first().size)
    }

    @Test
    fun replaceScopeSwapsOnlyThatQueryScope() = runTest {
        dao.insertAll(
            listOf(
                row("n1", "r1", "2026-10-05", text = "old"),
                row("n2", "r2", "2026-10-05"),
            ),
        )

        // A refresh of r1's day keeps only what that response returned.
        dao.replaceScope(
            "r1",
            "2026-10-05",
            "2026-10-05",
            listOf(row("n1", "r1", "2026-10-05", text = "fresh"), row("n9", "r1", "2026-10-05")),
        )

        val r1 = dao.query("r1", "2026-10-05", "2026-10-05")
        assertEquals(listOf("n1", "n9"), r1.map { it.serverId })
        assertEquals("fresh", r1.first { it.serverId == "n1" }.text)
        // Another recipient's cached window is untouched.
        assertEquals(listOf("n2"), dao.query("r2", null, null).map { it.serverId })
    }

    @Test
    fun unfilteredRefreshReplacesTheWholeCache() = runTest {
        dao.insertAll(listOf(row("n1", "r1", "2026-10-05"), row("n2", "r1", "2026-10-06")))

        dao.replaceScope(null, null, null, listOf(row("n2", "r1", "2026-10-06")))

        assertEquals(listOf("n2"), dao.query(null, null, null).map { it.serverId })
    }
}
