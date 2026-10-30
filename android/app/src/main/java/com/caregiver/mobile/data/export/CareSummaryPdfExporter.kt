package com.caregiver.mobile.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.caregiver.mobile.R
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.presentation.notes.OptionGroup
import com.caregiver.mobile.presentation.notes.OptionLabels
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

object CareSummaryPdfExporter {
    fun export(
        context: Context,
        summary: SummaryDto,
        recipientName: String?,
        languageTag: String,
        notes: List<NoteDto> = emptyList(),
    ): android.net.Uri {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "care-summary-${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        val paint = TextPaint().apply {
            textSize = 12f
            color = Color.BLACK
            isAntiAlias = true
        }
        val titlePaint = TextPaint(paint).apply { textSize = 18f; isFakeBoldText = true }
        val sectionPaint = TextPaint(paint).apply { textSize = 14f; isFakeBoldText = true }

        var pageNumber = 1
        // RTL/alignment switch, resolved once from the requested language
        // tag; declared before the nested drawing helpers so they can
        // capture it (local functions cannot reference later declarations).
        val ar = languageTag.startsWith("ar")
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun newPage() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun drawTextBlock(text: String, p: TextPaint = paint, spacing: Float = 6f) {
            if (text.isBlank()) return
            val width = (pageWidth - 2 * margin).toInt()
            val alignment = if (ar) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(true)
                .build()
            val lineCount = layout.lineCount
            var line = 0
            while (line < lineCount) {
                var linesOnPage = 0
                var blockHeight = 0f
                while (line + linesOnPage < lineCount) {
                    val lineTop = layout.getLineTop(line + linesOnPage).toFloat()
                    val lineBottom = layout.getLineBottom(line + linesOnPage).toFloat()
                    val h = lineBottom - (if (linesOnPage == 0) layout.getLineTop(line).toFloat() else layout.getLineBottom(line + linesOnPage - 1).toFloat())
                    if (y + blockHeight + h > pageHeight - margin && linesOnPage > 0) break
                    if (y + blockHeight + h > pageHeight - margin && linesOnPage == 0 && y > margin) {
                        newPage()
                        continue
                    }
                    blockHeight += h
                    linesOnPage++
                }
                if (linesOnPage == 0) {
                    newPage()
                    continue
                }
                canvas.save()
                canvas.translate(margin, y - layout.getLineTop(line))
                canvas.clipRect(0f, layout.getLineTop(line).toFloat(), width.toFloat(), (layout.getLineTop(line) + blockHeight))
                layout.draw(canvas)
                canvas.restore()
                y += blockHeight + spacing
                line += linesOnPage
                if (line < lineCount) newPage()
            }
        }

        drawTextBlock(context.getString(R.string.pdf_title), titlePaint, 4f)
        drawTextBlock(context.getString(R.string.app_name), paint, 8f)
        recipientName?.takeIf { it.isNotBlank() }?.let {
            drawTextBlock(context.getString(R.string.pdf_recipient, it), paint, 4f)
        }
        drawTextBlock(
            context.resources.getQuantityString(
                R.plurals.pdf_period,
                summary.periodDays,
                summary.periodDays,
            ),
            paint,
            4f,
        )
        drawTextBlock(
            context.getString(
                R.string.pdf_generated,
                DateFormats.historyDay(LocalDate.now().toString(), ar),
            ),
            paint,
            12f,
        )

        drawTextBlock(context.getString(R.string.pdf_executive_summary), sectionPaint, 4f)
        drawTextBlock(
            if (summary.text.isBlank()) context.getString(R.string.summary_no_supported_observations) else summary.text,
            paint,
            12f,
        )

        drawTextBlock(context.getString(R.string.pdf_care_flags), sectionPaint, 4f)
        if (summary.redFlags.isEmpty()) {
            drawTextBlock(context.getString(R.string.pdf_no_flags), paint, 12f)
        } else {
            summary.redFlags.forEach { flag ->
                drawTextBlock("• ${flagLabel(context, flag)}", paint, 4f)
            }
            y += 8f
        }

        if (summary.evidence.isNotEmpty()) {
            drawTextBlock(context.getString(R.string.pdf_evidence), sectionPaint, 4f)
            summary.evidence.forEach { ev ->
                drawTextBlock("• ${ev.quote}", paint, 8f)
            }
        }

        drawTextBlock(context.getString(R.string.pdf_recorded_observations), sectionPaint, 4f)
        val listSeparator = if (ar) "، " else ", "
        val trendLines = mutableListOf<String>()
        notes.sortedBy { it.date }.forEach { n ->
            val parts = mutableListOf<String>()
            if (n.pain >= 0) parts += context.getString(R.string.pdf_pain, n.pain)
            if (n.sleep.isNotBlank()) {
                parts += context.getString(
                    R.string.pdf_sleep,
                    OptionLabels.label(OptionGroup.Sleep, n.sleep, ar),
                )
            }
            if (n.mood.isNotBlank()) {
                parts += context.getString(
                    R.string.pdf_mood,
                    OptionLabels.label(OptionGroup.Mood, n.mood, ar),
                )
            }
            if (parts.isNotEmpty()) {
                trendLines += context.getString(
                    R.string.pdf_observation_line,
                    n.date,
                    parts.joinToString(listSeparator),
                )
            }
        }
        if (trendLines.isEmpty()) {
            drawTextBlock(context.getString(R.string.pdf_no_trends), paint, 12f)
        } else {
            trendLines.forEach { drawTextBlock(it, paint, 4f) }
            y += 8f
        }

        if (summary.uncertainties.isNotEmpty()) {
            drawTextBlock(context.getString(R.string.pdf_unclear), sectionPaint, 4f)
            summary.uncertainties.forEach { u ->
                drawTextBlock("• ${uncertaintyText(context, u)}", paint, 4f)
            }
            y += 8f
        }

        drawTextBlock(context.getString(R.string.pdf_disclaimer_title), sectionPaint, 4f)
        drawTextBlock(context.getString(R.string.pdf_disclaimer), paint, 12f)

        doc.finishPage(page)
        try {
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    /**
     * System-generated uncertainty topics get localized copy; a model-invented
     * topic shows only its detail, mirroring the on-screen summary.
     */
    private fun uncertaintyText(
        context: Context,
        uncertainty: SummaryDto.UncertaintyDto,
    ): String = when (uncertainty.topic) {
        "ai_unavailable" -> context.getString(R.string.ai_unavailable)
        "ai_not_configured" -> context.getString(R.string.summary_ai_not_configured)
        "invalid_evidence" -> context.getString(R.string.summary_unclear_invalid_evidence)
        "unverified_claim" -> context.getString(R.string.summary_unclear_unverified)
        else -> uncertainty.detail.ifBlank { uncertainty.topic }
    }

    private fun flagLabel(context: Context, flag: String): String = when (flag) {
        "FALL_REPORTED" -> context.getString(R.string.flag_fall)
        "HIGH_PAIN" -> context.getString(R.string.flag_pain)
        "BREATHING_TROUBLE" -> context.getString(R.string.flag_breathing_trouble)
        "LOSS_OF_CONSCIOUSNESS" -> context.getString(R.string.flag_loss_of_consciousness)
        "REPEATED_MISSED_MED" -> context.getString(R.string.flag_missed_med)
        "MEDICATION_UNVERIFIED" -> context.getString(R.string.flag_med_unverified)
        "POOR_APPETITE_DAYS" -> context.getString(R.string.flag_poor_appetite)
        "MODEL_URGENT" -> context.getString(R.string.flag_model_urgent)
        else -> context.getString(R.string.flag_unknown)
    }
}
