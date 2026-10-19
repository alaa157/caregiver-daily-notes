package com.caregiver.mobile.presentation.summary

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Chooser wiring under Robolectric. Robolectric shadows record started
 * intents in memory — no real messaging app is launched and nothing is sent.
 */
@RunWith(RobolectricTestRunner::class)
class CareSummaryShareIntentTest {

    @Test
    fun chooserCarriesSendActionPlainTextAndExtraText() {
        val message = "Care Summary\nDate: Oct 19, 2026\n\nReal generated summary.\n\nShared from Caregiver Notes"

        val chooser = CareSummaryShare.chooserIntent(message)

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("text/plain", send?.type)
        assertEquals(message, send?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun launchFromActivityContextRecordedByEmulatedSystem() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val message = "Care Summary\nDate: 19 أكتوبر 2026\n\nملخص حقيقي.\n\nتمت المشاركة من تطبيق ملاحظات مقدم الرعاية"

        val started = CareSummaryShare.launch(activity, message)

        assertTrue(started)
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("text/plain", send?.type)
        assertEquals(message, send?.getStringExtra(Intent.EXTRA_TEXT))
    }
}
