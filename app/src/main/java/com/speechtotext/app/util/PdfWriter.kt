package com.speechtotext.app.util

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.speechtotext.app.domain.Transcript
import java.io.OutputStream

object PdfWriter {

    fun writePdf(transcript: Transcript, outputStream: OutputStream) {
        val document = PdfDocument()
        val pageWidth = 595 // A4 standard width in points
        val pageHeight = 842 // A4 standard height in points
        val margin = 40
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 150, 220)
            textSize = 20f
            isFakeBoldText = true
        }

        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }

        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Draw Header
        var y = margin.toFloat()

        val textDir = if (transcript.isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val alignment = if (transcript.isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL

        // Title Layout
        val titleLayout = StaticLayout.Builder.obtain(
            transcript.title, 0, transcript.title.length, titlePaint, contentWidth
        ).setTextDirection(textDir).setAlignment(alignment).build()

        canvas.save()
        canvas.translate(margin.toFloat(), y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 8

        // Metadata
        val timeStr = TimeFormat.formatSecondsToDisplay(transcript.durationSeconds)
        val metaText = "Language: ${transcript.languageName}  |  Duration: $timeStr  |  Words: ${transcript.wordCount}"
        val metaLayout = StaticLayout.Builder.obtain(
            metaText, 0, metaText.length, metaPaint, contentWidth
        ).build()

        canvas.save()
        canvas.translate(margin.toFloat(), y)
        metaLayout.draw(canvas)
        canvas.restore()
        y += metaLayout.height + 12

        // Divider
        canvas.drawLine(margin.toFloat(), y, (pageWidth - margin).toFloat(), y, linePaint)
        y += 16

        // Paragraphs
        val paragraphs = transcript.fullText.split("\n").filter { it.isNotBlank() }

        for (para in paragraphs) {
            val paraLayout = StaticLayout.Builder.obtain(
                para, 0, para.length, bodyPaint, contentWidth
            ).setTextDirection(textDir).setAlignment(alignment).build()

            // Check page overflow
            if (y + paraLayout.height > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin.toFloat()
            }

            canvas.save()
            canvas.translate(margin.toFloat(), y)
            paraLayout.draw(canvas)
            canvas.restore()
            y += paraLayout.height + 12
        }

        document.finishPage(page)

        try {
            document.writeTo(outputStream)
        } finally {
            document.close()
        }
    }
}
