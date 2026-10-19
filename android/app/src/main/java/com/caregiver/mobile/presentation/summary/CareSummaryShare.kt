package com.caregiver.mobile.presentation.summary

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

/**
 * Native share plumbing for the generated care summary
 * (spec: "Share Generated Care Summary with Doctor").
 *
 * Kept tiny and framework-light on purpose: the text builder and the chooser
 * intent are pure, so they are unit-testable without Robolectric and without
 * ever starting a real messaging app. The launcher takes the system share
 * sheet path only — no target app is hardcoded, nothing is sent
 * automatically, no permissions are requested, and the summary text is never
 * logged.
 */
internal object CareSummaryShare {

    /**
     * Builds the plain-text message from the ACTUAL generated summary shown
     * on the results screen — never placeholder content. Returns null when
     * there is nothing usable to share (blank summary text), so callers hide
     * the action entirely instead of sharing an empty shell.
     *
     * Note: `SummaryDto` carries no trusted generation timestamp, so the
     * date in the message is labelled for what it really is — the sharing /
     * preparation date (today), pre-formatted with the app's bilingual
     * `DateFormats` — and never claims to be the summary's own date.
     */
    fun shareText(summaryText: String, dateText: String, appName: String, arabic: Boolean): String? {
        if (summaryText.isBlank()) return null
        return if (arabic) {
            "ملخص الرعاية\nتاريخ المشاركة: $dateText\n\n$summaryText\n\nتمت المشاركة من تطبيق $appName"
        } else {
            "Care Summary\nShared on: $dateText\n\n$summaryText\n\nShared from $appName"
        }
    }

    /**
     * The chooser intent: `ACTION_SEND` + `text/plain` + `EXTRA_TEXT`,
     * wrapped in `Intent.createChooser` so the system share sheet lists every
     * compatible installed app (WhatsApp, Telegram, Gmail, Messages, …).
     */
    fun chooserIntent(text: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        },
        null,
    )

    /**
     * Opens the share sheet once per tap. Uses the Activity context when one
     * is reachable through ContextWrapper chains (the normal Compose case),
     * falling back to a `NEW_TASK` start otherwise. Any launch failure (no
     * handler, security restriction) degrades to false instead of crashing —
     * and stays silent so the summary text can never leak into logs.
     */
    fun launch(context: Context, text: String): Boolean = try {
        val activity = context.findActivity()
        val intent = chooserIntent(text)
        if (activity == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            activity.startActivity(intent)
        }
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
