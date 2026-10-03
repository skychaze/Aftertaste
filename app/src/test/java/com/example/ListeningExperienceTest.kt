package com.example

import android.content.Context
import android.media.session.PlaybackState
import android.support.v4.media.session.PlaybackStateCompat
import com.example.tracker.RepeatMode
import com.example.data.ResolvedGenreEntity
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
    fun `seek and repeat follow session capabilities`() {
        val state = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PAUSED, 30_000L, 0f)
            .setActions(PlaybackState.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SET_REPEAT_MODE)
            .build()
        assertTrue(PlaybackControls.from(state).canSeek)
        assertTrue(PlaybackControls.from(state).supports(PlaybackCommand.REPEAT))
        assertFalse(PlaybackControls.from(null).canSeek)
        assertFalse(PlaybackControls.from(null).canRepeat)
        val customRepeat = PlaybackState.Builder().addCustomAction(
            PlaybackState.CustomAction.Builder(
                PlaybackControls.YOUTUBE_MUSIC_REPEAT_ACTION, "Repeat off", android.R.drawable.ic_media_play
            ).build()
        ).build()
        assertTrue(PlaybackControls.from(customRepeat).canRepeat)
        assertEquals(RepeatMode.OFF, RepeatMode.fromYouTubeMusicIcon("repeat_off"))
        assertEquals(RepeatMode.ONE, RepeatMode.fromYouTubeMusicIcon("repeat_one"))
        assertEquals(RepeatMode.ALL, RepeatMode.fromYouTubeMusicIcon("repeat_all"))
        assertNull(RepeatMode.fromYouTubeMusicIcon("unknown"))
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
        assertEquals(RepeatMode.ONE, RepeatMode.from(PlaybackStateCompat.REPEAT_MODE_ONE))
    }

    @Test
    fun `existing genres include saved labels without listening history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.musicTrackerDao()
            dao.putResolvedGenre(ResolvedGenreEntity("a", "Rock", 1.0, "manual", 1L))
            dao.putResolvedGenre(ResolvedGenreEntity("b", "Jazz", 1.0, "manual", 1L))
            dao.putResolvedGenre(ResolvedGenreEntity("c", "Rock", 1.0, "manual", 1L))
            dao.putResolvedGenre(ResolvedGenreEntity("d", "  ", 1.0, "manual", 1L))
            assertEquals(listOf("Jazz", "Rock"), dao.getExistingGenres().first())
        } finally {
            database.close()
        }
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
