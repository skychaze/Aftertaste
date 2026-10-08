package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.LikedTrackEntity
import com.example.tracker.GenreTags
import com.example.tracker.TrackerUiState
import com.example.ui.*
import com.example.ui.components.*
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w320dp-h800dp-mdpi")
class SongLikeActionsTest {
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())
    private val track = UniqueTrackItem(
        "Song & more", "Artist + guest", null, "Pop", null, 141L, 2,
        // Keep the displayed date fixed across developer and CI time zones.
        LocalDateTime.of(2026, 10, 8, 14, 48).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    @Test
    fun `long row with missing artwork keeps metadata and separate touch actions`() {
        val title = "777Hz - Harmony of Angels (Ambient Harp Version)"
        val liked = mutableStateOf(false)
        var opened = 0
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.width(320.dp).background(BentoBackground).padding(16.dp)) {
                    SlimTrackRow(title, "Meditative Mind", "2m 21s", "2 plays",
                        onClick = { opened++ }, isLiked = liked.value,
                        onLikeChanged = { liked.value = it })
                }
            }
        }
        compose.onNodeWithText("2m 21s").assertIsDisplayed()
        compose.onNodeWithText("2 plays").assertIsDisplayed()
        val heart = compose.onNodeWithContentDescription("Like $title")
        heart.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        heart.performTouchInput { click() }
        compose.runOnIdle { assertTrue(liked.value); assertEquals(0, opened) }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/song-row-like.png")
        compose.onNodeWithContentDescription("Unlike $title").assertIsOn().performClick()
        compose.onNodeWithText(title).performTouchInput { click() }
        compose.runOnIdle { assertFalse(liked.value); assertEquals(1, opened) }
    }

    @Test
    fun `likes stay synced through Today History genre rankings drilldowns and liked music`() {
        val destination = mutableStateOf(0)
        val likedTracks = mutableStateOf(emptyList<LikedTrackEntity>())
        val selected = mutableStateOf<UniqueTrackItem?>(null)
        fun like(title: String, artist: String, art: String?, liked: Boolean) {
            val key = requireNotNull(GenreTags.trackKey(artist, title))
            likedTracks.value = if (liked) listOf(LikedTrackEntity(key, title, artist, art, 1L)) else emptyList()
        }
        compose.setContent {
            MyApplicationTheme {
                val state = AnalyticsUiState(
                    likedTracks = likedTracks.value,
                    trackerState = TrackerUiState(trackTitle = track.title, artist = track.artist),
                    todayTrackFeed = listOf(TodayTrackFeedItem(1L, track.title, track.artist, null, "Pop",
                        durationSeconds = 141L, timestamp = 1L)),
                    historyTracks = listOf(track.copy(title = " song & MORE ", artist = "ARTIST + GUEST")),
                    historyQuery = "song", selectedHistoryDate = "2026-10-08", selectedDayTracks = listOf(track),
                )
                if (destination.value == 1) {
                    LazyColumn {
                        historyTrackItems(state, { selected.value = it }, {},
                            onTrackLiked = { item, liked -> like(item.title, item.artist, item.artworkUrl, liked) })
                    }
                    selected.value?.let { item ->
                        TrackDetailsDialog(item, { selected.value = null },
                            isLiked = likedTracks.value.isNotEmpty(),
                            onLikeChanged = { like(item.title, item.artist, item.artworkUrl, it) })
                    }
                } else Column(Modifier.fillMaxSize().testTag("song_action_test_scroll").verticalScroll(rememberScrollState())) {
                    when (destination.value) {
                        0 -> DailyListeningView(state, onSetDailyGoal = {}, onOpenYtMusic = {}, onTrackLiked = ::like)
                        2 -> GenrePieChartCard(GenreAnalyticsData(), onScopeSelected = {}, topTracks = listOf(track),
                            likedTracks = likedTracks.value,
                            onTrackLiked = { item, liked -> like(item.title, item.artist, item.artworkUrl, liked) },
                            onUnlikeTrack = { like(it.title, it.artist, it.artworkUrl, false) })
                        3 -> GenrePieChartCard(
                            GenreAnalyticsData(genres = listOf(GenreSliceData("Pop", 141L, 2, 100f, 1,
                                androidx.compose.ui.graphics.Color.Blue)), totalSeconds = 141L),
                            selectedGenre = "Pop", selectedGenreTracks = listOf(track), onScopeSelected = {},
                            likedTracks = likedTracks.value,
                            onTrackLiked = { item, liked -> like(item.title, item.artist, item.artworkUrl, liked) })
                        4 -> WeeklyAnalyticsView(state,
                            onTrackLiked = { item, liked -> like(item.title, item.artist, item.artworkUrl, liked) })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Like ${track.title}").performScrollTo().performTouchInput { click() }
        compose.onNodeWithContentDescription("Open in YouTube Music").assertDoesNotExist()
        compose.onNodeWithContentDescription("Unlike song").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Unlike ${track.title}").performScrollTo().assertIsOn()
        compose.onAllNodesWithText(track.title).onLast().performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithContentDescription("Open in YouTube Music").assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.runOnIdle { destination.value = 1 }
        compose.onAllNodesWithContentDescription("Unlike  song & MORE ").onLast().assertIsOn().performTouchInput { click() }
        compose.onNodeWithContentDescription("Open in YouTube Music").assertDoesNotExist()
        compose.onNodeWithText(" song & MORE ").performClick()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onAllNodesWithContentDescription("Like  song & MORE ").onLast().performClick()
        compose.onAllNodesWithContentDescription("Unlike  song & MORE ").onLast().assertIsOn()
        compose.onAllNodesWithText("Close").onLast().performClick()
        compose.runOnIdle { destination.value = 2 }
        compose.onNodeWithText("Songs").performClick()
        compose.onNodeWithText("Liked music").performScrollTo().assertExists()
        compose.onAllNodesWithContentDescription("Unlike  song & MORE ").onLast().performScrollTo().performClick()
        compose.onNodeWithContentDescription("Like ${track.title}").performScrollTo().performClick()
        compose.runOnIdle { destination.value = 3 }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("song_action_test_scroll").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithTag("genre_unique_tracks_card").assertIsDisplayed()
        compose.onNodeWithContentDescription("Unlike ${track.title}").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithContentDescription("Open in YouTube Music").assertDoesNotExist()
        compose.onAllNodesWithText(track.title).onLast().performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onAllNodesWithContentDescription("Like ${track.title}").onLast().performClick()
        compose.onAllNodesWithText("Close").onLast().performClick()
        compose.runOnIdle { destination.value = 4 }
        compose.onNodeWithContentDescription("Unlike ${track.title}").performScrollTo().assertIsOn()
        compose.runOnIdle { destination.value = 0 }
        compose.onNodeWithContentDescription("Unlike song").assertExists()
    }

    @Test
    fun `popup header actions work with long title missing artwork and genre editing`() {
        val item = track.copy(title = "777Hz - Harmony of Angels (Ambient Harp Version)")
        val liked = mutableStateOf(false)
        var saved = ""
        var dismissed = false
        compose.setContent {
            MyApplicationTheme {
                TrackDetailsDialog(item, { dismissed = true }, { _, genre -> saved = genre }, listOf("Rock"),
                    isLiked = liked.value, onLikeChanged = { liked.value = it })
            }
        }
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithText(item.title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithText("Listening time in this view").assertExists()
        compose.onNodeWithText("Last played", substring = true).assertExists()
        val music = compose.onNodeWithContentDescription("Open in YouTube Music")
        music.performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Open in YouTube Music").assertDoesNotExist()
        music.performClick()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals("${item.title} ${item.artist}", intent.data?.getQueryParameter("q"))
        assertEquals("com.google.android.apps.youtube.music", intent.`package`)
        compose.onNodeWithContentDescription("Like ${item.title}").performClick()
        compose.onNodeWithContentDescription("Unlike ${item.title}").assertIsOn()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/song-details-actions.png")
        compose.onNodeWithText("Edit this song's genre").performScrollTo().performClick()
        compose.onNodeWithText("Genre for this song").performScrollTo().performTextReplacement("Rock")
        compose.onNodeWithText("Save genre").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Rock", saved); assertTrue(dismissed); assertTrue(liked.value) }
    }
}
