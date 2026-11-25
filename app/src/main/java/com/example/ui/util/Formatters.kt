package com.example.ui.util

import java.util.Locale

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return ""
    return "${formatFileSize(bytesPerSec)}/s"
}

fun formatDuration(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%d:%02d", mins, secs)
    }
}
