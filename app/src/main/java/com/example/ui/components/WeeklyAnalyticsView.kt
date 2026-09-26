package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AnalyticsUiState
import com.example.ui.DayChartItem
import com.example.ui.HistoryRange
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

@Composable
fun WeeklyAnalyticsView(
    state: AnalyticsUiState,
    onRangeSelected: (HistoryRange) -> Unit = {},
    onDaySelected: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val days = state.past7Days
    val totalSeconds = days.sumOf { it.seconds }
    val averageLabel = when {
        state.weekAverageMinutes > 0 -> "${state.weekAverageMinutes}m"
        totalSeconds > 0L -> "<1m"
        else -> "0m"
    }
    val dayCountLabel = if (days.size == 1) "day" else "days"

    Column(
        modifier = modifier.fillMaxWidth().testTag("last_seven_day_record_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Listening history", color = BentoTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Review daily totals and open a day to see its tracks.", color = BentoTextSecondary, fontSize = 13.sp)
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("last_seven_day_record_hero_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Listening time", color = BentoTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    TimeFormatUtils.formatCompactDuration(totalSeconds),
                    color = BentoTextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "$averageLabel daily average · ${days.size} $dayCountLabel",
                    color = BentoTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryRange.entries.forEach { range ->
                        val selected = range == state.historyRange
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(shape)
                                .background(if (selected) BentoPrimary else BentoTileBg)
                                .border(1.dp, if (selected) BentoPrimary else BentoTileBorder, shape)
                                .clickable { onRangeSelected(range) }
                                .testTag("history_range_${range.days}_days"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                range.label,
                                color = if (selected) androidx.compose.ui.graphics.Color.White else BentoTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("history_daily_chart_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Daily listening", color = BentoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("Tap a day to see its tracks", color = BentoTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))

                if (totalSeconds == 0L) {
                    Text("No listening recorded in this period yet.", color = BentoTextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                }

                val maxDaySeconds = days.maxOfOrNull { it.seconds } ?: 0L
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEach { day ->
                        HistoryDayBar(
                            day = day,
                            maxDaySeconds = maxDaySeconds,
                            selected = day.dateStr == state.selectedHistoryDate,
                            showMonth = state.historyRange != HistoryRange.SEVEN_DAYS,
                            onClick = { onDaySelected(if (day.dateStr == state.selectedHistoryDate) null else day.dateStr) }
                        )
                    }
                }
            }
        }

        state.selectedHistoryDate?.let { date ->
            UniqueTracksListCard(
                title = date,
                subtitle = "Tracks played",
                tracks = state.selectedDayTracks,
                onClose = { onDaySelected(null) },
                modifier = Modifier.testTag("last_seven_day_record_tracks_card")
            )
        }
    }
}

@Composable
private fun HistoryDayBar(
    day: DayChartItem,
    maxDaySeconds: Long,
    selected: Boolean,
    showMonth: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val barHeight = if (maxDaySeconds == 0L) 4f else 4f + 44f * day.seconds / maxDaySeconds
    Column(
        modifier = Modifier
            .width(48.dp)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) BentoHeroContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${day.dayLabel}, ${day.dateStr}, ${TimeFormatUtils.formatCompactDuration(day.seconds)}"
            }
            .testTag("history_day_${day.dateStr}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            TimeFormatUtils.formatCompactDuration(day.seconds),
            color = BentoTextSecondary,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.width(22.dp).height(50.dp).clip(RoundedCornerShape(8.dp)).background(BentoTileBg),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(barHeight.dp)
                    .clip(RoundedCornerShape(8.dp)).background(if (selected) BentoPrimary else BentoHeroContainer)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(day.dayLabel, color = BentoTextSecondary, fontSize = 10.sp, maxLines = 1)
        Text(
            if (showMonth) day.dateStr.substring(5) else "${day.dayNumber}",
            color = BentoTextPrimary,
            fontSize = if (showMonth) 9.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
