package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.TrackerUiState
import com.example.ui.theme.BentoHeroOnContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.ProposalLive
import com.example.ui.theme.ProposalPlayingContainer
import java.util.Locale

@Composable
fun NowPlayingCard(
    state: TrackerUiState,
    onOpenYtMusic: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaybackCommand: (com.example.tracker.PlaybackCommand) -> Unit = {},
) {
    val hasTrack = !state.isPlaceholderTrack()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE2EBE6))
            .padding(18.dp)
            .testTag("now_playing_card"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.isActivelyPlaying) "Now playing"
                else if (hasTrack) "Paused" else "Ready when you are",
                color = if (state.isActivelyPlaying) ProposalLive else BentoTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            androidx.compose.material3.IconButton(onClick = onOpenYtMusic) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    "Open YouTube Music",
                    tint = BentoPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TrackArtwork(
                state.trackTitle,
                state.artist,
                state.artworkUrl,
                Modifier.size(88.dp),
                color = Color(0xFFF9D6C0),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (hasTrack) state.trackTitle else "Press play in YouTube Music",
                    color = BentoTextPrimary,
                    fontSize = 20.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (hasTrack) state.artist else "Your listening journal starts with a song.",
                    color = BentoTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasTrack) {
            if (state.trackDurationMs > 0L) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProposalProgressBar(
                        (state.trackPositionMs.toFloat() / state.trackDurationMs).coerceIn(0f, 1f),
                        height = 4.dp,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatDurationDetailed(state.trackPositionMs / 1000L),
                            fontSize = 12.sp,
                            color = BentoTextSecondary,
                        )
                        Text(
                            formatDurationDetailed(state.trackDurationMs / 1000L),
                            fontSize = 12.sp,
                            color = BentoTextSecondary,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(
                    onClick = { onPlaybackCommand(com.example.tracker.PlaybackCommand.PREVIOUS) },
                    enabled = state.playbackControls.canGoPrevious,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        "Previous track",
                        modifier = Modifier.size(30.dp),
                    )
                }
                androidx.compose.material3.FilledIconButton(
                    onClick = { onPlaybackCommand(com.example.tracker.PlaybackCommand.PLAY_PAUSE) },
                    enabled = state.playbackControls.canPlayPause,
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(
                        if (state.isActivelyPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        if (state.isActivelyPlaying) "Pause playback" else "Resume playback",
                        modifier = Modifier.size(30.dp),
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { onPlaybackCommand(com.example.tracker.PlaybackCommand.NEXT) },
                    enabled = state.playbackControls.canGoNext,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.SkipNext, "Next track", modifier = Modifier.size(30.dp))
                }
            }
            if (!state.playbackControls.canPlayPause)
                Text(
                    "Open your player to connect playback controls.",
                    color = BentoTextSecondary,
                    fontSize = 12.sp,
                )
        } else {
            androidx.compose.material3.TextButton(onClick = onOpenYtMusic) {
                Text("Open YouTube Music", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionBanner(
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ProposalPlayingContainer)
                .padding(12.dp)
                .testTag("permission_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BentoPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Permission Alert",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Notification Access Required",
                color = BentoHeroOnContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Grant access to track YouTube Music playback automatically.",
                color = BentoHeroOnContainer.copy(alpha = 0.75f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onGrantPermission,
            modifier = Modifier.testTag("grant_permission_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary),
            shape = RoundedCornerShape(12.dp),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp,
                    vertical = 8.dp,
                ),
        ) {
            Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

fun formatSecondsToHoursMinutes(seconds: Long): Pair<Int, Int> {
    val totalMinutes = seconds / 60
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return Pair(hours, minutes)
}

fun formatDurationDetailed(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02dm %02ds", minutes, secs)
    }
}

/** Formats a track position/duration in milliseconds as a player clock (m:ss / h:mm:ss). */
fun formatTrackClock(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}

@Composable
fun MiniPlaybackBar(
    state: TrackerUiState,
    onCommand: (com.example.tracker.PlaybackCommand) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(ProposalPlayingContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrackArtwork(state.trackTitle, state.artist, state.artworkUrl, Modifier.size(40.dp))
        Column(Modifier.weight(1f).clickable(onClick = onOpenPlayer)) {
            Text(
                state.trackTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.artist,
                color = BentoTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.material3.IconButton(
            onClick = { onCommand(com.example.tracker.PlaybackCommand.PREVIOUS) },
            enabled = state.playbackControls.canGoPrevious,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.SkipPrevious, "Previous track")
        }
        androidx.compose.material3.IconButton(
            onClick = { onCommand(com.example.tracker.PlaybackCommand.PLAY_PAUSE) },
            enabled = state.playbackControls.canPlayPause,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                if (state.isActivelyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (state.isActivelyPlaying) "Pause playback" else "Resume playback",
            )
        }
        androidx.compose.material3.IconButton(
            onClick = { onCommand(com.example.tracker.PlaybackCommand.NEXT) },
            enabled = state.playbackControls.canGoNext,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.SkipNext, "Next track")
        }
    }
}

private fun TrackerUiState.isPlaceholderTrack(): Boolean =
    trackTitle.isBlank() ||
        trackTitle.equals("No music playing", ignoreCase = true) ||
        trackTitle.equals("Unknown Track", ignoreCase = true)

/**
 * Slim single-row banner: icon, two lines of text, and an Enable action. Stays compact so the
 * listening summary keeps its place on first use.
 */
