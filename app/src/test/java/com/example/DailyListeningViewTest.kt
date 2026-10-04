package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.tracker.TrackerUiState
import com.example.ui.AnalyticsUiState
import com.example.ui.components.DailyListeningView
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DailyListeningViewTest {
    @get:Rule
    // Artwork loading returns from Dispatchers.IO. An unconfined test dispatcher can
    // resume Compose's apply notifications on that worker while goal controls change.
    // Queue resumptions on the test scheduler, which the Compose rule advances on UI.
    val composeTestRule = createComposeRule(effectContext = StandardTestDispatcher())

    @Test
    fun `goal editing is collapsed until requested and the launch action opens music`() {
        var chosenGoal = 0
        var openedMusic = false
        composeTestRule.setContent {
            MyApplicationTheme {
                DailyListeningView(
                    state = AnalyticsUiState(
                        trackerState = TrackerUiState(todayTotalSeconds = 2_538L, dailyGoalMinutes = 60)
                    ),
                    onSetDailyGoal = { chosenGoal = it },
                    onOpenYtMusic = { openedMusic = true }
                )
            }
        }

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithText("42m 18s").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("42m of 60m").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithText("90m").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithText("Edit goal").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("90m").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithContentDescription("Open YouTube Music").performClick()

        composeTestRule.runOnIdle {
            assertEquals(90, chosenGoal)
            assertTrue(openedMusic)
        }
    }

    @Test
    fun `goal countdown stays positive until the goal is reached`() {
        val seconds = mutableStateOf(3_541L)
        composeTestRule.setContent {
            MyApplicationTheme {
                DailyListeningView(
                    state = AnalyticsUiState(
                        trackerState = TrackerUiState(todayTotalSeconds = seconds.value, dailyGoalMinutes = 60)
                    ),
                    onSetDailyGoal = {},
                    onOpenYtMusic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("1 minute to your daily goal").assertExists()
        composeTestRule.runOnIdle { seconds.value = 3_600L }
        // A queued dispatcher needs a frame to apply the state change before asserting.
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("Daily goal reached").assertExists()
    }
}
