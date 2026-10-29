package com.caregiver.mobile.presentation.summary

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Share wiring under Robolectric. Robolectric shadows record started
 * intents in memory — no real messaging app is launched and nothing is sent.
 */
@RunWith(RobolectricTestRunner::class)
class CareSummaryShareIntentTest {

    private val uri = Uri.parse("content://com.caregiver.mobile.fileprovider/reports/care-summary-test.pdf")

    @Test
    fun pdfIntentCarriesStreamGrantAndOptionalText() {
        val message = "Care Summary\nShared on: Oct 19, 2026\n\nReal generated summary."

        val send = CareSummaryShare.pdfShareIntent(uri, message)

        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(message, send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(uri, send.clipData?.getItemAt(0)?.uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun blankSummaryTextIsNotAttachedToPdfShare() {
        val send = CareSummaryShare.pdfShareIntent(uri, "   \n ")

        assertNull(send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun launchChooserFromActivityRecordsPdfSend() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val started = CareSummaryShare.launchChooser(
            activity,
            CareSummaryShare.pdfShareIntent(uri, "Summary"),
        )

        assertTrue(started)
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("application/pdf", send?.type)
        assertEquals(uri, send?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

    @Test
    fun launchChooserFromApplicationContextAddsNewTaskFlag() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        val started = CareSummaryShare.launchChooser(
            application,
            CareSummaryShare.pdfShareIntent(uri),
        )

        assertTrue(started)
        val chooser = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
