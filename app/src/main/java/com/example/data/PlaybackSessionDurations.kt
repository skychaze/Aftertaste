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
        }.getOrDefault(emptyMap())
        if (parsed.isNotEmpty()) return parsed
        return Regex("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\d+)")
            .findAll(raw)
            .associate { match -> match.groupValues[1] to match.groupValues[2].toLong() }
    }

    fun encode(durations: Map<String, Long>): String? {
        val nonNegative = durations.mapValues { (_, seconds) -> seconds.coerceAtLeast(0L) }
        if (nonNegative.isEmpty()) return null
        return JSONObject().apply {
            nonNegative.forEach { (date, seconds) -> put(date, seconds) }
        }.toString()
    }

    fun includesDate(session: PlaybackSessionEntity, date: String): Boolean {
        return session.date == date || parse(session.dailyDurations).containsKey(date)
    }

    fun contributionsForSession(session: PlaybackSessionEntity): Map<String, Long> {
        val durations = parse(session.dailyDurations).filterValues { it > 0L }
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
        if (result.isEmpty() && session.durationSeconds > 0L) {
            return mapOf(session.date to session.durationSeconds)
        }
        val totalOverlap = result.values.sum()
        if (totalOverlap <= session.durationSeconds) return result

        val capped = linkedMapOf<String, Long>()
        var remaining = session.durationSeconds
        for ((date, seconds) in result) {
            if (remaining <= 0L) break
            val contribution = minOf(seconds, remaining)
            if (contribution > 0L) capped[date] = contribution
            remaining -= contribution
        }
        return capped
    }

    fun durationForDate(session: PlaybackSessionEntity, date: String): Long {
        val durations = parse(session.dailyDurations)
        if (durations.values.any { it > 0L }) return durations[date] ?: 0L
        return contributionsForSession(session)[date] ?: 0L
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
        return (overlapMillis / 1000L).coerceAtLeast(0L)
    }

    fun durationForPeriod(
        session: PlaybackSessionEntity,
        includeDate: (String) -> Boolean
    ): Long {
        val durations = parse(session.dailyDurations)
        if (durations.values.any { it > 0L }) {
            return durations.filterKeys(includeDate).values.sum()
        }
        return contributionsForSession(session)
            .filterKeys(includeDate)
            .values
            .sum()
    }
}
