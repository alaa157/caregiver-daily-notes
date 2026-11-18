package com.caregiver.mobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parsesReleaseTagsAndDropsDemoSuffixes() {
        assertEquals("0.3.4", AppVersion.parseTag("android-0.3.4-demo")?.toString())
        assertEquals("0.3.4", AppVersion.parseTag("v0.3.4 (demo)")?.toString())
        assertEquals("2", AppVersion.parseTag("v2")?.toString())
    }

    @Test
    fun rejectsTagsThatDoNotLookLikeAppVersions() {
        assertNull(AppVersion.parseTag("nightly"))
        assertNull(AppVersion.parseTag("android-demo"))
        assertNull(AppVersion.parseTag(""))
    }

    @Test
    fun comparesNumericComponentsInsteadOfText() {
        assertTrue(
            AppVersion.parseTag("android-0.3.10-demo")!! >
                AppVersion.parseTag("android-0.3.9-demo")!!,
        )
        assertTrue(
            AppVersion.parseTag("v0.4")!! > AppVersion.parseTag("v0.3.99")!!,
        )
    }

    @Test
    fun comparesTrailingZeroComponentsAsEqual() {
        assertEquals(
            0,
            AppVersion.parseTag("v1.0")!!.compareTo(AppVersion.parseTag("v1.0.0")!!),
        )
    }
}
