package com.example

import com.example.data.PlaybackSessionDurations
import com.example.data.PlaybackSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSessionDurationsTest {
    @Test
    fun `zero duration marker records that a session was counted on a date`() {
        val session = PlaybackSessionEntity(
            date = "2026-09-10",
            year = 2026,
            month = 9,
            startTime = 0L,
            dailyDurations = "{\"2026-09-10\":30,\"2026-09-11\":0}"
        )

        assertEquals(true, PlaybackSessionDurations.includesDate(session, "2026-09-11"))
        assertEquals(0L, PlaybackSessionDurations.durationForDate(session, "2026-09-11"))
    }

    @Test
    fun `date contributions keep a cross-midnight session split`() {
        val session = PlaybackSessionEntity(
            date = "2026-09-10",
            year = 2026,
            month = 9,
            startTime = 0L,
            durationSeconds = 120L,
            dailyDurations = "{\"2026-09-10\":90,\"2026-09-11\":30}"
        )

        assertEquals(90L, PlaybackSessionDurations.durationForDate(session, "2026-09-10"))
        assertEquals(30L, PlaybackSessionDurations.durationForDate(session, "2026-09-11"))
        assertEquals(
            30L,
            PlaybackSessionDurations.durationForPeriod(session) { it == "2026-09-11" }
        )
    }

    @Test
    fun `legacy sessions use their start date as their contribution`() {
        val session = PlaybackSessionEntity(
            date = "2026-09-10",
            year = 2026,
            month = 9,
            startTime = 0L,
            durationSeconds = 120L
        )

        assertEquals(120L, PlaybackSessionDurations.durationForDate(session, "2026-09-10"))
        assertEquals(0L, PlaybackSessionDurations.durationForDate(session, "2026-09-11"))
    }

    @Test
    fun `legacy sessions expose the overlap on the following day`() {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        val start = format.parse("2026-09-10 23:59")!!.time
        val end = format.parse("2026-09-11 00:01")!!.time
        val session = PlaybackSessionEntity(
            date = "2026-09-10",
            year = 2026,
            month = 9,
            startTime = start,
            endTime = end,
            durationSeconds = 120L
        )

        assertEquals(60L, PlaybackSessionDurations.durationForDate(session, "2026-09-10"))
        assertEquals(60L, PlaybackSessionDurations.durationForDate(session, "2026-09-11"))
        assertEquals(
            60L,
            PlaybackSessionDurations.durationForPeriod(session) { it == "2026-09-11" }
        )
    }
}
