package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.GenreTags
import com.example.ui.*
import com.example.ui.theme.*
import com.example.util.TimeFormatUtils
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun WeeklyAnalyticsView(
    state: AnalyticsUiState,
    onRangeSelected: (HistoryRange) -> Unit = {},
    onDaySelected: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
    onTrackLiked: ((UniqueTrackItem, Boolean) -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HistoryHeader(state, onRangeSelected, onDaySelected, {}, {})
        state.selectedHistoryDate?.let { date ->
            UniqueTracksListCard(
                prettyDayTitle(date),
                "Listening time and plays on this day.",
                state.selectedDayTracks,
                { onDaySelected(null) },
                editable = false,
                likedTracks = state.likedTracks,
                onTrackLiked = onTrackLiked,
                modifier = Modifier.testTag("last_seven_day_record_tracks_card"),
            )
        }
    }
}

@Composable
fun HistoryHeader(
    state: AnalyticsUiState,
    onRangeSelected: (HistoryRange) -> Unit,
    onDaySelected: (String?) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSortSelected: (HistorySort) -> Unit,
) {
    var showDays by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val total = state.past7Days.sumOf { it.seconds }
    Column(
        Modifier.fillMaxWidth().testTag("last_seven_day_record_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProposalTitle("History", "Find a song. Revisit a listening day.")
        OutlinedTextField(
            value = state.historyQuery,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth().testTag("history_search"),
            label = { Text("Search history") },
            placeholder = { Text("Song, artist or album", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon =
                if (state.historyQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(Icons.Default.Close, "Clear search")
                        }
                    }
                } else null,
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        ProposalSegmentedControl(
            HistoryRange.entries.map { it.label },
            HistoryRange.entries.indexOf(state.historyRange),
            { onRangeSelected(HistoryRange.entries[it]) },
            optionTestTag = { "history_range_${HistoryRange.entries[it].days}_days" },
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "${TimeFormatUtils.formatCompactDuration(total)} recorded",
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                val average =
                    if (state.weekAverageMinutes == 0 && total > 0L) "<1m"
                    else "${state.weekAverageMinutes}m"
                Text(
                    "$average daily average · ${state.past7Days.size} ${if (state.past7Days.size == 1) "day" else "days"}",
                    color = BentoTextSecondary,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = { showDays = !showDays }) {
                Text(if (showDays) "Hide days" else "Browse days")
            }
        }
        Column(Modifier.testTag("history_daily_chart_card")) {
            AnimatedVisibility(showDays || state.selectedHistoryDate != null) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val max = state.past7Days.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L
                    state.past7Days.forEach { day ->
                        val selected = day.dateStr == state.selectedHistoryDate
                        Column(
                            Modifier.width(84.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) BentoHeroContainer else ProposalPanel)
                                .clickable(role = Role.Button) {
                                    onDaySelected(if (selected) null else day.dateStr)
                                }
                                .padding(12.dp)
                                .testTag("history_day_${day.dateStr}")
                                .semantics(mergeDescendants = true) {
                                    contentDescription =
                                        "${prettyDayTitle(day.dateStr)}, ${TimeFormatUtils.formatCompactDuration(day.seconds)}"
                                },
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(day.dayLabel, color = BentoTextSecondary, fontSize = 12.sp)
                            Text(
                                day.dateStr.substring(5).replace('-', '/'),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            ProposalProgressBar(day.seconds.toFloat() / max, height = 4.dp)
                            Text(
                                TimeFormatUtils.formatCompactDuration(day.seconds),
                                fontSize = 12.sp,
                                color = BentoTextSecondary,
                            )
                        }
                    }
                }
            }
        }
        state.selectedHistoryDate?.let { date ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BentoHeroContainer)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Songs from ${prettyDayTitle(date)}", Modifier.weight(1f), fontSize = 14.sp)
                IconButton(onClick = { onDaySelected(null) }) {
                    Icon(Icons.Default.Close, "Show all days")
                }
            }
        }
        ProposalSegmentedControl(
            HistorySort.entries.map { it.label },
            HistorySort.entries.indexOf(state.historySort),
            { onSortSelected(HistorySort.entries[it]) },
        )
    }
}

fun LazyListScope.historyTrackItems(
    state: AnalyticsUiState,
    onTrackSelected: (UniqueTrackItem) -> Unit,
    onLoadMore: () -> Unit,
    onTrackLiked: ((UniqueTrackItem, Boolean) -> Unit)? = null,
) {
    if (state.historyTracks.isEmpty()) {
        item("empty_history") {
            JournalEmptyState(
                if (state.historyQuery.isBlank()) "No listening in this period"
                else "No matching songs",
                if (state.historyQuery.isBlank())
                    "Choose a longer period, or play music to start your history."
                else "Try another song or artist, or choose a longer period.",
            )
        }
    } else {
        item("history_tracks_header") {
            ProposalSectionHead(
                if (state.historyQuery.isBlank()) "Your listening" else "Search results",
                "${state.historyTracks.size} songs",
            )
        }
        items(state.historyTracks, key = { "${it.title}|${it.artist}|${it.genre}" }) { track ->
            Column {
                SlimTrackRow(
                    track.title,
                    track.artist,
                    listeningDuration(track.totalSeconds),
                    playLabel(track.playCount),
                    artworkUrl = track.artworkUrl,
                    onClick = { onTrackSelected(track) },
                    isLiked = state.likedTracks.any { it.trackKey == GenreTags.trackKey(track.artist, track.title) },
                    onLikeChanged = onTrackLiked?.let { callback -> { liked -> callback(track, liked) } },
                )
                ProposalDividerLine()
            }
        }
        if (state.historyTracks.size >= state.historyTrackLimit)
            item("more_history") {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Show more songs")
                }
            }
    }
}

private fun prettyDayTitle(date: String): String {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: return date
    return SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(parsed)
}
