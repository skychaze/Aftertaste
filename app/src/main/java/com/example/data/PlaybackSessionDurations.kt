package com.example.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PlaybackSessionDurations {
    fun parse(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        val parsed = runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence()
                .associateWith { json.optLong(it).coerceAtLeast(0L) }
                .filterValues { it > 0L }
        }.getOrDefault(emptyMap())
        if (parsed.isNotEmpty()) return parsed
        return Regex("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\d+)")
            .findAll(raw)
            .associate { match -> match.groupValues[1] to match.groupValues[2].toLong() }
            .filterValues { it > 0L }
    }

    fun encode(durations: Map<String, Long>): String? {
        val positive = durations.filterValues { it > 0L }
        if (positive.isEmpty()) return null
        return JSONObject().apply {
            positive.forEach { (date, seconds) -> put(date, seconds) }
        }.toString()
    }

    fun contributionsForSession(session: PlaybackSessionEntity): Map<String, Long> {
        val durations = parse(session.dailyDurations)
        if (durations.isNotEmpty()) return durations
        if (session.endTime <= session.startTime) {
            return if (session.durationSeconds > 0L) mapOf(session.date to session.durationSeconds) else emptyMap()
        }

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val day = Calendar.getInstance().apply {
            timeInMillis = session.startTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val lastDay = Calendar.getInstance().apply {
            timeInMillis = session.endTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = linkedMapOf<String, Long>()
        while (!day.after(lastDay)) {
            val date = formatter.format(day.time)
            val seconds = legacyOverlapSeconds(session, date)
            if (seconds > 0L) result[date] = seconds
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        return if (result.isEmpty() && session.durationSeconds > 0L) {
            mapOf(session.date to session.durationSeconds)
        } else {
            result
        }
    }

    fun durationForDate(session: PlaybackSessionEntity, date: String): Long {
        val durations = parse(session.dailyDurations)
        if (durations.isNotEmpty()) return durations[date] ?: 0L
        val overlap = legacyOverlapSeconds(session, date)
        return if (overlap > 0L) overlap else if (session.date == date) session.durationSeconds else 0L
    }

    private fun legacyOverlapSeconds(session: PlaybackSessionEntity, date: String): Long {
        if (session.durationSeconds <= 0L || session.endTime <= session.startTime) return 0L
        val dayStart = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }.parse(date)?.time
        }.getOrNull() ?: return 0L
        val dayEnd = Calendar.getInstance().apply {
            time = Date(dayStart)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val overlapMillis = (minOf(session.endTime, dayEnd) - maxOf(session.startTime, dayStart)).coerceAtLeast(0L)
        return (overlapMillis / 1000L).coerceAtMost(session.durationSeconds)
    }

    fun durationForPeriod(
        session: PlaybackSessionEntity,
        includeDate: (String) -> Boolean
    ): Long {
        val durations = parse(session.dailyDurations)
        if (durations.isNotEmpty()) {
            return durations.filterKeys(includeDate).values.sum()
        }

        if (session.endTime > session.startTime) {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val day = Calendar.getInstance().apply {
                timeInMillis = session.startTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val lastDay = Calendar.getInstance().apply {
                timeInMillis = session.endTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var total = 0L
            while (!day.after(lastDay)) {
                val date = formatter.format(day.time)
                if (includeDate(date)) total += legacyOverlapSeconds(session, date)
                day.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (total > 0L) return total
        }

        return if (includeDate(session.date)) session.durationSeconds else 0L
    }
}
