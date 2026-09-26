package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.GenreClassifier
import com.example.ui.UniqueTrackItem
import com.example.ui.theme.BentoTextSecondary
import com.example.util.TimeFormatUtils

/**
 * Flat detail panel that opens below a breakdown, in the proposal style.
 * Used for a history day's tracks and for a selected genre's tracks.
 * Tracks are tappable for genre editing only when [editable] is true.
 */
@Composable
fun UniqueTracksListCard(
    title: String,
    helper: String,
    tracks: List<UniqueTrackItem>,
    onClose: () -> Unit,
    editable: Boolean = true,
    onEditGenre: (UniqueTrackItem, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<UniqueTrackItem?>(null) }
    var genreInput by remember { mutableStateOf("") }
    if (editable) {
        editing?.let { track ->
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("Set genre for this song") },
                text = {
                    Column {
                        Text("${track.title} - ${track.artist}")
                        OutlinedTextField(
                            value = genreInput,
                            onValueChange = { genreInput = it.take(60) },
                            label = { Text("Genre") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (genreInput.isNotBlank()) {
                            onEditGenre(track, genreInput)
                            editing = null
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
            )
        }
    }
    ProposalDetailPanel(
        title = title,
        helper = helper,
        onClose = onClose,
        modifier = modifier
    ) {
        if (tracks.isEmpty()) {
            Text(
                "No tracks recorded for this selection.",
                color = BentoTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column {
                tracks.forEachIndexed { index, track ->
                    if (index > 0) ProposalDividerLine()
                    SlimTrackRow(
                        title = track.title,
                        artist = track.artist,
                        duration = TimeFormatUtils.formatTrackDuration(track.totalSeconds),
                        plays = "${track.playCount} ${if (track.playCount == 1) "play" else "plays"}",
                        artworkUrl = track.artworkUrl,
                        artColor = GenreClassifier.getColorForGenre(track.genre),
                        onClick = if (editable) {
                            {
                                editing = track
                                genreInput = track.genre
                            }
                        } else null
                    )
                }
            }
        }
    }
}
