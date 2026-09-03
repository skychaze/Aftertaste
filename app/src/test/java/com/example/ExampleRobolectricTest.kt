package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.tracker.YouTubeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
