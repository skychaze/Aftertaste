package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.AnalyticsUiState
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
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `selecting a year calls the selection action`() {
        var selectedYear = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                YearlyAnalyticsView(
                    state = AnalyticsUiState(
                        selectedYear = 2026,
                        availableYears = listOf(2026, 2025)
                    ),
                    onSelectYear = { selectedYear = it },
                    onSeedData = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("select_year_2025").performClick()
        composeTestRule.runOnIdle { assertEquals(2025, selectedYear) }
    }
}
