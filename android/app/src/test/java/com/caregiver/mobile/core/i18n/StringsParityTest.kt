package com.caregiver.mobile.core.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 8: every `ar` key exists in `en` and vice versa, no empty values,
 * placeholders stay consistent, and the board-15 state catalog exists.
 */
class StringsParityTest {

    private fun resDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("android/app/src/main/res"),
            File(System.getProperty("user.dir") + "/src/main/res"),
            File(System.getProperty("user.dir") + "/app/src/main/res"),
            File(System.getProperty("user.dir") + "/android/app/src/main/res"),
        )
        return candidates.firstOrNull { File(it, "values/strings.xml").exists() }
            ?: error(
                "strings res dir not found from user.dir=" + System.getProperty("user.dir") +
                    " tried=" + candidates.map { it.path },
            )
    }

    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val out = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i)
            val name = el.attributes.getNamedItem("name").nodeValue
            out[name] = el.textContent ?: ""
        }
        return out
    }

    private fun placeholders(value: String): List<String> {
        // %1$s, %1$d, %s, %d — order matters.
        return Regex("%(\\d+\\$)?[sdf]").findAll(value).map { it.value }.toList()
    }

    @Test
    fun arabicAndEnglishKeysMatch() {
        val dir = resDir()
        val en = strings(File(dir, "values/strings.xml"))
        val ar = strings(File(dir, "values-ar/strings.xml"))

        assertTrue("en strings must not be empty", en.isNotEmpty())
        assertEquals(
            "ar/en key sets must match.\nen-only=${en.keys - ar.keys}\nar-only=${ar.keys - en.keys}",
            en.keys.sorted(),
            ar.keys.sorted(),
        )
    }

    @Test
    fun noEmptyValues() {
        val dir = resDir()
        val en = strings(File(dir, "values/strings.xml"))
        val ar = strings(File(dir, "values-ar/strings.xml"))

        (en + ar).forEach { (key, value) ->
            assertTrue("string '$key' must not be blank", value.isNotBlank())
        }
    }

    @Test
    fun placeholdersStayConsistent() {
        val dir = resDir()
        val en = strings(File(dir, "values/strings.xml"))
        val ar = strings(File(dir, "values-ar/strings.xml"))

        en.forEach { (key, enValue) ->
            val arValue = ar[key] ?: return@forEach
            assertEquals(
                "placeholders for '$key' must match (en='$enValue' ar='$arValue')",
                placeholders(enValue),
                placeholders(arValue),
            )
        }
    }

    @Test
    fun board15StateCatalogExists() {
        val dir = resDir()
        val en = strings(File(dir, "values/strings.xml"))

        // Board-15 catalog: offline, empty states, plans empty, safety banner CD.
        val required = listOf(
            "common_offline",
            "people_empty",
            "notes_empty",
            "plans_empty",
            "plans_view_proposals",
            "safety_banner_cd",
        )
        val missing = required.filter { it !in en }
        assertTrue("missing board-15 state strings: $missing", missing.isEmpty())
    }
}
