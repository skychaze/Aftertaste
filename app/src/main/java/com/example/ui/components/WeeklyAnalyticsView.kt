package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    Column(
        modifier = modifier.fillMaxWidth().testTag("last_seven_day_record_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("last_seven_day_record_hero_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Listening history", color = BentoTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${TimeFormatUtils.formatDynamicTime(totalSeconds)} in ${state.historyRange.label.lowercase()}",
                    color = BentoTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryRange.entries.forEach { range ->
                        val selected = range == state.historyRange
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (selected) BentoHeroContainer else BentoTileBg)
                                .border(1.dp, if (selected) BentoPrimary else BentoTileBorder, RoundedCornerShape(12.dp))
                                .clickable { onRangeSelected(range) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                range.label,
                                color = if (selected) BentoHeroOnContainer else BentoTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${state.weekAverageMinutes} min daily average",
                    color = BentoTextMuted,
                    fontSize = 12.sp
                )
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Days", color = BentoTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(days, key = { it.dateStr }) { day ->
                        val selected = day.dateStr == state.selectedHistoryDate
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (selected) BentoHeroContainer else BentoTileBg)
                                .clickable { onDaySelected(if (selected) null else day.dateStr) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(8.dp).height(38.dp).clip(CircleShape)
                                    .background(if (day.seconds > 0L) BentoPrimary else Color.Transparent)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (day.isToday) "Today" else "${day.dayLabel}, ${day.dateStr}",
                                    color = BentoTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (day.seconds > 0L) TimeFormatUtils.formatDynamicTime(day.seconds) else "No listening",
                                    color = BentoTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Text("Open", color = BentoPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

    }
}
