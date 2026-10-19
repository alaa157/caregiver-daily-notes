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
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SummaryDto
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

        fun ensureSpace(h: Float) {
            if (y + h > pageHeight - margin) newPage()
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

        val ar = languageTag.startsWith("ar")
        drawTextBlock(if (ar) "ملخص الرعاية" else "Care Summary", titlePaint, 4f)
        drawTextBlock("Caregiver Daily Notes", paint, 8f)
        recipientName?.takeIf { it.isNotBlank() }?.let { drawTextBlock((if (ar) "المستفيد: " else "Recipient: ") + it, paint, 4f) }
        drawTextBlock((if (ar) "الفترة: " else "Period: ") + summary.periodDays + " days", paint, 4f)
        drawTextBlock((if (ar) "تاريخ التقرير: " else "Generated: ") + LocalDate.now().toString(), paint, 12f)

        drawTextBlock(if (ar) "الملخص" else "Executive summary", sectionPaint, 4f)
        drawTextBlock(if (summary.text.isBlank()) (if (ar) "لا توجد ملاحظات مدعومة." else "No supported observations found.") else summary.text, paint, 12f)

        drawTextBlock(if (ar) "التنبيهات" else "Care flags", sectionPaint, 4f)
        if (summary.redFlags.isEmpty()) {
            drawTextBlock(if (ar) "لا توجد تنبيهات رعاية مسجلة خلال هذه الفترة." else "No recorded care flags in this reporting period.", paint, 12f)
        } else {
            summary.redFlags.forEach { drawTextBlock("• $it", paint, 4f) }
            y += 8f
        }

        if (summary.evidence.isNotEmpty()) {
            drawTextBlock(if (ar) "الأدلة" else "Supporting evidence", sectionPaint, 4f)
            summary.evidence.forEach { ev ->
                drawTextBlock("${ev.noteId}: ${ev.quote}", paint, 8f)
            }
        }

        drawTextBlock(if (ar) "الملاحظات المسجلة" else "Recorded observations", sectionPaint, 4f)
        val trendLines = mutableListOf<String>()
        notes.sortedBy { it.date }.forEach { n ->
            val parts = mutableListOf<String>()
            if (n.pain >= 0) parts += (if (ar) "الألم: " else "Pain: ") + n.pain
            if (n.sleep.isNotBlank()) parts += (if (ar) "النوم: " else "Sleep: ") + n.sleep
            if (n.mood.isNotBlank()) parts += (if (ar) "المزاج: " else "Mood: ") + n.mood
            if (parts.isNotEmpty()) trendLines += "${n.date}: " + parts.joinToString(", ")
        }
        if (trendLines.isEmpty()) {
            drawTextBlock(if (ar) "لا توجد بيانات اتجاه مسجلة خلال هذه الفترة." else "No recorded trend data for this period.", paint, 12f)
        } else {
            trendLines.forEach { drawTextBlock(it, paint, 4f) }
            y += 8f
        }

        if (summary.uncertainties.isNotEmpty()) {
            drawTextBlock(if (ar) "معلومات غير واضحة" else "Unclear information", sectionPaint, 4f)
            summary.uncertainties.forEach { u -> drawTextBlock("${u.topic}: ${u.detail}", paint, 4f) }
            y += 8f
        }

        drawTextBlock(if (ar) "إخلاء المسؤولية" else "Disclaimer", sectionPaint, 4f)
        val disclaimer = if (ar)
            "يلخص هذا التقرير الملاحظات المسجلة بواسطة مقدم الرعاية خلال الفترة المحددة، بهدف دعم التواصل مع المختصين في الرعاية الصحية. ولا يُعد تشخيصًا طبيًا أو بديلًا عن التقييم الطبي المتخصص."
        else
            "This report summarizes caregiver-recorded observations and available care notes for the stated period. It is intended to support communication with healthcare professionals and is not a medical diagnosis or a substitute for professional medical assessment."
        drawTextBlock(disclaimer, paint, 12f)

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }
}
