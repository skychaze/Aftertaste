package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AnalyticsUiState
import com.example.ui.DayChartItem
import com.example.ui.UniqueTrackItem
import com.example.ui.theme.BentoHeroAccent
import com.example.ui.theme.BentoHeroContainer
import com.example.ui.theme.BentoHeroOnContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoSurfaceCard
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTileBg
import com.example.ui.theme.BentoTileBorder
import com.example.util.TimeFormatUtils

/**
 * Weekly Analytics View:
 * - Displays daily listening totals across the week in an interactive 7-Day Histogram.
 * - Dynamic Time Formatting: Upgrades to [X] Day(s) [Y] Hour(s) when >= 24h, else [Y] Hour(s).
 * - Clicking a day's bar reveals the unique, non-repeating tracks played on that date.
 */
@Composable
fun WeeklyAnalyticsView(
    state: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    val past7Days = state.past7Days
    val totalSeconds = past7Days.sumOf { it.seconds }
    val totalHours = totalSeconds / 3600
    val totalMinutes = (totalSeconds % 3600) / 60

    var selectedDayItem by remember { mutableStateOf<DayChartItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_analytics_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Weekly Hero Bento Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("weekly_hero_card"),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Weekly",
                                tint = BentoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "WEEKLY LISTENING TIME",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = TimeFormatUtils.formatDynamicTime(totalSeconds),
                                color = BentoHeroOnContainer,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Average per day pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoTileBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Avg: ${state.weekAverageMinutes}m / day",
                            color = BentoTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Big Digital Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$totalHours",
                        color = BentoTextPrimary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "h ",
                        color = BentoTextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "$totalMinutes",
                        color = BentoPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "m",
                        color = BentoTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        // 7-Day Interactive Histogram Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("weekly_histogram_card"),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "WEEKLY HISTOGRAM",
                            color = BentoTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap any bar to view unique tracks played",
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Histogram",
                        tint = BentoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                val maxSec = (past7Days.maxOfOrNull { it.seconds } ?: 1L).coerceAtLeast(1800L) // at least 30m for scale

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    past7Days.forEach { item ->
                        val isSelected = selectedDayItem?.dateStr == item.dateStr
                        val heightFraction = (item.seconds.toFloat() / maxSec).coerceIn(0.06f, 1f)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    selectedDayItem = if (isSelected) null else item
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Listening time on top of bar
                            Text(
                                text = if (item.seconds >= 3600) "${item.seconds / 3600}h" else "${item.minutes}m",
                                color = if (isSelected) BentoPrimary else BentoTextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Bar
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .background(
                                        when {
                                            isSelected -> Brush.verticalGradient(
                                                listOf(BentoPrimary, BentoHeroAccent)
                                            )
                                            item.isToday -> Brush.verticalGradient(
                                                listOf(BentoPrimary.copy(alpha = 0.85f), BentoPrimary.copy(alpha = 0.5f))
                                            )
                                            item.seconds > 0 -> Brush.verticalGradient(
                                                listOf(BentoHeroContainer, BentoHeroContainer.copy(alpha = 0.6f))
                                            )
                                            else -> Brush.verticalGradient(
                                                listOf(BentoTileBg, BentoTileBg)
                                            )
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) BentoPrimary else Color.Transparent,
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                    )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Day Label (e.g. Mon, Tue)
                            Text(
                                text = item.dayLabel,
                                color = if (isSelected) BentoPrimary else if (item.isToday) BentoTextPrimary else BentoTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected || item.isToday) FontWeight.Bold else FontWeight.Normal
                            )

                            // Day Number (e.g. 2, 3)
                            Text(
                                text = "${item.dayNumber}",
                                color = if (item.isToday) BentoPrimary else BentoTextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Revealed Unique Tracks List for Selected Day
        AnimatedVisibility(
            visible = selectedDayItem != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            selectedDayItem?.let { day ->
                UniqueTracksListCard(
                    title = "${day.dayLabel}, ${day.dateStr}",
                    subtitle = "Unique Tracks Played (${TimeFormatUtils.formatDynamicTime(day.seconds)})",
                    tracks = day.uniqueTracks,
                    onClose = { selectedDayItem = null },
                    modifier = Modifier.testTag("weekly_day_tracks_card")
                )
            }
        }
    }
}
