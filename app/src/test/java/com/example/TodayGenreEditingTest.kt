package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.tracker.ArtworkResolver
import com.example.ui.AnalyticsUiState
import com.example.ui.TodayTrackFeedItem
import com.example.ui.UniqueTrackItem
import com.example.ui.components.DailyListeningView
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w320dp-h800dp-mdpi")
class TodayGenreEditingTest {
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())
    private val track = TodayTrackFeedItem(
        id = 1L, title = "Blinding Lights", artist = "The Weeknd", album = null, genre = "Pop",
        durationSeconds = 404L, playCount = 2,
        // Keep the displayed date fixed across developer and CI time zones.
        timestamp = LocalDateTime.of(2026, 10, 8, 14, 48)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    @Before
    fun cacheTransparentArtwork() {
        // Keep the placeholder visible and avoid live artwork requests in screenshots.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = ArtworkResolver.getCacheKey(track.artist, track.title)
        val file = File(context.filesDir, "artworks/$key.jpg")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun showToday(onSaved: (UniqueTrackItem, String) -> Unit) {
        val genre = mutableStateOf(track.genre)
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.fillMaxSize().background(BentoBackground).verticalScroll(rememberScrollState())) {
                    DailyListeningView(
                        state = AnalyticsUiState(
                            todayTrackFeed = listOf(track.copy(genre = genre.value)),
                            existingGenres = listOf("Pop", "Rock"),
                        ),
                        onSetDailyGoal = {}, onOpenYtMusic = {}, dateLabel = "Thursday, 8 October",
                        onEditTrackGenre = { selected, edited ->
                            onSaved(selected, edited)
                            genre.value = edited
                        },
                    )
                }
            }
        }
    }

    private fun openSong() {
        compose.onNodeWithText(track.title).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1_000L)
        expandSheet()
    }

    private fun expandSheet() {
        compose.onNode(isRoot() and hasAnyDescendant(hasText("Close"))).performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1_000L)
    }

    @Test
    fun `Today song details can save an existing genre and a new genre`() {
        val saved = mutableListOf<Pair<UniqueTrackItem, String>>()
        showToday { selected, genre -> saved += selected to genre }
        openSong()
        compose.onNodeWithText("Edit this song's genre").assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/today-genre-details.png")
        compose.onNodeWithText("Edit this song's genre").performClick()
        compose.mainClock.advanceTimeBy(1_000L)
        expandSheet()
        compose.onNodeWithText("Genre for this song").performScrollTo().assertTextContains("Pop").assertIsDisplayed()
        compose.onNodeWithText("Save genre").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/today-genre-editor.png")
        compose.onNodeWithText("Choose an existing genre").performScrollTo().performClick()
        compose.onNodeWithText("Rock").performClick()
        compose.onNodeWithText("Genre for this song").assertTextContains("Rock")
        compose.onNodeWithText("Save genre").performScrollTo().performClick()
        compose.onNodeWithText("Save genre").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, saved.size)
            assertEquals(track.title, saved.single().first.title)
            assertEquals(track.artist, saved.single().first.artist)
            assertEquals("Rock", saved.single().second)
        }

        openSong()
        compose.onNodeWithText("Rock").assertExists()
        compose.onNodeWithText("Edit this song's genre").performClick()
        compose.onNodeWithText("Genre for this song").performScrollTo().performTextReplacement("  Synthwave  ")
        compose.onNodeWithText("Save genre").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("Rock", "Synthwave"), saved.map { it.second }) }
        openSong()
        compose.onNodeWithText("Synthwave").assertExists()
    }

    @Test
    fun `blank genres cannot be saved and closing the editor leaves the genre unchanged`() {
        var saveCount = 0
        showToday { _, _ -> saveCount++ }
        openSong()
        compose.onNodeWithText("Edit this song's genre").performClick()
        compose.onNodeWithText("Genre for this song").performScrollTo().performTextReplacement("   ")
        compose.onNodeWithText("Save genre").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Genre for this song").performScrollTo().performTextReplacement("Jazz")
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Save genre").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, saveCount) }
        openSong()
        compose.onNodeWithText("Pop").assertExists()
    }
}
