package com.speechtotext.app.util

import java.util.Locale

object TimeFormat {

    fun formatSecondsToDisplay(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong().coerceAtLeast(0L)
        val hrs = totalMs / 3600000
        val mins = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatToSrtTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong().coerceAtLeast(0L)
        val hrs = totalMs / 3600000
        val mins = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val ms = totalMs % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hrs, mins, secs, ms)
    }

    fun formatToVttTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong().coerceAtLeast(0L)
        val hrs = totalMs / 3600000
        val mins = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val ms = totalMs % 1000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hrs, mins, secs, ms)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
    }
}
