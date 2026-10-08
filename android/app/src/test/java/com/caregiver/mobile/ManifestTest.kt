package com.caregiver.mobile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manifest wiring the whole app depends on: the registered Application
 * (without it the MainActivity cast crashes on launch), RTL support, and
 * the narrow cleartext exception.
 */
class ManifestTest {

    @Test
    fun applicationRegisteredWithRtlAndSecurityConfig() {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue("missing ${file.path}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val app = doc.getElementsByTagName("application").item(0)
        val attrs = app.attributes
        assertEquals(
            ".CaregiverApp",
            attrs.getNamedItem("android:name").nodeValue,
        )
        assertEquals("true", attrs.getNamedItem("android:supportsRtl").nodeValue)
        assertEquals(
            "@xml/network_security_config",
            attrs.getNamedItem("android:networkSecurityConfig").nodeValue,
        )
    }
}
