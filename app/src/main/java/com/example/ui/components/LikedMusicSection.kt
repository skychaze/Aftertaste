package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LikedTrackEntity
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

@Composable
fun LikedMusicSection(tracks: List<LikedTrackEntity>, onUnlike: (LikedTrackEntity) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProposalSectionHead(
            "Liked music",
            "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}",
            Modifier.semantics { heading() },
        )
        Text(
            "Saved across all time. Open a song in YouTube Music to play it.",
            color = BentoTextSecondary,
            fontSize = 13.sp,
        )
        if (tracks.isEmpty()) {
            Text(
                "Tap the heart on Today's playing card to save a song here.",
                color = BentoTextSecondary,
                fontSize = 14.sp,
            )
        }
        tracks.forEach { track ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Open ${track.title} in YouTube Music",
                        ) {
                            openTrackInYouTubeMusic(context, track.title, track.artist)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TrackArtwork(track.title, track.artist, track.artworkUrl, Modifier.size(56.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            track.title,
                            color = BentoTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artist,
                            color = BentoTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        null,
                        tint = BentoPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { onUnlike(track) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Favorite, "Unlike ${track.title}", tint = BentoPrimary)
                }
            }
        }
    }
}
