package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.AnalyticsUiState
import com.example.ui.MonthChartItem
import com.example.ui.components.YearlyAnalyticsView
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class YearlyAnalyticsViewTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `selecting a year calls the selection action`() {
        var selectedYear = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                YearlyAnalyticsView(
                    state =
                        AnalyticsUiState(
                            selectedYear = 2026,
                            availableYears = listOf(2026, 2025),
                        ),
                    onSelectYear = { selectedYear = it },
                    onSeedData = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("year_picker").performClick()
        composeTestRule.onNodeWithTag("select_year_2025").performClick()
        composeTestRule.runOnIdle { assertEquals(2025, selectedYear) }
    }

    @Test
    fun `month detail follows the data when history finishes loading`() {
        val state = mutableStateOf(AnalyticsUiState(selectedYear = 2026))
        composeTestRule.setContent {
            MyApplicationTheme {
                YearlyAnalyticsView(state.value, {}, {})
            }
        }
        composeTestRule.runOnIdle {
            state.value =
                state.value.copy(
                    yearTotalSeconds = 18_360L,
                    monthlyBreakdown =
                        listOf(
                            MonthChartItem(1, "Jan", 0L, 0f, 0, false),
                            MonthChartItem(3, "Mar", 18_360L, 5.1f, 6, false),
                        ),
                )
        }
        composeTestRule.onNodeWithText("Mar 2026").assertExists()
    }

    @Test
    fun `month picker defaults to the peak month and follows the chosen month`() {
        val months =
            listOf(
                MonthChartItem(1, "Jan", 3_600L, 1f, 2, false),
                MonthChartItem(3, "Mar", 18_360L, 5.1f, 6, false),
                MonthChartItem(9, "Sep", 0L, 0f, 0, true),
            )
        composeTestRule.setContent {
            MyApplicationTheme {
                YearlyAnalyticsView(
                    state =
                        AnalyticsUiState(
                            selectedYear = 2026,
                            availableYears = listOf(2026),
                            yearTotalSeconds = 21_960L,
                            monthlyBreakdown = months,
                        ),
                    onSelectYear = {},
                    onSeedData = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Mar").assertExists()
        composeTestRule.onNodeWithText("5h 6m").assertExists()

        composeTestRule.onNodeWithTag("month_picker_field").performClick()
        composeTestRule.onNodeWithTag("select_month_9").performClick()

        composeTestRule.onNodeWithText("No listening recorded in Sep.").assertExists()
        composeTestRule.onNodeWithText("5h 6m").assertDoesNotExist()
    }
}
