package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.example.tracker.PlaybackControls
import com.example.tracker.TrackerUiState
import com.example.ui.components.NowPlayingCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PlayerSeekingTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `changing track cancels an active seek gesture and allows fresh seeks`() {
        assertSeekCancellation { it.copy(trackTitle = "Song B") }
    }

    @Test
    fun `changing source cancels an active seek gesture and allows fresh seeks`() {
        assertSeekCancellation { it.copy(sourcePackage = "another.player") }
    }

    private fun assertSeekCancellation(changeTrack: (TrackerUiState) -> TrackerUiState) {
        val state = mutableStateOf(
            TrackerUiState(
                artist = "Artist",
                trackTitle = "Song A",
                trackDurationMs = 240_000L,
                playbackControls = PlaybackControls(canSeek = true),
            )
        )
        val seeks = mutableListOf<Long>()
        composeTestRule.setContent {
            MyApplicationTheme {
                NowPlayingCard(state.value, {}, onSeek = { seeks.add(it) })
            }
        }
        val slider = composeTestRule.onNodeWithContentDescription("Playback position")
        slider.performTouchInput {
            down(Offset(width * 0.25f, centerY))
            moveTo(Offset(width * 0.5f, centerY))
        }
        composeTestRule.runOnIdle { state.value = changeTrack(state.value) }
        slider.performTouchInput {
            moveTo(Offset(width * 0.75f, centerY))
            up()
        }
        composeTestRule.runOnIdle { assertEquals(emptyList<Long>(), seeks) }
        slider.performTouchInput {
            down(Offset(width * 0.25f, centerY))
            up()
        }
        composeTestRule.runOnIdle {
            assertEquals(1, seeks.size)
            assertTrue(seeks.single() in 40_000L..80_000L)
        }
        slider.performTouchInput {
            down(Offset(width * 0.25f, centerY))
            moveTo(Offset(width * 0.75f, centerY))
            up()
        }
        composeTestRule.runOnIdle {
            assertEquals(2, seeks.size)
            assertTrue(seeks.last() in 160_000L..200_000L)
        }
    }
}
