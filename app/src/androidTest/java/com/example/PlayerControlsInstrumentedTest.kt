package com.example

import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ResolvedGenreEntity
import com.example.tracker.RepeatMode
import com.example.ui.UniqueTrackItem
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerControlsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun seekAndRepeatReachTheConnectedSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = compose.activity.application as YTTrackerApplication
        val engine = app.trackerEngine
        val onlyYouTubeMusic = engine.uiState.value.filterOnlyYouTubeMusic
        val session = MediaSessionCompat(compose.activity, "Player controls")
        var requestedPosition = -1L
        var customRepeatRequested = false
        fun publishPosition(position: Long) {
            session.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PAUSED, position, 0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_SET_REPEAT_MODE)
                .build())
        }
        instrumentation.runOnMainSync {
            engine.setFilterOnlyYouTubeMusic(false)
            session.setCallback(object : MediaSessionCompat.Callback() {
                override fun onSeekTo(pos: Long) {
                    requestedPosition = pos
                    publishPosition(pos)
                }
                override fun onSetRepeatMode(repeatMode: Int) = session.setRepeatMode(repeatMode)
                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                    customRepeatRequested = action == com.example.tracker.PlaybackControls.YOUTUBE_MUSIC_REPEAT_ACTION
                    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
                }
            }, Handler(Looper.getMainLooper()))
            session.setMetadata(MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Starboy")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "The Weeknd")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 240_000L)
                .build())
            session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
            publishPosition(60_000L)
            session.isActive = true
            val controller = MediaController(compose.activity, session.sessionToken.token as android.media.session.MediaSession.Token)
            engine.javaClass.getDeclaredMethod("switchActiveController", MediaController::class.java).apply {
                isAccessible = true
            }.invoke(engine, controller)
        }
        try {
            compose.waitUntil(10_000) { app.trackerEngine.uiState.value.trackTitle == "Starboy" }
            val slider = compose.onNodeWithContentDescription("Playback position")
            slider.performTouchInput { click(center.copy(x = width * 0.7f)) }
            compose.waitUntil(5_000) { requestedPosition in 150_000L..190_000L }
            slider.performTouchInput { swipe(center.copy(x = width * 0.7f), center.copy(x = width * 0.25f)) }
            compose.waitUntil(5_000) { requestedPosition in 45_000L..75_000L }
            compose.onNodeWithContentDescription("Repeat off. Change repeat mode").performClick()
            compose.waitUntil(5_000) { app.trackerEngine.uiState.value.repeatMode == RepeatMode.ALL }
            compose.onNodeWithContentDescription("Repeat all. Change repeat mode").performClick()
            compose.waitUntil(5_000) { app.trackerEngine.uiState.value.repeatMode == RepeatMode.ONE }
            captureEvidence("player")
            instrumentation.runOnMainSync { session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE) }
            compose.waitUntil(5_000) { app.trackerEngine.uiState.value.repeatMode == RepeatMode.OFF }
            instrumentation.runOnMainSync {
                session.setPlaybackState(PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PAUSED, requestedPosition, 0f)
                    .addCustomAction(com.example.tracker.PlaybackControls.YOUTUBE_MUSIC_REPEAT_ACTION,
                        "Repeat off", android.R.drawable.ic_media_play)
                    .build())
            }
            compose.waitUntil(5_000) { !engine.uiState.value.playbackControls.canSeek }
            compose.onNodeWithContentDescription("Repeat off. Change repeat mode").performClick()
            compose.waitUntil(5_000) { customRepeatRequested && engine.uiState.value.repeatMode == RepeatMode.ONE }
        } finally {
            instrumentation.runOnMainSync {
                session.release()
                engine.setFilterOnlyYouTubeMusic(onlyYouTubeMusic)
            }
        }
    }

    @Test fun genrePickerReusesSavedGenresAndAllowsNewOnes() = runBlocking {
        val app = compose.activity.application as YTTrackerApplication
        app.database.musicTrackerDao().putResolvedGenre(ResolvedGenreEntity("picker-test", "Jazz", 1.0, "manual", 1L))
        val genres = app.repository.getExistingGenres().first()
        val track = UniqueTrackItem("Starboy", "The Weeknd", null, "Pop", null, 60L, 1, 1L)
        var selected: String? = null
        compose.runOnUiThread {
            compose.activity.setContentForGenreTest(track, genres) { selected = it }
        }
        compose.onNodeWithText("Edit this song's genre").performClick()
        compose.onNodeWithText("Choose an existing genre").performClick()
        compose.onNodeWithText("Jazz").performClick()
        captureEvidence("genre-picker")
        compose.onNodeWithText("Save genre").performClick()
        compose.runOnIdle { assertEquals("Jazz", selected) }
        compose.onNodeWithText("Genre for this song").performTextReplacement("Night drive")
        compose.onNodeWithText("Save genre").performClick()
        compose.runOnIdle { assertEquals("Night drive", selected) }
    }

    private fun captureEvidence(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureEvidence") != "true") return
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val node = if (name == "genre-picker") {
            compose.onNodeWithText("Genre for this song").onParent()
        } else compose.onRoot()
        val screenshot = node.captureToImage().asAndroidBitmap()
        java.io.File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

}

private fun MainActivity.setContentForGenreTest(track: UniqueTrackItem, genres: List<String>, onSave: (String) -> Unit) {
    setContent {
        MyApplicationTheme {
            TrackDetailsDialog(track, {}, { _, genre -> onSave(genre) }, genres)
        }
    }
}
