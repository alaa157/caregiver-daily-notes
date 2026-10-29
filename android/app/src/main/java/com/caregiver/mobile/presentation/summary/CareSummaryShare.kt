package com.caregiver.mobile.presentation.summary

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

/**
 * Native share plumbing for the generated care summary
 * (spec: "Share Generated Care Summary with Doctor").
 *
 * Kept tiny and framework-light on purpose: the text builder and the share
 * intents are pure, so they are unit-testable without ever starting a real
 * messaging app. The launcher takes the system share sheet path only — no
 * target app is hardcoded, nothing is sent automatically, no permissions are
 * requested, and the summary text is never logged.
 */
internal object CareSummaryShare {

    /**
     * Builds the optional plain-text message from the ACTUAL generated
     * summary shown on the results screen — never placeholder content.
     * Returns null when there is no generated text, so the PDF is shared on
     * its own instead of attaching an empty message.
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
     * The PDF share intent: `ACTION_SEND` + `application/pdf` +
     * `EXTRA_STREAM`, with an explicit ClipData grant so receiving apps can
     * always read the FileProvider URI. The optional message text is added
     * only when there is a real summary to include.
     */
    fun pdfShareIntent(uri: Uri, text: String? = null): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Opens the system chooser for [sendIntent]. Uses the Activity context
     * when one is reachable through ContextWrapper chains (the normal Compose
     * case), falling back to a `NEW_TASK` start otherwise. Any launch failure
     * (no handler, security restriction) degrades to false instead of
     * crashing — and stays silent so the summary text can never leak into
     * logs.
     */
    fun launchChooser(context: Context, sendIntent: Intent): Boolean = try {
        val chooser = Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val activity = context.findActivity()
        if (activity == null) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } else {
            activity.startActivity(chooser)
        }
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
