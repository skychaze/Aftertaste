package com.example

import android.content.Context
import android.media.session.PlaybackState
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PlaybackSessionEntity
import com.example.tracker.PlaybackCommand
import com.example.tracker.PlaybackControls
import com.example.ui.DateBounds
import com.example.ui.GenreScope
import com.example.ui.ListeningPeriods
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ListeningExperienceTest {
    @Test
    fun `taste periods cross years and clamp calendar months`() {
        assertEquals(
            DateBounds("2025-11-03", "2026-02-02"),
            ListeningPeriods.bounds(GenreScope.THREE_MONTHS, "2026-02-02"),
        )
        assertEquals(
            DateBounds("2025-08-03", "2026-02-02"),
            ListeningPeriods.bounds(GenreScope.SIX_MONTHS, "2026-02-02"),
        )
        assertEquals(
            DateBounds("2024-03-01", "2024-03-31"),
            ListeningPeriods.bounds(GenreScope.MONTH, "2024-03-31"),
        )
    }

    @Test
    fun `controls distinguish resume and pause capabilities`() {
        val paused =
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 0L, 0f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SKIP_TO_NEXT)
                .build()
        val controls = PlaybackControls.from(paused)
        assertTrue(controls.supports(PlaybackCommand.PLAY_PAUSE))
        assertTrue(controls.supports(PlaybackCommand.NEXT))
        assertFalse(controls.supports(PlaybackCommand.PREVIOUS))
        val playing =
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .setActions(PlaybackState.ACTION_PLAY)
                .build()
        assertFalse(PlaybackControls.from(playing).canPlayPause)
        assertFalse(PlaybackControls.from(null).canPlayPause)
    }

    @Test
    fun `history aggregates repeats searches literally and ranks by the selected metric`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database =
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            try {
                val dao = database.musicTrackerDao()
                suspend fun add(
                    title: String,
                    artist: String,
                    seconds: Long,
                    plays: Int,
                    time: Long,
                    date: String = "2026-10-02",
                    source: String = "com.google.android.apps.youtube.music",
                ) {
                    dao.insertSession(
                        PlaybackSessionEntity(
                            date = date,
                            year = 2026,
                            month = 10,
                            startTime = time,
                            endTime = time + seconds * 1000L,
                            durationSeconds = seconds,
                            title = title,
                            artist = artist,
                            album = "Album",
                            genre = "Rock",
                            sourcePackage = source,
                            playCount = plays,
                            isOpen = false,
                        )
                    )
                }
                add("Return", "Artist", 120L, 2, 1L)
                add("return", "ARTIST", 60L, 1, 2L)
                add("Long song", "Band", 600L, 1, 3L)
                add("Newest", "Band", 90L, 2, 9L)
                add("100%", "Artist", 45L, 1, 4L)
                add("Old", "Artist", 999L, 50, 0L, "2026-08-01")
                add("Video", "Artist", 999L, 50, 0L, source = "com.google.android.youtube")
                val start = "2026-09-03"
                val end = "2026-10-02"
                val plays = dao.getListeningTracks(start, end, null, "", "PLAYS", 50).first()
                assertEquals("return", plays.first().title.lowercase())
                assertEquals(3, plays.first().playCount)
                assertEquals(180L, plays.first().totalSeconds)
                assertEquals(
                    "Long song",
                    dao.getListeningTracks(start, end, null, "", "TIME", 50).first().first().title,
                )
                assertEquals(
                    "Newest",
                    dao.getListeningTracks(start, end, null, "", "RECENT", 50).first().first().title,
                )
                assertEquals(
                    listOf("100%"),
                    dao.getListeningTracks(start, end, null, "%", "RECENT", 50).first().map {
                        it.title
                    },
                )
                assertEquals(
                    2,
                    dao.getListeningTracks(start, end, null, "artist", "RECENT", 50).first().size,
                )
                val artist = dao.getListeningArtists(start, end, null).first().first()
                assertEquals("Band", artist.artist)
                assertEquals(690L, artist.totalSeconds)
                assertEquals(4, plays.size)
            } finally {
                database.close()
            }
        }
}
