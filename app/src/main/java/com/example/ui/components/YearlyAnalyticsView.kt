package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AnalyticsUiState
import com.example.ui.theme.BentoHeroContainer
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
    val fullDays = totalSeconds / 86_400L

    Column(
        modifier = modifier.fillMaxWidth().testTag("yearly_analytics_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your year", color = BentoTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Listening totals, streaks, and milestones.", color = BentoTextSecondary, fontSize = 13.sp)
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("year_selector_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BentoHeroContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Year", color = BentoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.availableYears.forEach { year ->
                        val selected = year == state.selectedYear
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(shape)
                                .background(if (selected) BentoPrimary else BentoTileBg)
                                .border(1.dp, if (selected) BentoPrimary else BentoTileBorder, shape)
                                .clickable { onSelectYear(year) }
                                .padding(horizontal = 14.dp)
                                .testTag("select_year_$year"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$year",
                                color = if (selected) Color.White else BentoTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("year_hero_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text("${state.selectedYear} listening time", color = BentoTextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            TimeFormatUtils.formatCompactDuration(totalSeconds),
                            color = BentoTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    if (state.peakMonthName != "-") {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroContainer)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("Peak month", color = BentoTextSecondary, fontSize = 10.sp)
                            Text(
                                "${state.peakMonthName.take(3)} · ${TimeFormatUtils.formatCompactDuration((state.peakMonthHours * 3600f).toLong())}",
                                color = BentoPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InsightStat("Active days", "${state.yearActiveDays}")
                    InsightStat("Avg per active day", "${state.yearAverageMinutesPerDay}m")
                    InsightStat("Full days", "${fullDays}d")
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BentoStreakIconBg.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = BentoStreakText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Current streak", color = BentoTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.currentStreakDays} ${if (state.currentStreakDays == 1) "day" else "days"}",
                        color = BentoStreakText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (totalSeconds == 0L) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("yearly_empty_state"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No history for ${state.selectedYear} yet", color = BentoTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Load sample data to preview yearly insights.", color = BentoTextSecondary, fontSize = 12.sp)
                    Button(
                        onClick = onSeedData,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary)
                    ) {
                        Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Load sample data", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("milestones_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(BentoHeroContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Listening milestones", color = BentoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Progress toward each listening goal", color = BentoTextSecondary, fontSize = 12.sp)
                    }
                }

                state.milestones.forEach { milestone ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (milestone.isUnlocked) Icons.Default.Star else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (milestone.isUnlocked) BentoStreakText else BentoTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(milestone.title, color = BentoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(milestone.description, color = BentoTextSecondary, fontSize = 11.sp)
                            }
                            Text("${milestone.requiredHours}h", color = BentoTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { milestone.progressFraction },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (milestone.isUnlocked) BentoStreakText else BentoPrimary,
                            trackColor = BentoTileBg
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.InsightStat(label: String, value: String) {
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(BentoTileBg).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, color = BentoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = BentoTextSecondary, fontSize = 10.sp, minLines = 2)
    }
}
