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
            composeTestRule.onAllNodesWithText("42 min 18 sec").fetchSemanticsNodes().isNotEmpty()
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
    fun `today duration refreshes across minute hour and day boundaries`() {
        val seconds = mutableStateOf(0L)
        composeTestRule.setContent {
            MyApplicationTheme {
                DailyListeningView(
                    state = AnalyticsUiState(
                        trackerState = TrackerUiState(todayTotalSeconds = seconds.value)
                    ),
                    onSetDailyGoal = {},
                    onOpenYtMusic = {}
                )
            }
        }

        listOf(
            0L to "0 sec",
            45L to "45 sec",
            59L to "59 sec",
            60L to "1 min 00 sec",
            65L to "1 min 05 sec",
            125L to "2 min 05 sec",
            3_599L to "59 min 59 sec",
            3_600L to "1 hr 00 min 00 sec",
            3_725L to "1 hr 02 min 05 sec",
            86_399L to "23 hr 59 min 59 sec",
            86_400L to "24 hr 00 min 00 sec",
            90_065L to "25 hr 01 min 05 sec",
        ).forEach { (value, expected) ->
            composeTestRule.runOnIdle { seconds.value = value }
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText(expected).assertExists()
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
