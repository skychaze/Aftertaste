package com.example.ui.components

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ArtistAggregateRow
import com.example.tracker.ArtworkResolver
import com.example.ui.DateBounds
import com.example.ui.UniqueTrackItem
import com.example.ui.theme.*
import com.example.util.TimeFormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrackArtwork(
    title: String,
    artist: String,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    color: Color = BentoHeroContainer,
) {
    val context = LocalContext.current
    val resolved by
        produceState<String?>(null, title, artist, artworkUrl) {
            value =
                withContext(Dispatchers.IO) {
                    ArtworkResolver.resolveArtwork(context, artist, title, artworkUrl)
                }
        }
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        RecordMark(Modifier.fillMaxSize().padding(10.dp), BentoPrimary.copy(alpha = 0.75f))
        resolved?.let { path ->
            AsyncImage(
                model =
                    ImageRequest.Builder(context)
                        .data(path)
                        .crossfade(
                            if (
                                Settings.Global.getFloat(
                                    context.contentResolver,
                                    Settings.Global.ANIMATOR_DURATION_SCALE,
                                    1f,
                                ) > 0f
                            )
                                180
                            else 0
                        )
                        .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun RecordMark(modifier: Modifier = Modifier, color: Color = BentoPrimary) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.38f
        drawCircle(color, radius, style = Stroke(size.minDimension * 0.07f))
        drawCircle(color, radius * 0.57f, style = Stroke(size.minDimension * 0.07f))
        drawCircle(color, radius * 0.14f)
        drawLine(
            color,
            center.copy(x = center.x + radius * 0.85f, y = center.y - radius * 1.1f),
            center.copy(x = center.x + radius * 0.85f, y = center.y - radius * 0.15f),
            strokeWidth = size.minDimension * 0.09f,
        )
        drawLine(
            color,
            center.copy(x = center.x + radius * 0.85f, y = center.y - radius * 0.15f),
            center.copy(x = center.x + radius * 0.35f, y = center.y + radius * 0.35f),
            strokeWidth = size.minDimension * 0.09f,
        )
    }
}

@Composable
fun JournalEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(BentoHeroContainer.copy(alpha = 0.6f))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RecordMark(Modifier.size(44.dp))
        Text(title, color = BentoTextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(body, color = BentoTextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
fun TrackRanking(
    title: String,
    helper: String,
    tracks: List<UniqueTrackItem>,
    onEditGenre: ((UniqueTrackItem, String) -> Unit)? = null,
) {
    var selected by remember { mutableStateOf<UniqueTrackItem?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProposalSectionHead(title, "${tracks.size} songs")
        Text(helper, color = BentoTextSecondary, fontSize = 13.sp)
        tracks.forEachIndexed { index, track ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${index + 1}",
                    fontSize = 14.sp,
                    color = BentoTextSecondary,
                    modifier = Modifier.width(20.dp),
                )
                SlimTrackRow(
                    track.title,
                    track.artist,
                    listeningDuration(track.totalSeconds),
                    playLabel(track.playCount),
                    artworkUrl = track.artworkUrl,
                    onClick = { selected = track },
                )
            }
        }
    }
    selected?.let { TrackDetailsDialog(it, { selected = null }, onEditGenre) }
}

@Composable
fun ArtistRanking(artists: List<ArtistAggregateRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ProposalSectionHead("Your favourite artists", "By listening time")
        val maximum = artists.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1L) ?: 1L
        artists.forEachIndexed { index, artist ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (index % 2 == 0) Color(0xFFCFDFD6) else Color(0xFFF9D6C0)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            artist.artist.take(1).uppercase(Locale.getDefault()),
                            color = BentoTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        artist.artist,
                        Modifier.weight(1f),
                        fontSize = 16.sp,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        TimeFormatUtils.formatCompactDuration(artist.totalSeconds),
                        fontSize = 13.sp,
                        color = BentoTextSecondary,
                    )
                }
                ProposalProgressBar(
                    artist.totalSeconds.toFloat() / maximum,
                    height = 4.dp,
                    color = if (index % 2 == 0) Color(0xFF197A72) else Color(0xFFAA5D39),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailsDialog(
    track: UniqueTrackItem,
    onDismiss: () -> Unit,
    onEditGenre: ((UniqueTrackItem, String) -> Unit)? = null,
) {
    var editing by remember(track) { mutableStateOf(false) }
    var genre by remember(track) { mutableStateOf(track.genre) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BentoBackground) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(track.title, track.artist, track.artworkUrl, Modifier.size(88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        track.title,
                        fontSize = 22.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary,
                    )
                    Text(track.artist, fontSize = 16.sp, color = BentoTextSecondary)
                }
            }
            track.album
                ?.takeIf { it.isNotBlank() && it != "YouTube Music" }
                ?.let { Text(it, color = BentoTextSecondary, fontSize = 14.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column {
                    Text(
                        listeningDuration(track.totalSeconds),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                    )
                    Text(
                        "Listening time in this view",
                        fontSize = 12.sp,
                        color = BentoTextSecondary,
                    )
                }
                Column {
                    Text("${track.playCount}", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("Plays in this view", fontSize = 12.sp, color = BentoTextSecondary)
                }
            }
            Text(
                "Last played ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(track.lastPlayedTimestamp))}",
                fontSize = 13.sp,
                color = BentoTextSecondary,
            )
            Text(
                if (track.genre == "Other") "Genre not yet classified" else track.genre,
                color = BentoPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (onEditGenre != null) {
                if (editing) {
                    OutlinedTextField(
                        genre,
                        { genre = it.take(60) },
                        label = { Text("Genre for this song") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onEditGenre(track, genre.trim())
                            onDismiss()
                        },
                        enabled = genre.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save genre")
                    }
                } else TextButton(onClick = { editing = true }) { Text("Edit this song's genre") }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

fun playLabel(count: Int): String = "$count ${if (count == 1) "play" else "plays"}"

fun periodLabel(bounds: DateBounds): String {
    if (bounds.startDate == "0001-01-01") return "Across your listening history"
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val output = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    return "${output.format(requireNotNull(input.parse(bounds.startDate)))} to ${output.format(requireNotNull(input.parse(bounds.endDate)))}"
}

fun listeningDuration(seconds: Long): String =
    if (seconds >= 3600L) TimeFormatUtils.formatCompactDuration(seconds)
    else TimeFormatUtils.formatTrackDuration(seconds)
