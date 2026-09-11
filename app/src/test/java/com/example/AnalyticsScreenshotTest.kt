package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.tracker.TrackerUiState
import com.example.ui.AnalyticsUiState
import com.example.ui.DayChartItem
import com.example.ui.UniqueTrackItem
import com.example.ui.components.WeeklyAnalyticsView
import com.example.ui.components.YearlyAnalyticsView
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AnalyticsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lastSevenDayRecordScreenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                WeeklyAnalyticsView(state = sampleState())
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/last-seven-day-record.png")
    }

    @Test
    fun yearlySummaryScreenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                YearlyAnalyticsView(
                    state = sampleState().copy(
                        yearTotalSeconds = 18_540L,
                        yearActiveDays = 6,
                        yearAverageMinutesPerDay = 51,
                        peakMonthName = "September",
                        peakMonthHours = 5.1f,
                        currentStreakDays = 4
                    ),
                    onSelectYear = {},
                    onSeedData = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/yearly-summary.png")
    }

    private fun sampleState(): AnalyticsUiState {
        val track = UniqueTrackItem(
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            genre = "Pop",
            totalSeconds = 245L,
            playCount = 1,
            lastPlayedTimestamp = 1L
        )
        return AnalyticsUiState(
            trackerState = TrackerUiState(),
            past7Days = listOf(
                DayChartItem("2026-09-06", "Sun", 6, 245L, 4, false, listOf(track)),
                DayChartItem("2026-09-07", "Mon", 7, 125L, 2, false),
                DayChartItem("2026-09-08", "Tue", 8, 60L, 1, false),
                DayChartItem("2026-09-09", "Wed", 9, 0L, 0, false),
                DayChartItem("2026-09-10", "Thu", 10, 360L, 6, false),
                DayChartItem("2026-09-11", "Fri", 11, 90L, 1, false),
                DayChartItem("2026-09-12", "Sat", 12, 540L, 9, true)
            ),
            availableYears = listOf(2026),
            selectedYear = 2026
        )
    }
}
