package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.GenreClassifier
import com.example.ui.AnalyticsUiState
import com.example.ui.TodayTrackFeedItem
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoStreakText
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.ProposalLive
import com.example.ui.theme.ProposalMuted
import com.example.ui.theme.ProposalPanel
import com.example.ui.theme.ProposalSub
import com.example.util.TimeFormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Today tab from the design proposal: one listening total first, goal row, compact live card, track rows. */
@Composable
fun DailyListeningView(
    state: AnalyticsUiState,
    todayTracks: List<TodayTrackFeedItem> = state.todayTrackFeed,
    onSetDailyGoal: (Int) -> Unit,
    onOpenYtMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditingGoal by remember { mutableStateOf(false) }
    val tracker = state.trackerState
    val totalSeconds = tracker.todayTotalSeconds

    val goalMinutes = tracker.dailyGoalMinutes
    val goalSeconds = goalMinutes * 60L
    val goalProgress = if (goalSeconds > 0) (totalSeconds.toFloat() / goalSeconds).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = goalProgress,
        animationSpec = tween(600),
        label = "goalProgress"
    )
    val remainingMinutes = ((goalSeconds - totalSeconds).coerceAtLeast(0L) + 59L) / 60L

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BentoBackground)
            .testTag("daily_listening_view"),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        ProposalTitle(
            title = "Today",
            subtitle = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
            status = {
                val (dot, label) = when {
                    tracker.isActivelyPlaying -> ProposalLive to "Tracking"
                    tracker.currentSessionSeconds > 0L -> BentoStreakText to "Paused"
                    else -> ProposalMuted to "Waiting"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape).background(dot)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(label, color = dot, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProposalTotal(TimeFormatUtils.formatTrackDuration(totalSeconds))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${TimeFormatUtils.formatCompactDuration(totalSeconds)} of ${goalMinutes}m",
                    color = BentoTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = { isEditingGoal = !isEditingGoal },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(if (isEditingGoal) "Done" else "Edit goal", color = BentoPrimary, fontSize = 12.sp)
                }
            }

            ProposalProgressBar(animatedProgress)

            Text(
                text = if (goalProgress >= 1f) "Daily goal reached"
                else if (remainingMinutes == 1L) "1 minute to your daily goal"
                else "$remainingMinutes minutes to your daily goal",
                color = ProposalSub,
                fontSize = 11.sp
            )

            if (isEditingGoal) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(30, 60, 90, 120).forEach { targetMins ->
                        TextButton(
                            onClick = {
                                onSetDailyGoal(targetMins)
                                isEditingGoal = false
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text(
                                text = "${targetMins}m",
                                color = if (goalMinutes == targetMins) BentoPrimary else BentoTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (goalMinutes == targetMins) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        NowPlayingCard(state = tracker, onOpenYtMusic = onOpenYtMusic)

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ProposalSectionHead(
                title = "Today's tracks",
                count = "${todayTracks.size} ${if (todayTracks.size == 1) "track" else "tracks"}"
            )

            if (todayTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ProposalPanel)
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No tracks played yet today",
                            color = BentoTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Play any song in YouTube Music to build your daily feed.",
                            color = BentoTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column {
                    todayTracks.forEachIndexed { index, track ->
                        if (index > 0) ProposalDividerLine()
                        SlimTrackRow(
                            title = track.title,
                            artist = track.artist,
                            duration = TimeFormatUtils.formatTrackDuration(track.durationSeconds),
                            plays = "${track.playCount} ${if (track.playCount == 1) "play" else "plays"}",
                            artworkUrl = track.artworkUrl,
                            artColor = GenreClassifier.getColorForGenre(track.genre)
                        )
                    }
                }
            }
        }
    }
}
