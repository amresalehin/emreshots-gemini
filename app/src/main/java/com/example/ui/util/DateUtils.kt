package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    // ThreadLocal formatters to prevent continuous object allocation in Composables
    private val fullDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    }

    private val shortDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    }

    private val timeOnlyFormat = ThreadLocal.withInitial {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    fun formatFullDate(timestamp: Long): String {
        return fullDateFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatShortDate(timestamp: Long): String {
        return shortDateFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatTimeOnly(timestamp: Long): String {
        return timeOnlyFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "Video"
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remMinutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
}
