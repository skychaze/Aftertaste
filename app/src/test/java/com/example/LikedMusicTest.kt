package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LikedTrackEntity
import com.example.data.MusicTrackerRepository
import com.example.tracker.TrackerUiState
import com.example.ui.GenreAnalyticsData
import com.example.ui.components.GenrePieChartCard
import com.example.ui.components.NowPlayingCard
import com.example.ui.theme.JournalBackground
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel8)
class LikedMusicTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `likes deduplicate persist without history and can be removed`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "liked-music-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var database = open()
        try {
            var repository = MusicTrackerRepository(database.musicTrackerDao(), database)
            repository.setTrackLiked(" Song ", "Artist", null, true)
            repository.setTrackLiked("song", "ARTIST", null, true)
            repository.setTrackLiked("Song", "Other artist", null, true)
            assertEquals(2, repository.getLikedTracks().first().size)
            assertTrue(database.musicTrackerDao().getAllSessionsSync().isEmpty())
            database.close()
            database = open()
            repository = MusicTrackerRepository(database.musicTrackerDao(), database)
            assertEquals(2, repository.getLikedTracks().first().size)
            repository.setTrackLiked("SONG", "artist", null, false)
            assertEquals("Other artist", repository.getLikedTracks().first().single().artist)
            repository.clearAll()
            assertTrue(repository.getLikedTracks().first().isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `heart toggles for paused songs and stays hidden without a track`() {
        val liked = mutableStateOf(false)
        val tracker = mutableStateOf(TrackerUiState(trackTitle = "Song", artist = "Artist"))
        compose.setContent {
            MyApplicationTheme {
                NowPlayingCard(
                    tracker.value,
                    {},
                    isLiked = liked.value,
                    onLikeChanged = { liked.value = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Like song").performClick()
        compose.onNodeWithContentDescription("Unlike song").performClick()
        compose.runOnIdle {
            assertFalse(liked.value)
            tracker.value = TrackerUiState()
        }
        compose.onNodeWithContentDescription("Like song").assertDoesNotExist()
    }

    @Test
    fun `saved songs remain available in empty taste periods and open a music search`() {
        val tracks =
            mutableStateOf(
                listOf(LikedTrackEntity("artist\u001fsong", "Song & more", "Artist", null, 1L))
            )
        compose.setContent {
            MyApplicationTheme {
                Column(
                    Modifier.fillMaxSize()
                        .background(JournalBackground)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    GenrePieChartCard(
                        genreData = GenreAnalyticsData(),
                        onScopeSelected = {},
                        likedTracks = tracks.value,
                        onUnlikeTrack = { track ->
                            tracks.value = tracks.value.filter { it.trackKey != track.trackKey }
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("Songs").performClick()
        compose.onNodeWithText("Liked music").performScrollTo().assertExists()
        compose.onNodeWithText("Song & more").performScrollTo()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/liked-music.png")
        compose.onNodeWithText("Song & more").performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("music.youtube.com", intent.data?.host)
        assertEquals("Song & more Artist", intent.data?.getQueryParameter("q"))
        compose.onNodeWithContentDescription("Unlike Song & more").performClick()
        compose.onNodeWithText("Song & more").assertDoesNotExist()
    }
}
