package com.example.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LikedTrackEntity
import com.example.tracker.GenreTags
import com.example.ui.UniqueTrackItem
import com.example.ui.theme.BentoTextSecondary

@Composable
fun UniqueTracksListCard(
    title: String,
    helper: String,
    tracks: List<UniqueTrackItem>,
    onClose: () -> Unit,
    editable: Boolean = true,
    onEditGenre: (UniqueTrackItem, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    existingGenres: List<String> = emptyList(),
    likedTracks: List<LikedTrackEntity> = emptyList(),
    onTrackLiked: ((UniqueTrackItem, Boolean) -> Unit)? = null,
) {
    var selectedTrack by remember { mutableStateOf<UniqueTrackItem?>(null) }
    selectedTrack?.let { track ->
        TrackDetailsDialog(
            track, { selectedTrack = null }, if (editable) onEditGenre else null, existingGenres,
            isLiked = likedTracks.any { it.trackKey == GenreTags.trackKey(track.artist, track.title) },
            onLikeChanged = onTrackLiked?.let { callback -> { liked -> callback(track, liked) } },
        )
    }
    ProposalDetailPanel(
        title = title,
        helper = helper,
        onClose = onClose,
        modifier = modifier,
    ) {
        if (tracks.isEmpty()) {
            Text(
                "No tracks recorded for this selection.",
                color = BentoTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(tracks.size, key = { "${tracks[it].title}|${tracks[it].artist}" }) { index ->
                    val track = tracks[index]
                    if (index > 0) ProposalDividerLine()
                    SlimTrackRow(
                        title = track.title,
                        artist = track.artist,
                        duration = listeningDuration(track.totalSeconds),
                        plays = playLabel(track.playCount),
                        artworkUrl = track.artworkUrl,
                        onClick = { selectedTrack = track },
                        isLiked = likedTracks.any { it.trackKey == GenreTags.trackKey(track.artist, track.title) },
                        onLikeChanged = onTrackLiked?.let { callback -> { liked -> callback(track, liked) } },
                    )
                }
            }
        }
    }
}
