package com.caregiver.mobile.core.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copy contract per design: every `ar` key exists in `en` and vice versa,
 * no empty values, placeholders stay consistent, and the generated
 * resources match design/copy.*.json keys in identical order (dots become
 * underscores). scripts/gen_strings.py is the generator; this test guards
 * the contract in CI.
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
    fun generatedStringsMatchCopyJsonKeysInOrder() {
        val dir = resDir()
        // Walk up from the Gradle user.dir (android/app for unit tests) to
        // the repo root holding design/copy.*.json.
        var root: File? = File(System.getProperty("user.dir")).absoluteFile
        var copyEn: File? = null
        var copyAr: File? = null
        while (root != null && copyEn == null) {
            val candidate = File(root, "design/copy.en.json")
            if (candidate.isFile) {
                copyEn = candidate
                copyAr = File(root, "design/copy.ar.json")
            }
            root = root.parentFile
        }
        assertTrue("design/copy.en.json not found from $dir", copyEn?.isFile == true)
        assertTrue("design/copy.ar.json not found from $dir", copyAr?.isFile == true)

        val enKeys = keysInOrder(copyEn!!)
        val arKeys = keysInOrder(copyAr!!)
        assertEquals(
            "copy.en.json and copy.ar.json must have identical keys in identical order",
            enKeys,
            arKeys,
        )

        val expected = enKeys.map { it.replace(".", "_") }
        val resEn = strings(File(dir, "values/strings.xml")).keys.toList()
        val resAr = strings(File(dir, "values-ar/strings.xml")).keys.toList()
        assertEquals(
            "values/strings.xml must match copy.en.json (run scripts/gen_strings.py)",
            expected,
            resEn,
        )
        assertEquals(
            "values-ar/strings.xml must match copy.ar.json (run scripts/gen_strings.py)",
            expected,
            resAr,
        )
    }

    private fun keysInOrder(file: File): List<String> {
        // Order-sensitive key extraction: a regex over the raw JSON preserves
        // document order without a JSON parser dependency.
        val keys = Regex("\"([^\"]+)\"\\s*:").findAll(file.readText())
            .map { it.groupValues[1] }.toList()
        assertTrue("${file.name} must not be empty", keys.isNotEmpty())
        return keys
    }
}
