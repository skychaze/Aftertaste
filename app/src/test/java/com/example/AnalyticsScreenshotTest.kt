package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.Color
import com.example.tracker.TrackerUiState
import com.example.ui.AnalyticsUiState
import com.example.ui.DayChartItem
import com.example.ui.GenreAnalyticsData
import com.example.ui.GenreScope
import com.example.ui.GenreSliceData
import com.example.ui.Milestone
import com.example.ui.TodayTrackFeedItem
import com.example.ui.UniqueTrackItem
import com.example.ui.components.DailyListeningView
import com.example.ui.components.GenrePieChartCard
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
    fun dailyListeningScreenshot() {
        val state = sampleState().copy(
            trackerState = TrackerUiState(
                isActivelyPlaying = true,
                trackTitle = "Blinding Lights",
                artist = "The Weeknd",
                currentGenre = "Pop",
                currentSessionSeconds = 204L,
                trackPositionMs = 42_000L,
                trackDurationMs = 200_000L,
                todayTotalSeconds = 2_538L,
                todaySessionCount = 3,
                dailyGoalMinutes = 60
            ),
            todayTrackFeed = listOf(
                TodayTrackFeedItem(
                    id = 1L,
                    title = "Blinding Lights",
                    artist = "The Weeknd",
                    album = null,
                    genre = "Pop",
                    durationSeconds = 404L,
                    timestamp = 1L,
                    playCount = 2
                ),
                TodayTrackFeedItem(
                    id = 2L,
                    title = "Kasoor",
                    artist = "Prateek Kuhad",
                    album = null,
                    genre = "Indie",
                    durationSeconds = 197L,
                    timestamp = 2L
                )
            )
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                DailyListeningView(state = state, onSetDailyGoal = {}, onOpenYtMusic = {})
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/daily-listening.png")
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
                        currentStreakDays = 4,
                        milestones = listOf(
                            Milestone("Bronze listener", 5, "First 5 hours listened", true, 1f),
                            Milestone("Silver beat", 25, "25 hours listened", false, 0.2f),
                            Milestone("Gold listener", 50, "50 hours listened", false, 0.1f)
                        )
                    ),
                    onSelectYear = {},
                    onSeedData = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/yearly-summary.png")
    }

    @Test
    fun genreSummaryScreenshot() {
        val genres = listOf(
            GenreSliceData("Pop", 720L, 12, 50f, 2, Color(0xFF6750A4)),
            GenreSliceData("Rock", 432L, 7, 30f, 1, Color(0xFF0061A4)),
            GenreSliceData("Indie", 288L, 4, 20f, 1, Color(0xFF008577))
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                GenrePieChartCard(
                    genreData = GenreAnalyticsData(
                        scope = GenreScope.MONTH,
                        genres = genres,
                        totalSeconds = genres.sumOf { it.totalSeconds },
                        totalTracksTracked = 4
                    ),
                    onScopeSelected = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/genres-summary.png")
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
            weekAverageMinutes = 3,
            availableYears = listOf(2026),
            selectedYear = 2026
        )
    }
}
