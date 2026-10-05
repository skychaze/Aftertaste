package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.WeekdayListeningView
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class WeekdayListeningViewTest {
    @get:Rule val composeTestRule = createComposeRule()
    private val totals = listOf(21_600L, 57_600L, 0L, 17_280L, 6_480L, 32_400L, 34_560L)
    private val days = listOf(12, 20, 0, 8, 6, 10, 8)

    @Test
    fun `weekday selection reveals the total and active day average`() {
        composeTestRule.setContent {
            MyApplicationTheme { WeekdayListeningView(2026, totals, days) }
        }
        composeTestRule.onNodeWithTag("weekday_1").assertIsSelected()
        composeTestRule.onNodeWithText("Tuesday · 16h total").assertExists()
        composeTestRule.onNodeWithText("Listened on 20 Tuesdays · 48m average").assertExists()
        composeTestRule.onNodeWithTag("weekday_6").performClick()
        composeTestRule.onNodeWithTag("weekday_6").assertIsSelected()
        composeTestRule.onNodeWithText("Sunday · 9h 36m total").assertExists()
        composeTestRule.onNodeWithText("Listened on 8 Sundays · 1h 12m average").assertExists()
    }

    @Test
    fun `average explanation is available on demand and dismissible`() {
        composeTestRule.setContent {
            MyApplicationTheme { WeekdayListeningView(2026, totals, days) }
        }
        composeTestRule.onNodeWithText("Average listening time").assertDoesNotExist()
        composeTestRule.onNodeWithTag("weekday_average_info").performClick()
        composeTestRule.onNodeWithText("Average listening time").assertExists()
        composeTestRule.onNodeWithText(
            "Total listening time on Tuesdays in 2026 divided by the number of Tuesdays with recorded listening. Days without recorded listening do not count."
        ).assertExists()
        composeTestRule.onNodeWithText("Got it").performClick()
        composeTestRule.onNodeWithText("Average listening time").assertDoesNotExist()
    }

    @Test
    fun `zero listening weekday does not show a misleading average`() {
        composeTestRule.setContent {
            MyApplicationTheme { WeekdayListeningView(2026, totals, days) }
        }
        composeTestRule.onNodeWithTag("weekday_2").performClick()
        composeTestRule.onNodeWithText("No listening recorded on Wednesdays in 2026.").assertExists()
        composeTestRule.onNodeWithTag("weekday_average_info").assertDoesNotExist()
    }

    @Test
    fun `default selection follows loaded totals and resets with the year`() {
        val year = mutableStateOf(2026)
        val seconds = mutableStateOf(emptyList<Long>())
        val counts = mutableStateOf(emptyList<Int>())
        composeTestRule.setContent {
            MyApplicationTheme { WeekdayListeningView(year.value, seconds.value, counts.value) }
        }
        composeTestRule.runOnIdle {
            seconds.value = totals
            counts.value = days
        }
        composeTestRule.onNodeWithTag("weekday_1").assertIsSelected()
        composeTestRule.onNodeWithTag("weekday_6").performClick()
        composeTestRule.onNodeWithTag("weekday_average_info").performClick()
        composeTestRule.runOnIdle {
            year.value = 2025
            seconds.value = listOf(60L)
            counts.value = listOf(1)
        }
        composeTestRule.onNodeWithTag("weekday_0").assertIsSelected()
        composeTestRule.onNodeWithText("Average listening time").assertDoesNotExist()
        composeTestRule.onNodeWithText("Total listening time in 2025").assertExists()
        composeTestRule.onNodeWithText("Listened on 1 Monday · 1m average").assertExists()
    }
}
