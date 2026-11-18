package com.caregiver.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests for the person+day scoped local draft store (#906). */
class NoteDraftStoreTest {

    private fun fullDraft(savedAt: Long = 1_700_000_000_000L) = NoteDraftStore.Draft(
        mood = "happy",
        appetite = "fair",
        sleep = "poor",
        mobility = "walks",
        medication = "missed",
        pain = 3,
        fall = false,
        text = "Arabic text عربي",
        savedAt = savedAt,
    )

    @Test
    fun saveAndLoadRoundTripKeepsEveryField() {
        val store = NoteDraftStore.inMemory()
        val key = NoteDraftStore.key("r1", "2026-11-17")

        store.save(key, fullDraft())

        val loaded = store.load(key)!!
        assertEquals("happy", loaded.mood)
        assertEquals("fair", loaded.appetite)
        assertEquals("poor", loaded.sleep)
        assertEquals("walks", loaded.mobility)
        assertEquals("missed", loaded.medication)
        assertEquals(3, loaded.pain)
        assertEquals(false, loaded.fall)
        assertEquals("Arabic text عربي", loaded.text)
        assertEquals(1_700_000_000_000L, loaded.savedAt)
    }

    @Test
    fun keysAreScopedToBothPersonAndDay() {
        val store = NoteDraftStore.inMemory()
        store.save(NoteDraftStore.key("r1", "2026-11-17"), fullDraft())

        assertNull(store.load(NoteDraftStore.key("r2", "2026-11-17")))
        assertNull(store.load(NoteDraftStore.key("r1", "2026-11-18")))
    }

    @Test
    fun clearingRemovesTheDraftWithoutRemovingOthers() {
        val store = NoteDraftStore.inMemory()
        val today = NoteDraftStore.key("r1", "2026-11-17")
        val yesterday = NoteDraftStore.key("r1", "2026-11-16")
        store.save(today, fullDraft())
        store.save(yesterday, fullDraft())

        store.clear(today)

        assertNull(store.load(today))
        assertNotNullYesterday(store, yesterday)
    }

    private fun assertNotNullYesterday(store: NoteDraftStore, key: String) {
        assertEquals("happy", store.load(key)?.mood)
    }

    @Test
    fun corruptPayloadIsTreatedAsNoDraft() {
        val broken = object : NoteDraftStore.Storage {
            override fun read(key: String): String = "{not json"
            override fun write(key: String, value: String?) = Unit
        }

        assertNull(NoteDraftStore(broken).load("any-key"))
    }
}
