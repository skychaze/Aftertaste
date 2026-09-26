package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.tracker.TrackerUiState
import com.example.ui.AnalyticsUiState
import com.example.ui.DayChartItem
import com.example.ui.HistoryRange
import com.example.ui.UniqueTrackItem
import com.example.ui.components.WeeklyAnalyticsView
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
class WeeklyAnalyticsViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `range selection and day details follow the listening chart`() {
        val selectedRange = mutableStateOf(HistoryRange.SEVEN_DAYS)
        val selectedDate = mutableStateOf<String?>(null)
        val track = UniqueTrackItem(
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            genre = "Pop",
            totalSeconds = 245L,
            playCount = 1,
            lastPlayedTimestamp = 1L
        )
        val days = listOf(
            DayChartItem("2026-09-06", "Sun", 6, 245L, 4, false, listOf(track)),
            DayChartItem("2026-09-07", "Mon", 7, 125L, 2, false)
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WeeklyAnalyticsView(
                    state = AnalyticsUiState(
                        trackerState = TrackerUiState(),
                        past7Days = days,
                        weekAverageMinutes = 3,
                        historyRange = selectedRange.value,
                        selectedHistoryDate = selectedDate.value,
                        selectedDayTracks = if (selectedDate.value == days.first().dateStr) listOf(track) else emptyList()
                    ),
                    onRangeSelected = { selectedRange.value = it },
                    onDaySelected = { selectedDate.value = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("history_range_7_days").assertIsSelected()
        composeTestRule.onNodeWithTag("history_range_30_days").performClick()
        composeTestRule.onNodeWithTag("history_range_30_days").assertIsSelected()
        composeTestRule.onNodeWithTag("history_range_7_days").assertIsNotSelected()
        composeTestRule.runOnIdle { assertEquals(HistoryRange.THIRTY_DAYS, selectedRange.value) }

        composeTestRule.onNodeWithTag("history_day_2026-09-06").performClick()
        composeTestRule.onNodeWithText("Starboy").assertExists()

        val chartTop = composeTestRule.onNodeWithTag("history_daily_chart_card")
            .fetchSemanticsNode().boundsInRoot.top
        val detailsTop = composeTestRule.onNodeWithTag("last_seven_day_record_tracks_card")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Day details should appear below the daily chart", detailsTop > chartTop)
    }

    @Test
    fun `positive sub-minute average is not shown as zero`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                WeeklyAnalyticsView(
                    state = AnalyticsUiState(
                        trackerState = TrackerUiState(),
                        past7Days = listOf(DayChartItem("2026-09-26", "Sat", 26, 30L, 0, true))
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("<1m daily average · 1 day").assertExists()
    }
}
