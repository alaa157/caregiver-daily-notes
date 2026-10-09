package com.caregiver.mobile.core.navigation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation contract per design/screens.md + components.md BottomNav:
 * five tabs (home/people/history/saved/settings) in order, labels from the
 * nav.* copy keys, plus the full destination set. Tab titles must resolve
 * to real, distinct translations in both locales.
 */
class AppRoutesTest {

    @Test
    fun tabsAreHomePeopleHistorySavedSettingsInOrder() {
        assertEquals(
            listOf("home", "people", "history", "saved", "settings"),
            MainTab.entries.map { it.route },
        )
    }

    @Test
    fun tabTitlesHaveDistinctArabicAndEnglishStrings() {
        val en = readStrings("values")
        val ar = readStrings("values-ar")
        (listOf("nav_home", "nav_people", "nav_history", "nav_saved", "nav_settings") +
            listOf("home_addNote")).forEach { key ->
            val english = en[key]
            val arabic = ar[key]
            assertTrue("missing English string: $key", !english.isNullOrBlank())
            assertTrue("missing Arabic string: $key", !arabic.isNullOrBlank())
            assertNotEquals("Arabic must not copy English for $key", english, arabic)
        }
    }

    @Test
    fun allSpecDestinationsRegistered() {
        val routes = AppDestinations.all.map { it.base }
        assertTrue(
            routes.containsAll(
                listOf(
                    // Auth + server URL.
                    "login", "register", "server-url",
                    // Tabs (spec BottomNav).
                    "home", "people", "history", "saved",
                    // Recipient + note flow.
                    "recipient/{recipientId}",
                    "add-recipient",
                    "note-editor/{recipientId}",
                    "note-saved/{noteId}",
                    "note/{noteId}",
                    "addendum/{noteId}",
                    // Summary.
                    "summary/{recipientId}",
                    "summary-result/{recipientId}/{periodDays}",
                    // Plans.
                    "plans",
                    "plan-proposal/{planId}",
                    "plan-edit/{planId}",
                    "plan-versions/{planId}",
                    // Settings tab.
                    "settings",
                ),
            ),
        )
    }

    @Test
    fun routeBuildersProduceConcreteRoutes() {
        assertEquals("home", AppRoutes.tab(MainTab.Home))
        assertEquals("saved", AppRoutes.tab(MainTab.Saved))
        assertEquals("settings", AppRoutes.tab(MainTab.Settings))
        assertEquals("recipient/abc", AppRoutes.recipientDetail("abc"))
        assertEquals("note-editor/abc", AppRoutes.noteEditor("abc"))
        assertEquals("note/n1", AppRoutes.noteDetail("n1"))
        assertEquals("summary/r1", AppRoutes.summary("r1"))
        assertEquals("summary-result/r1/30", AppRoutes.summaryResult("r1", 30))
        assertEquals("plan-edit/p1", AppRoutes.planEdit("p1"))
    }

    @Test
    fun buildersMatchRegisteredTemplates() {
        val templates = AppDestinations.all.map { it.base }
        assertTrue(
            templates.containsAll(
                listOf(
                    AppRoutes.recipientDetail("{recipientId}"),
                    AppRoutes.noteEditor("{recipientId}"),
                    AppRoutes.noteDetail("{noteId}"),
                    AppRoutes.summary("{recipientId}"),
                    AppRoutes.summaryResult("{recipientId}", 7)
                        .replace("/7", "/{periodDays}"),
                    AppRoutes.planEdit("{planId}"),
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
