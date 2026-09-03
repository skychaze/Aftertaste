package com.example.util

/**
 * Dynamic Time Formatting Logic strictly conforming to specification:
 *
 * Threshold Rules:
 * - 1 Day = 24 full hours.
 * - 1 Month = 30 full days (720 hours).
 * - Displays whole units without decimals; units roll over only upon hitting exact full-integer thresholds.
 *
 * Display Formats:
 * - Under 24 Hours: [Y] Hour(s) (e.g. 0 Hours, 1 Hour, 14 Hours)
 * - 24 Hours to 29 Days: [X] Day(s) [Y] Hour(s) (e.g. 29 hours -> 1 Day 5 Hours)
 * - 30 Days and Above: [M] Month(s) [D] Day(s) (e.g. 35 days -> 1 Month 5 Days; increments to 2 Months at 60 full days)
 */
object TimeFormatUtils {

    fun formatDynamicTime(totalSeconds: Long): String {
        val totalHours = (totalSeconds / 3600L).coerceAtLeast(0L)
        val totalDays = totalHours / 24L

        return when {
            totalDays >= 30L -> {
                val months = totalDays / 30L
                val remDays = totalDays % 30L
                val monthUnit = if (months == 1L) "Month" else "Months"
                val dayUnit = if (remDays == 1L) "Day" else "Days"
                if (remDays > 0L) {
                    "$months $monthUnit $remDays $dayUnit"
                } else {
                    "$months $monthUnit"
                }
            }
            totalDays >= 1L -> {
                val days = totalDays
                val remHours = totalHours % 24L
                val dayUnit = if (days == 1L) "Day" else "Days"
                val hourUnit = if (remHours == 1L) "Hour" else "Hours"
                if (remHours > 0L) {
                    "$days $dayUnit $remHours $hourUnit"
                } else {
                    "$days $dayUnit"
                }
            }
            else -> {
                val hours = totalHours
                val hourUnit = if (hours == 1L) "Hour" else "Hours"
                "$hours $hourUnit"
            }
        }
    }

    /**
     * Formats duration for individual song / track items (e.g. "3m 45s" or "45s").
     */
    fun formatTrackDuration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val m = s / 60L
        val remS = s % 60L
        return if (m > 0L) {
            "${m}m ${remS}s"
        } else {
            "${remS}s"
        }
    }
}
