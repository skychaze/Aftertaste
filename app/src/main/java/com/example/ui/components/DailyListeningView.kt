package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.GenreClassifier
import com.example.ui.AnalyticsUiState
import com.example.ui.TodayTrackFeedItem
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily Listening View:
 * - Directly shows tracks listened to today in a clean feed/list.
 * - No histogram (since hourly bins are unnecessary/cluttered per specifications).
 * - Displays whole hours under 24 hours per dynamic time formatting rules.
 * - Live digital playback counter & daily goal progress.
 */
@Composable
fun DailyListeningView(
    state: AnalyticsUiState,
    todayTracks: List<TodayTrackFeedItem> = state.todayTrackFeed,
    onSetDailyGoal: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSeconds = state.trackerState.todayTotalSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val goalMinutes = state.trackerState.dailyGoalMinutes
    val goalSeconds = goalMinutes * 60L
    val goalProgress = if (goalSeconds > 0) (totalSeconds.toFloat() / goalSeconds).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = goalProgress,
        animationSpec = tween(600),
        label = "goalProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_listening_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Today Hero Bento Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("today_hero_card"),
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
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Daily Clock",
                                tint = BentoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TODAY'S LISTENING TIME",
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

                    // Tracking Status Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (state.trackerState.isActivelyPlaying) BentoHeroContainer else BentoTileBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (state.trackerState.isActivelyPlaying) BentoPrimary else BentoTextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.trackerState.isActivelyPlaying) "ACTIVE" else "PAUSED",
                            color = if (state.trackerState.isActivelyPlaying) BentoHeroOnContainer else BentoTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
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
                        text = String.format("%02d", hours),
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
                        text = String.format("%02d", minutes),
                        color = BentoTextPrimary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "m ",
                        color = BentoTextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = String.format("%02d", seconds),
                        color = BentoPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "s",
                        color = BentoTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Daily Goal Progress
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Goal: ${goalMinutes}m",
                            color = BentoTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${(goalProgress * 100).toInt()}% achieved",
                            color = if (goalProgress >= 1f) BentoPrimary else BentoTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = BentoPrimary,
                        trackColor = BentoTileBg
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Goal Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(30, 60, 90, 120).forEach { targetMins ->
                        val isSelected = goalMinutes == targetMins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BentoHeroContainer else BentoTileBg)
                                .border(
                                    1.dp,
                                    if (isSelected) BentoHeroAccent else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSetDailyGoal(targetMins) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${targetMins}m",
                                color = if (isSelected) BentoHeroOnContainer else BentoTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Daily Track List / Feed Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("today_track_feed_card"),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = BentoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TODAY'S TRACK FEED",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${todayTracks.size} ${if (todayTracks.size == 1) "track played" else "tracks played"}",
                                color = BentoTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (todayTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(BentoTileBg)
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = BentoTextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        todayTracks.forEachIndexed { index, track ->
                            val genreColor = GenreClassifier.getColorForGenre(track.genre)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BentoTileBg)
                                    .border(1.dp, BentoTileBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Track feed artwork or icon badge
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(genreColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!track.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = track.artworkUrl,
                                            contentDescription = "Track Artwork",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = genreColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Track title & artist
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = BentoTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.artist,
                                            color = BentoTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (track.genre.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(genreColor.copy(alpha = 0.15f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = track.genre,
                                                    color = genreColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Duration & time played
                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (track.isActivelyPlaying) {
                                            LiveEqualizerIndicator(
                                                isPlaying = true,
                                                color = BentoPrimary,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                        }
                                        Text(
                                            text = TimeFormatUtils.formatTrackDuration(track.durationSeconds),
                                            color = BentoPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (track.playCount > 1) {
                                            Text(
                                                text = "${track.playCount}x  •  ",
                                                color = BentoPrimary.copy(alpha = 0.85f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = timeFormat.format(Date(track.timestamp)),
                                            color = BentoTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
