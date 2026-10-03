package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    fun monthStart(): Calendar {
        val now = Calendar.getInstance()
        return monthStart(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    }

    fun monthStart(year: Int, month: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    fun monthEnd(year: Int, month: Int): Calendar =
        monthStart(year, month).apply { add(Calendar.MONTH, 1) }

    // month is Calendar-style (0 = Jan, 11 = Dec); key is zero-padded "yyyy-MM".
    fun monthKey(year: Int, month: Int): String =
        String.format(Locale.US, "%04d-%02d", year, month + 1)

    fun currentMonthKey(): String {
        val now = Calendar.getInstance()
        return monthKey(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
