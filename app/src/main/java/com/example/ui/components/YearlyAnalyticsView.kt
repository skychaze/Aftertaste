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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.ui.MonthChartItem
import com.example.ui.theme.BentoHeroAccent
import com.example.ui.theme.BentoHeroContainer
import com.example.ui.theme.BentoHeroOnContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoStreakIconBg
import com.example.ui.theme.BentoStreakText
import com.example.ui.theme.BentoSurfaceCard
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTileBg
import com.example.ui.theme.BentoTileBorder
import com.example.util.TimeFormatUtils

@Composable
fun YearlyAnalyticsView(
    state: AnalyticsUiState,
    onSelectYear: (Int) -> Unit,
    onSeedData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSeconds = state.yearTotalSeconds
    val totalHours = totalSeconds / 3600
    val totalMinutes = (totalSeconds % 3600) / 60

    var selectedMonthItem by remember { mutableStateOf<MonthChartItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("yearly_analytics_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Year Selector Bento Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BentoHeroContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Year",
                            tint = BentoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Year Analytics",
                        color = BentoTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Year selector pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.availableYears.forEach { yr ->
                        val isSelected = yr == state.selectedYear
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BentoHeroContainer else BentoTileBg)
                                .border(
                                    1.dp,
                                    if (isSelected) BentoHeroAccent else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectYear(yr) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$yr",
                                color = if (isSelected) BentoHeroOnContainer else BentoTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Year Hero Bento Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("year_hero_card"),
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
                    Column {
                        Text(
                            text = "${state.selectedYear} LISTENING TIME",
                            color = BentoTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = TimeFormatUtils.formatDynamicTime(totalSeconds),
                            color = BentoHeroOnContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.peakMonthName != "-") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Peak: ${state.peakMonthName} (${state.peakMonthHours.toInt()}h)",
                                color = BentoHeroOnContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        fontSize = 20.sp,
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stats Bento Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Active Days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BentoTileBg)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "${state.yearActiveDays} Days",
                                color = BentoTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Active Days",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Daily Average
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BentoTileBg)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "${state.yearAverageMinutesPerDay} min",
                                color = BentoTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Daily Avg",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Dynamic Unit Threshold Info
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BentoHeroContainer)
                            .padding(14.dp)
                    ) {
                        Column {
                            val daysEquivalent = totalHours / 24L
                            Text(
                                text = "${daysEquivalent}d",
                                color = BentoHeroOnContainer,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Full Days",
                                color = BentoHeroOnContainer.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 12-Month Interactive Bar Chart Bento Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("yearly_months_chart_card"),
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
                            text = "YEARLY HISTOGRAM (12 MONTHS)",
                            color = BentoTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap any month to view unique tracks played",
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Trending",
                        tint = BentoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                val maxSec = (state.monthlyBreakdown.maxOfOrNull { it.totalSeconds } ?: 1L).coerceAtLeast(3600L)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    state.monthlyBreakdown.forEach { item ->
                        val isSelected = selectedMonthItem?.monthNumber == item.monthNumber
                        val heightFraction = (item.totalSeconds.toFloat() / maxSec).coerceIn(0.06f, 1f)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    selectedMonthItem = if (isSelected) null else item
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Listening hours
                            Text(
                                text = if (item.totalSeconds >= 3600) "${item.totalSeconds / 3600}h" else if (item.totalSeconds > 0) "${item.totalSeconds / 60}m" else "-",
                                color = if (isSelected) BentoPrimary else BentoTextMuted,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Month Bar
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                    .background(
                                        when {
                                            isSelected -> Brush.verticalGradient(
                                                listOf(BentoPrimary, BentoHeroAccent)
                                            )
                                            item.isCurrentMonth -> Brush.verticalGradient(
                                                listOf(BentoPrimary.copy(alpha = 0.85f), BentoPrimary.copy(alpha = 0.5f))
                                            )
                                            item.totalSeconds > 0 -> Brush.verticalGradient(
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
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Month Name Letter (J, F, M, A, M, J...)
                            Text(
                                text = item.monthName.take(1),
                                color = if (isSelected) BentoPrimary else if (item.isCurrentMonth) BentoTextPrimary else BentoTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected || item.isCurrentMonth) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Revealed Unique Tracks List for Selected Month
        AnimatedVisibility(
            visible = selectedMonthItem != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            selectedMonthItem?.let { month ->
                UniqueTracksListCard(
                    title = "${month.monthName} ${state.selectedYear}",
                    subtitle = "Unique Tracks Played (${TimeFormatUtils.formatDynamicTime(month.totalSeconds)})",
                    tracks = month.uniqueTracks,
                    onClose = { selectedMonthItem = null },
                    modifier = Modifier.testTag("yearly_month_tracks_card")
                )
            }
        }

        // Listening Milestones Bento Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("milestones_card"),
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BentoStreakIconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Milestones",
                            tint = BentoStreakText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "YEARLY LISTENING MILESTONES",
                        color = BentoTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                state.milestones.forEach { milestone ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (milestone.isUnlocked) BentoStreakIconBg
                                    else BentoTileBg
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (milestone.isUnlocked) Icons.Default.Star else Icons.Default.Lock,
                                contentDescription = milestone.title,
                                tint = if (milestone.isUnlocked) BentoStreakText else BentoTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = milestone.title,
                                    color = BentoTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${milestone.requiredHours}h",
                                    color = BentoTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { milestone.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (milestone.isUnlocked) BentoStreakText else BentoPrimary,
                                trackColor = BentoTileBg
                            )
                        }
                    }
                }
            }
        }

        // Demo Helper: Seed sample yearly data if desired
        if (state.yearTotalSeconds == 0L) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No history recorded for ${state.selectedYear} yet",
                        color = BentoTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onSeedData,
                        colors = ButtonDefaults.buttonColors(containerColor = BentoHeroContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = "Seed Sample Data",
                            tint = BentoHeroOnContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Populate Sample Yearly Data", color = BentoHeroOnContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
