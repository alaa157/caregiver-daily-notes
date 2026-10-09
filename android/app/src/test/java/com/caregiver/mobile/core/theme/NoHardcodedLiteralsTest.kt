package com.caregiver.mobile.core.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 8: no hardcoded visual literals outside the theme files. Colors,
 * sizes, and type come from design/tokens.json via core/theme only.
 * Exempt: everything under core/theme (the token mappings) and
 * res/values/themes.xml (the pre-Compose splash must carry the token
 * background hex, since a windowBackground cannot reference Compose).
 */
class NoHardcodedLiteralsTest {

    private fun srcMain(): File {
        val candidates = listOf(
            File("src/main"),
            File("app/src/main"),
            File("android/app/src/main"),
            File(System.getProperty("user.dir") + "/src/main"),
            File(System.getProperty("user.dir") + "/app/src/main"),
            File(System.getProperty("user.dir") + "/android/app/src/main"),
        )
        return candidates.firstOrNull { File(it, "AndroidManifest.xml").exists() }
            ?: error("src/main not found from user.dir=" + System.getProperty("user.dir"))
    }

    private fun exempt(relative: String): Boolean =
        relative.contains("core/theme/") || relative == "res/values/themes.xml"

    @Test
    fun noHardcodedColorHexOutsideTheme() {
        val offenders = mutableListOf<String>()
        // Color(0x..), 0xFFxxxxxx, and #xxxxxx hex literals. Text sources
        // only: binary assets (fonts) are not scanned.
        val hex = Regex("Color\\(0x|0xFF[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{6}")
        srcMain().walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .forEach { file ->
            val relative = file.relativeTo(srcMain()).path.replace(File.separator, "/")
            if (exempt(relative)) return@forEach
            file.readLines().forEachIndexed { index, line ->
                if (hex.containsMatchIn(line)) {
                    offenders.add("$relative:${index + 1}: $line")
                }
            }
        }
        assertTrue(
            "hardcoded color hex outside theme files:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun noHardcodedDpOrSpOutsideTheme() {
        val offenders = mutableListOf<String>()
        // Digits directly followed by .dp/.sp (excludes Arrangement.spacedBy
        // and friends, which carry no digits before the dot).
        val dimen = Regex("(?<![\\w.])\\d+\\.(dp|sp)\\b")
        srcMain().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relative = file.relativeTo(srcMain()).path.replace(File.separator, "/")
                if (exempt(relative)) return@forEach
                file.readLines().forEachIndexed { index, line ->
                    if (dimen.containsMatchIn(line)) {
                        offenders.add("$relative:${index + 1}: ${line.trim()}")
                    }
                }
            }
        assertTrue(
            "hardcoded .dp/.sp outside theme files:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
