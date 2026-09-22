package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DailyStatEntity
import com.example.data.MusicTrackerRepository
import com.example.data.PlaybackSessionEntity
import com.example.tracker.YouTubeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AfterTaste", appName)
  }

  @Test
  fun `verify YouTube Music package detection`() {
    assertTrue(YouTubeHelper.isYouTubeMusic("com.google.android.apps.youtube.music"))
    assertFalse(YouTubeHelper.isYouTubeMusic("com.google.android.youtube"))
    assertFalse(YouTubeHelper.isYouTubeMusic("com.google.android.apps.youtube.kids"))
  }

  @Test
  fun `verify YouTube Video package exclusion`() {
    assertTrue(YouTubeHelper.isYouTubeVideoPackage("com.google.android.youtube"))
    assertTrue(YouTubeHelper.isYouTubeVideoPackage("com.google.android.apps.youtube.kids"))
    assertFalse(YouTubeHelper.isYouTubeVideoPackage("com.google.android.apps.youtube.music"))
    assertFalse(YouTubeHelper.isYouTubeVideoPackage("com.spotify.music"))
  }

  @Test
  fun `verify YouTube Video notification filtering`() {
    // YouTube video notification with video/channel cues
    assertTrue(
      YouTubeHelper.isYouTubeVideoNotification(
        pkg = "com.google.android.youtube",
        title = "Top 10 Coding Moments",
        text = "Tech Channel",
        subText = "YouTube"
      )
    )
    // Legitimate YouTube Music playback notification
    assertFalse(
      YouTubeHelper.isYouTubeVideoNotification(
        pkg = "com.google.android.apps.youtube.music",
        title = "Blinding Lights",
        text = "The Weeknd",
        subText = "After Hours"
      )
    )
  }

  @Test
  fun `verify track matching and loop detection logic`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.AppDatabase.getInstance(context)
    val repo = com.example.data.MusicTrackerRepository(db.musicTrackerDao())
    val engine = com.example.tracker.MusicTrackerEngine.getInstance(context, repo)

    assertTrue(engine.isPlaceholderTitle("YouTube Music"))
    assertTrue(engine.isPlaceholderTitle("Music Track"))

    // Verify track matching
    assertTrue(
      engine.isSameTrack(
        "Starboy (ft. Daft Punk)",
        "The Weeknd",
        "Starboy",
        "The Weeknd • YouTube Music"
      )
    )

    // Simulate starting a track
    engine.onPlaybackStarted(
      title = "Levitating",
      artist = "Dua Lipa • YouTube Music",
      album = "Future Nostalgia",
      pkg = "com.google.android.apps.youtube.music",
      isYt = true
    )

    assertTrue(engine.uiState.value.isActivelyPlaying)
    assertEquals("Levitating", engine.uiState.value.trackTitle)

    // Simulate 5 loops
    for (i in 1..5) {
      engine.simulateTrackLoop()
    }

    // Engine is still playing actively and accurately tracking repeats
    assertTrue(engine.uiState.value.isActivelyPlaying)
    assertEquals("Levitating", engine.uiState.value.trackTitle)
  }

  @Test
  fun `track matching does not merge title or artist prefixes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.AppDatabase.getInstance(context)
    val repo = com.example.data.MusicTrackerRepository(db.musicTrackerDao())
    val engine = com.example.tracker.MusicTrackerEngine.getInstance(context, repo)

    assertFalse(engine.isSameTrack("Hello", "Adele", "Hello Again", "Adele"))
    assertFalse(engine.isSameTrack("Alive", "Queen", "Alive", "Queens of the Stone Age"))
  }

  @Test
  fun `sample data does not overwrite an existing year`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.musicTrackerDao()
      dao.insertOrUpdateDaily(
        DailyStatEntity(
          date = "2026-01-01",
          year = 2026,
          month = 1,
          day = 1,
          dayOfWeek = 5,
          totalPlayTimeSeconds = 123L,
          sessionCount = 1
        )
      )

      MusicTrackerRepository(dao).seedSampleAnalyticsForYear(2026)

      assertEquals(123L, dao.getDailyStatSync("2026-01-01")?.totalPlayTimeSeconds)
      assertTrue(dao.getAllSessionsSync().isEmpty())
    } finally {
      db.close()
    }
  }

  @Test
  fun `analytics queries stay inside the requested period`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.musicTrackerDao()
      dao.insertOrUpdateDaily(DailyStatEntity("2026-09-01", 2026, 9, 1, 3, 600L, 1))
      dao.insertOrUpdateDaily(DailyStatEntity("2026-08-31", 2026, 8, 31, 2, 900L, 1))
      dao.insertSession(
        PlaybackSessionEntity(
          date = "2026-09-01",
          year = 2026,
          month = 9,
          startTime = 1L,
          durationSeconds = 600L,
          title = "September song",
          artist = "Artist",
          genre = "Rock",
          isOpen = false
        )
      )
      dao.insertSession(
        PlaybackSessionEntity(
          date = "2026-08-31",
          year = 2026,
          month = 8,
          startTime = 2L,
          durationSeconds = 900L,
          title = "August song",
          artist = "Artist",
          genre = "Pop",
          isOpen = false
        )
      )

      val stats = dao.getDailyStatsBetween("2026-09-01", "2026-09-30").first()
      val genres = dao.getGenreAggregates("2026-09-01", "2026-09-30").first()

      assertEquals(listOf("2026-09-01"), stats.map { it.date })
      assertEquals(listOf("Rock"), genres.map { it.genreName })
      assertEquals(600L, genres.single().totalSeconds)
    } finally {
      db.close()
    }
  }

  @Test
  fun `genre queries return aggregates and only sessions crossing range edges`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.musicTrackerDao()
      val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
      val rangeStart = formatter.parse("2026-09-01 00:00")!!.time
      val rangeEnd = formatter.parse("2026-10-01 00:00")!!.time
      val incomingId = dao.insertSession(
        PlaybackSessionEntity(
          date = "2026-08-31",
          year = 2026,
          month = 8,
          startTime = formatter.parse("2026-08-31 23:55")!!.time,
          endTime = formatter.parse("2026-09-01 00:05")!!.time,
          durationSeconds = 600L,
          title = "Incoming song",
          artist = "Artist",
          genre = "Rock",
          dailyDurations = "{\"2026-08-31\":300,\"2026-09-01\":300}",
          isOpen = false
        )
      )
      val outgoingId = dao.insertSession(
        PlaybackSessionEntity(
          date = "2026-09-30",
          year = 2026,
          month = 9,
          startTime = formatter.parse("2026-09-30 23:55")!!.time,
          endTime = formatter.parse("2026-10-01 00:05")!!.time,
          durationSeconds = 600L,
          title = "Outgoing song",
          artist = "Artist",
          genre = "Jazz",
          dailyDurations = "{\"2026-09-30\":300,\"2026-10-01\":300}",
          isOpen = false
        )
      )
      dao.insertSession(
        PlaybackSessionEntity(
          date = "2026-09-10",
          year = 2026,
          month = 9,
          startTime = formatter.parse("2026-09-10 12:00")!!.time,
          endTime = formatter.parse("2026-09-10 12:10")!!.time,
          durationSeconds = 600L,
          title = "Inside song",
          artist = "Artist",
          genre = "Rock",
          isOpen = false
        )
      )

      val crossings = dao.getSessionsCrossingRange(rangeStart, rangeEnd).first()
      val genres = dao.getGenreAggregates("2026-09-01", "2026-09-30").first()
      val incomingTrack = dao.getTrackAggregateForGenre(
        "2026-09-01", "2026-09-30", "Rock", "Incoming song", "Artist"
      )
      val outgoingTrack = dao.getTrackAggregateForGenre(
        "2026-09-01", "2026-09-30", "Jazz", "Outgoing song", "Artist"
      )

      assertEquals(setOf(incomingId, outgoingId), crossings.map { it.id }.toSet())
      assertEquals(600L, genres.single { it.genreName == "Jazz" }.totalSeconds)
      assertEquals(null, incomingTrack)
      assertEquals(600L, outgoingTrack?.totalSeconds)
    } finally {
      db.close()
    }
  }

  @Test
  fun `genre track aggregation treats blank and missing artists consistently`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.musicTrackerDao()
      dao.insertSession(
        PlaybackSessionEntity(
          date = "2026-09-10",
          year = 2026,
          month = 9,
          startTime = 0L,
          durationSeconds = 10L,
          title = "Same track",
          artist = null,
          genre = "Pop",
          isOpen = false
        )
      )
      dao.insertSession(
        PlaybackSessionEntity(
          date = "2026-09-11",
          year = 2026,
          month = 9,
          startTime = 0L,
          durationSeconds = 15L,
          title = "Same track",
          artist = "   ",
          genre = "Pop",
          isOpen = false
        )
      )

      val genres = dao.getGenreAggregates("2026-09-01", "2026-09-30").first()
      val topTracks = dao.getTopTracksForGenre("2026-09-01", "2026-09-30", "Pop", 100).first()
      val byMissingArtist = dao.getTrackAggregateForGenre(
        "2026-09-01", "2026-09-30", "Pop", "Same track", null
      )

      assertEquals(1, genres.single().trackCount)
      assertEquals(1, topTracks.size)
      assertEquals("Unknown Artist", topTracks.single().artist)
      assertEquals(25L, byMissingArtist?.totalSeconds)
    } finally {
      db.close()
    }
  }

  @Test
  fun `looping a track keeps a single database session`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.AppDatabase.getInstance(context)
    val repo = com.example.data.MusicTrackerRepository(db.musicTrackerDao())
    val engine = com.example.tracker.MusicTrackerEngine.getInstance(context, repo)

    engine.onPlaybackStarted(
      title = "Blinding Lights",
      artist = "The Weeknd",
      album = "After Hours",
      pkg = "com.google.android.apps.youtube.music",
      isYt = true
    )
    shadowOf(Looper.getMainLooper()).idle()
    awaitUntil { engine.uiState.value.trackTitle == "Blinding Lights" && engine.getCurrentDbSessionId() != null }

    val sid = engine.getCurrentDbSessionId()

    repeat(5) { engine.simulateTrackLoop() }
    shadowOf(Looper.getMainLooper()).idle()
    // Give async DB writes time to settle; a reintroduced "loop = new session"
    // bug would show up here as extra rows for the track
    Thread.sleep(1000)

    val trackSessions = runBlocking {
      db.musicTrackerDao().getAllSessions().first().filter { it.title == "Blinding Lights" }
    }
    assertEquals(1, trackSessions.size)
    assertEquals(sid, engine.getCurrentDbSessionId())
    // 5 absorbed loops must surface as extra plays on the session row so the
    // UI can render the repeat label
    assertEquals(6, trackSessions.first().playCount)
  }

  private fun awaitUntil(timeoutMs: Long = 5000L, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50)
    }
  }
}
