package com.filmfx.app.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val dateTimeFormatter = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    fun formatDate(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormatter.format(Date(timestamp))
    }
}
