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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.TrackerUiState
import java.util.Locale
import com.example.ui.theme.BentoHeroOnContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoStreakText
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.ProposalArtBg
import com.example.ui.theme.ProposalArtIcon
import com.example.ui.theme.ProposalLive
import com.example.ui.theme.ProposalPlayingContainer
import com.example.ui.theme.ProposalPlayingText

/**
 * Compact live card from the design proposal: flat tinted container, status line,
 * current song, and an explicit Open YouTube Music action. No progress timeline;
 * the card stays small so more track history fits below it.
 */
@Composable
fun NowPlayingCard(
    state: TrackerUiState,
    onOpenYtMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        state.isActivelyPlaying -> ProposalLive
        state.currentSessionSeconds > 0L -> BentoStreakText
        else -> BentoTextMuted
    }
    val statusText = when {
        state.isActivelyPlaying -> "Now tracking"
        state.currentSessionSeconds > 0L -> "Playback paused"
        else -> "Waiting for music"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ProposalPlayingContainer)
            .padding(15.dp)
            .testTag("now_playing_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                color = ProposalPlayingText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            if (state.isActivelyPlaying) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Session ${formatDurationDetailed(state.currentSessionSeconds)}",
                    color = ProposalPlayingText,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(ProposalArtBg),
                contentAlignment = Alignment.Center
            ) {
                if (!state.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = state.artworkUrl,
                        contentDescription = "Album artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = ProposalArtIcon, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.trackTitle.takeIf { !state.isPlaceholderTrack() } ?: "Nothing playing yet",
                    color = BentoTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = state.artist.takeIf { !state.isPlaceholderTrack() } ?: "Play music to start tracking",
                    color = ProposalPlayingText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenYtMusic)
                .semantics { contentDescription = "Open YouTube Music" }
                .testTag("open_yt_music_button")
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Open YouTube Music", color = BentoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = BentoPrimary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

private fun TrackerUiState.isPlaceholderTrack(): Boolean =
    trackTitle.isBlank() || trackTitle.equals("No music playing", ignoreCase = true) ||
        trackTitle.equals("Unknown Track", ignoreCase = true)

/**
 * Slim single-row banner: icon, two lines of text, and an Enable action.
 * Stays compact so the listening summary keeps its place on first use.
 */
@Composable
fun PermissionBanner(
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ProposalPlayingContainer)
            .padding(12.dp)
            .testTag("permission_banner"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BentoPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Permission Alert",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Notification Access Required",
                color = BentoHeroOnContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Grant access to track YouTube Music playback automatically.",
                color = BentoHeroOnContainer.copy(alpha = 0.75f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onGrantPermission,
            modifier = Modifier.testTag("grant_permission_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary),
            shape = RoundedCornerShape(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
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
