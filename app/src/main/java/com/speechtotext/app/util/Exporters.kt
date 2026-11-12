package com.speechtotext.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.speechtotext.app.domain.Transcript
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    TXT("txt", "text/plain", "Plain Text (.txt)"),
    SRT("srt", "application/x-subrip", "Subtitles (.srt)"),
    VTT("vtt", "text/vtt", "Web Subtitles (.vtt)"),
    JSON("json", "application/json", "Timings JSON (.json)"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word Doc (.docx)"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel Sheet (.xlsx)"),
    PDF("pdf", "application/pdf", "PDF Document (.pdf)")
}

object Exporters {

    private val json = Json { prettyPrint = true }

    fun writeToUri(
        context: Context,
        uri: Uri,
        format: ExportFormat,
        transcript: Transcript,
        captionLines: List<CaptionLine>
    ): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                exportToStream(format, transcript, captionLines, outputStream)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun exportToStream(
        format: ExportFormat,
        transcript: Transcript,
        captionLines: List<CaptionLine>,
        stream: OutputStream
    ) {
        when (format) {
            ExportFormat.TXT -> {
                stream.bufferedWriter().use { it.write(transcript.fullText) }
            }
            ExportFormat.SRT -> {
                val srt = CaptionGrouper.toSrt(captionLines)
                stream.bufferedWriter().use { it.write(srt) }
            }
            ExportFormat.VTT -> {
                val vtt = CaptionGrouper.toVtt(captionLines)
                stream.bufferedWriter().use { it.write(vtt) }
            }
            ExportFormat.JSON -> {
                val jsonString = json.encodeToString(transcript)
                stream.bufferedWriter().use { it.write(jsonString) }
            }
            ExportFormat.DOCX -> {
                DocxWriter.writeDocx(transcript, stream)
            }
            ExportFormat.XLSX -> {
                XlsxWriter.writeXlsx(captionLines, stream)
            }
            ExportFormat.PDF -> {
                PdfWriter.writePdf(transcript, stream)
            }
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Transcript") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    fun shareText(context: Context, text: String, title: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Transcript Share Karo")
        context.startActivity(shareIntent)
    }
}
