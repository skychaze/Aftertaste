package com.example

import com.example.data.GenreAggregateRow
import com.example.data.PlaybackSessionEntity
import com.example.data.TrackAggregateRow
import com.example.ui.DateBounds
import com.example.ui.GenrePeriodAdjustments
import com.example.ui.ScopedGenreSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenrePeriodAdjustmentsTest {
    private val bounds = DateBounds("2026-09-01", "2026-09-30")

    @Test
    fun `short incoming boundary contribution remains in genre totals and tracks`() {
        val session = PlaybackSessionEntity(
            date = "2026-08-31",
            year = 2026,
            month = 8,
            startTime = 0L,
            durationSeconds = 10L,
            title = "Short split",
            artist = "Artist",
            genre = "Pop",
            playCount = 1,
            dailyDurations = "{\"2026-08-31\":6,\"2026-09-01\":4}"
        )
        val scoped = ScopedGenreSession(session, "Pop", seconds = 4L)
        val key = GenrePeriodAdjustments.trackKey("Pop", session.title, session.artist)

        val genres = GenrePeriodAdjustments.adjustGenreAggregates(
            baseRows = emptyList(),
            sessions = listOf(scoped),
            bounds = bounds,
            baseTracks = mapOf(key to null)
        )
        val tracks = GenrePeriodAdjustments.adjustGenreTracks(
            baseRows = emptyList(),
            sessions = listOf(scoped),
            genre = "Pop",
            bounds = bounds,
            baseTracks = mapOf(key to null)
        )

        assertEquals(4L, genres.single().totalSeconds)
        assertEquals(1, genres.single().trackCount)
        assertEquals(4L, tracks.single().totalSeconds)
        assertEquals(1, tracks.single().playCount)
    }

    @Test
    fun `outgoing boundary-only track is removed from genre track count`() {
        val boundary = session("Outgoing", "Jazz", "2026-09-30", 10L)
        val key = GenrePeriodAdjustments.trackKey("Jazz", boundary.title, boundary.artist)
        val baseTrack = track("Outgoing", "Jazz", 10L)

        val genres = GenrePeriodAdjustments.adjustGenreAggregates(
            baseRows = listOf(GenreAggregateRow("Jazz", 30L, 2)),
            sessions = listOf(ScopedGenreSession(boundary, "Jazz", seconds = 0L)),
            bounds = bounds,
            baseTracks = mapOf(key to baseTrack)
        )

        assertEquals(20L, genres.single().totalSeconds)
        assertEquals(1, genres.single().trackCount)
    }

    @Test
    fun `top track query includes a replacement for outgoing boundary candidates`() {
        val boundary = session("Song 99", "Rock", "2026-09-30", 1_901L)
        val scoped = listOf(ScopedGenreSession(boundary, "Rock", seconds = 0L))
        val baseRows = (0..100).map { index -> track("Song $index", "Rock", 2_000L - index) }
        val key = GenrePeriodAdjustments.trackKey("Rock", boundary.title, boundary.artist)

        assertEquals(101, GenrePeriodAdjustments.topTracksQueryLimit(scoped, "Rock", bounds))

        val adjusted = GenrePeriodAdjustments.adjustGenreTracks(
            baseRows = baseRows,
            sessions = scoped,
            genre = "Rock",
            bounds = bounds,
            baseTracks = mapOf(key to baseRows[99])
        )

        assertEquals(100, adjusted.size)
        assertFalse(adjusted.any { it.title == "Song 99" })
        assertTrue(adjusted.any { it.title == "Song 100" })
    }

    private fun session(title: String, genre: String, date: String, duration: Long) = PlaybackSessionEntity(
        date = date,
        year = date.substring(0, 4).toInt(),
        month = date.substring(5, 7).toInt(),
        startTime = 0L,
        durationSeconds = duration,
        title = title,
        artist = "Artist",
        genre = genre
    )

    private fun track(title: String, genre: String, seconds: Long) = TrackAggregateRow(
        title = title,
        artist = "Artist",
        album = null,
        genre = genre,
        artworkUrl = null,
        totalSeconds = seconds,
        playCount = 1,
        lastPlayedTimestamp = 0L
    )
}
