package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.ArtistAggregateRow
import com.example.data.LikedTrackEntity
import com.example.ui.*
import com.example.ui.theme.*
import com.example.util.TimeFormatUtils
import java.util.Locale

private enum class TasteSection(val label: String) {
    GENRES("Genres"),
    SONGS("Songs"),
    ARTISTS("Artists"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenrePieChartCard(
    genreData: GenreAnalyticsData,
    selectedGenre: String? = null,
    selectedGenreTracks: List<UniqueTrackItem> = emptyList(),
    onGenreSelected: (String?) -> Unit = {},
    onEditTrackGenre: (UniqueTrackItem, String) -> Unit = { _, _ -> },
    onScopeSelected: (GenreScope) -> Unit,
    modifier: Modifier = Modifier,
    onSeedSampleData: (() -> Unit)? = null,
    topTracks: List<UniqueTrackItem> = emptyList(),
    topArtists: List<ArtistAggregateRow> = emptyList(),
    bounds: DateBounds? = null,
    likedTracks: List<LikedTrackEntity> = emptyList(),
    onUnlikeTrack: (LikedTrackEntity) -> Unit = {},
    existingGenres: List<String> = emptyList(),
    onTrackLiked: ((UniqueTrackItem, Boolean) -> Unit)? = null,
) {
    val selected = genreData.genres.firstOrNull { it.genreName == selectedGenre }
    val known = genreData.genres.filter { it.genreName != "Other" }
    val leader = known.firstOrNull()
    var showAllGenres by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(TasteSection.GENRES) }
    val scopes = listOf(GenreScope.MONTH, GenreScope.THREE_MONTHS, GenreScope.SIX_MONTHS)
    Column(
        modifier = modifier.fillMaxWidth().testTag("genre_analytics_card"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProposalTitle("Your taste", "The music you keep coming back to.")
        Column {
            ProposalSegmentedControl(
                options = scopes.map { it.label },
                selectedIndex = scopes.indexOf(genreData.scope),
                onSelect = { onScopeSelected(scopes[it]) },
                optionTestTag = { "taste_period_${scopes[it].name}" },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                listOf(GenreScope.YEAR, GenreScope.ALL_TIME).forEach { scope ->
                    TextButton(onClick = { onScopeSelected(scope) }) {
                        Text(
                            scope.label,
                            fontWeight =
                                if (scope == genreData.scope) FontWeight.Bold
                                else FontWeight.Normal,
                        )
                    }
                }
            }
            bounds?.let { Text(periodLabel(it), fontSize = 12.sp, color = BentoTextSecondary) }
        }
        if (genreData.genres.isEmpty()) {
            if (section == TasteSection.GENRES) {
                JournalEmptyState(
                    "Your taste starts here",
                    "Listen as you normally do. Your genres, favourite artists and most played songs will build up here over time.",
                )
                if (onSeedSampleData != null)
                    TextButton(onClick = onSeedSampleData) { Text("Explore with sample history") }
            }
        } else {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoHeroContainer)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (leader == null) "Still finding your sound" else leader.genreName,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BentoTextPrimary,
                    )
                    Text(
                        if (leader == null) "Label your songs to reveal your genre profile."
                        else
                            "${String.format(Locale.getDefault(), "%.0f", leader.percentage)}% of your listening time. ${if (leader.percentage >= 50f) "Your listening centres on this genre." else "Your most listened-to labelled genre in this period."}",
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = BentoTextSecondary,
                    )
                    Text(
                        "${TimeFormatUtils.formatCompactDuration(genreData.totalSeconds)} recorded",
                        fontSize = 12.sp,
                        color = BentoTextSecondary,
                    )
                }
                TasteRecord(genreData.genres, Modifier.size(88.dp))
            }
            if (genreData.totalTracksTracked < 10) {
                Text(
                    "An early picture of your taste. It becomes more representative as you listen.",
                    fontSize = 13.sp,
                    color = BentoTextSecondary,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TasteSection.entries.forEach { destination ->
                Column(
                    Modifier.weight(1f)
                        .selectable(
                            selected = section == destination,
                            role = Role.Tab,
                            onClick = { section = destination },
                        )
                        .heightIn(min = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        destination.label,
                        Modifier.padding(vertical = 12.dp),
                        color =
                            if (section == destination) BentoPrimary else BentoTextSecondary,
                        fontSize = 16.sp,
                        fontWeight =
                            if (section == destination) FontWeight.Bold else FontWeight.Medium,
                    )
                    Box(
                        Modifier.fillMaxWidth()
                            .height(3.dp)
                            .background(
                                if (section == destination) BentoPrimary else Color.Transparent
                            )
                    )
                }
            }
        }
        when (section) {
            TasteSection.SONGS -> {
                if (topTracks.isNotEmpty())
                    TrackRanking(
                        "On repeat",
                        "Most plays in this period",
                        topTracks,
                        onEditTrackGenre,
                        existingGenres,
                        likedTracks,
                        onTrackLiked,
                    )
                else
                    JournalEmptyState(
                        "No songs in this period",
                        "Choose a longer period to explore your most played songs.",
                    )
                LikedMusicSection(likedTracks, onUnlikeTrack)
            }
            TasteSection.ARTISTS ->
                if (topArtists.isNotEmpty()) ArtistRanking(topArtists)
                else
                    JournalEmptyState(
                        "No artists in this period",
                        "Choose a longer period to explore your favourite artists.",
                    )
            TasteSection.GENRES -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProposalSectionHead(
                        "Listening breakdown",
                        "Top ${minOf(3, genreData.genres.size)} genres",
                    )
                    val share = known.take(3).sumOf { it.percentage.toDouble() }
                    Text(
                        if (known.isEmpty())
                            "Your songs are waiting for genre labels. Tap Unclassified to add them."
                        else
                            "Your top ${minOf(3, known.size)} labelled genres account for ${String.format(Locale.getDefault(), "%.0f%%", share)} of listening time.",
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = BentoTextSecondary,
                    )
                    val preview =
                        genreData.genres.take(3).let { rows ->
                            if (selected != null && selected !in rows) rows + selected else rows
                        }
                    preview.forEach { genre ->
                        val progress by
                            animateFloatAsState(genre.percentage / 100f, label = "genre share")
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected?.genreName == genre.genreName)
                                        BentoHeroContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable(role = Role.Button) {
                                    onGenreSelected(
                                        if (selected?.genreName == genre.genreName) null
                                        else genre.genreName
                                    )
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "${genreData.genres.indexOf(genre) + 1}",
                                    fontSize = 14.sp,
                                    color = BentoTextSecondary,
                                    modifier = Modifier.width(22.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (genre.genreName == "Other") "Unclassified"
                                        else genre.genreName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = BentoTextPrimary,
                                    )
                                    Text(
                                        "${genre.trackCount} songs / ${TimeFormatUtils.formatCompactDuration(genre.totalSeconds)}",
                                        fontSize = 12.sp,
                                        color = BentoTextSecondary,
                                    )
                                }
                                Text(
                                    String.format(
                                        Locale.getDefault(),
                                        "%.1f%%",
                                        genre.percentage,
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = BentoTextPrimary,
                                )
                            }
                            ProposalProgressBar(progress, height = 7.dp, color = genre.color)
                        }
                        if (selected?.genreName == genre.genreName) {
                            UniqueTracksListCard(
                                title = "${genre.genreName} Tracks",
                                helper =
                                    "Ranked by listening time. Tap a song to inspect or label it.",
                                tracks = selectedGenreTracks,
                                onClose = { onGenreSelected(null) },
                                onEditGenre = onEditTrackGenre,
                                existingGenres = existingGenres,
                                likedTracks = likedTracks,
                                onTrackLiked = onTrackLiked,
                                modifier = Modifier.testTag("genre_unique_tracks_card"),
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showAllGenres = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Explore all ${genreData.genres.size} genres")
                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        Modifier.padding(start = 8.dp).size(20.dp),
                    )
                }
                val unknown = genreData.genres.firstOrNull { it.genreName == "Other" }
                if (unknown != null) {
                    Text(
                        "${String.format(Locale.getDefault(), "%.0f", unknown.percentage)}% is unclassified. Open Unclassified to label songs and make this profile more accurate.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = BentoTextSecondary,
                    )
                }
            }
        }
        if (BuildConfig.LASTFM_API_KEY.isNotBlank())
            Text(
                "Genre data provided in part by Last.fm",
                fontSize = 12.sp,
                color = BentoTextSecondary,
            )
    }
    if (showAllGenres) {
        ModalBottomSheet(
            onDismissRequest = { showAllGenres = false },
            containerColor = BentoBackground,
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ProposalTitle("All your genres", "Ranked by listening time in this period.")
                    bounds?.let {
                        Text(
                            periodLabel(it),
                            fontSize = 12.sp,
                            color = BentoTextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(genreData.genres, key = { it.genreName }) { genre ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) {
                                section = TasteSection.GENRES
                                onGenreSelected(genre.genreName)
                                showAllGenres = false
                            }
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                if (genre.genreName == "Other") "Unclassified" else genre.genreName,
                                Modifier.weight(1f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                String.format(Locale.getDefault(), "%.1f%%", genre.percentage),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "${listeningDuration(genre.totalSeconds)} / ${genre.trackCount} songs",
                            fontSize = 13.sp,
                            color = BentoTextSecondary,
                        )
                        ProposalProgressBar(
                            genre.percentage / 100f,
                            height = 6.dp,
                            color = genre.color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TasteRecord(genres: List<GenreSliceData>, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.width * 0.16f
        val inset = stroke / 2
        var angle = -90f
        genres.forEach { genre ->
            val sweep = genre.percentage * 3.6f
            drawArc(
                genre.color,
                angle,
                (sweep - 2f).coerceAtLeast(0f),
                false,
                Offset(inset, inset),
                Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke),
            )
            angle += sweep
        }
        drawCircle(BentoPrimary, size.width * 0.22f)
        drawCircle(Color.White, size.width * 0.055f)
    }
}
