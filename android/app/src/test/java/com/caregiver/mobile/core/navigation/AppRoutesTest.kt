package com.caregiver.mobile.core.navigation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation contract for Task 1: 4 tabs (home/people/notes/history) plus the
 * full destination set covering all 15 design boards and the missing pages.
 * Tab titles must resolve to real, distinct translations in both locales.
 */
class AppRoutesTest {

    @Test
    fun tabsAreHomePeopleNotesHistoryInOrder() {
        assertEquals(
            listOf("home", "people", "notes", "history"),
            MainTab.entries.map { it.route },
        )
    }

    @Test
    fun tabTitlesHaveDistinctArabicAndEnglishStrings() {
        val en = readStrings("values")
        val ar = readStrings("values-ar")
        listOf("tab_home", "tab_people", "tab_notes", "tab_history").forEach { key ->
            val english = en[key]
            val arabic = ar[key]
            assertTrue("missing English string: $key", !english.isNullOrBlank())
            assertTrue("missing Arabic string: $key", !arabic.isNullOrBlank())
            assertNotEquals("Arabic must not copy English for $key", english, arabic)
        }
    }

    @Test
    fun allBoardAndMissingPageDestinationsRegistered() {
        val routes = AppDestinations.all.map { it.base }
        assertTrue(
            routes.containsAll(
                listOf(
                    // Auth (board 1 + missing register + server URL).
                    "login", "register", "server-url",
                    // Tabs (boards 2, 3, 9 + notes tab).
                    "home", "people", "notes", "history",
                    // Recipient + note flow (boards 4-8).
                    "recipient/{recipientId}",
                    "note-editor/{recipientId}",
                    "note-saved/{noteId}",
                    "note/{noteId}",
                    "addendum/{noteId}",
                    // Summary (boards 10-11).
                    "summary/{recipientId}",
                    "summary-result",
                    // Plans (boards 12-14 + missing plans list).
                    "plans",
                    "plan-proposal/{planId}",
                    "plan-edit/{planId}",
                    "plan-versions/{planId}",
                    // Missing settings page.
                    "settings",
                ),
            ),
        )
    }

    private fun readStrings(valuesDir: String): Map<String, String> {
        val file = File("src/main/res/$valuesDir/strings.xml")
        assertTrue("missing ${file.path}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return doc.getElementsByTagName("string").let { nodes ->
            (0 until nodes.length).associate { i ->
                val el = nodes.item(i)
                el.attributes.getNamedItem("name").nodeValue to el.textContent.trim()
            }
        }
    }
}
