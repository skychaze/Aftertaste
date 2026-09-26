package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.GenreAnalyticsData
import com.example.ui.GenreSliceData
import com.example.ui.components.GenrePieChartCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel8)
class GenreSelectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `removed genre does not show another genres track panel`() {
        val genres = mutableStateOf(
            listOf(
                GenreSliceData("Pop", 600L, 10, 50f, 1, Color.Blue),
                GenreSliceData("Rock", 600L, 10, 50f, 1, Color.Red)
            )
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                GenrePieChartCard(
                    genreData = GenreAnalyticsData(
                        genres = genres.value,
                        totalSeconds = genres.value.sumOf { it.totalSeconds }
                    ),
                    selectedGenre = "Rock",
                    onScopeSelected = {}
                )
            }
        }
        val monthScopeTop = composeTestRule.onNodeWithText("This month").fetchSemanticsNode().boundsInRoot.top
        val yearScopeTop = composeTestRule.onNodeWithText("This year").fetchSemanticsNode().boundsInRoot.top
        val allTimeScopeTop = composeTestRule.onNodeWithText("All time").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Genre scope choices should share one row", maxOf(monthScopeTop, yearScopeTop, allTimeScopeTop) - minOf(monthScopeTop, yearScopeTop, allTimeScopeTop) < 1f)
        composeTestRule.onNodeWithText("Rock Tracks").assertExists()
        val breakdownTop = composeTestRule.onNodeWithText("Listening breakdown")
            .fetchSemanticsNode().boundsInRoot.top
        val tracksTop = composeTestRule.onNodeWithText("Rock Tracks")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Track details should follow the genre breakdown", tracksTop > breakdownTop)

        composeTestRule.runOnIdle { genres.value = genres.value.filter { it.genreName != "Rock" } }
        composeTestRule.onNodeWithText("Rock Tracks").assertDoesNotExist()
        composeTestRule.onNodeWithText("Pop Tracks").assertDoesNotExist()
    }
}
