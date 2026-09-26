package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GenreAnalyticsData
import com.example.BuildConfig
import com.example.ui.GenreScope
import com.example.ui.GenreSliceData
import com.example.ui.UniqueTrackItem
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.ProposalPanel
import com.example.ui.theme.ProposalRowValue
import com.example.ui.theme.ProposalSub
import com.example.util.TimeFormatUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2

/**
 * Genres tab from the design proposal: segmented scope, total with a small
 * donut, full-width genre rows with proportion bars, and the selected genre's
 * tracks in a panel below the breakdown.
 */
@Composable
fun GenrePieChartCard(
    genreData: GenreAnalyticsData,
    selectedGenre: String? = null,
    selectedGenreTracks: List<UniqueTrackItem> = emptyList(),
    onGenreSelected: (String?) -> Unit = {},
    onEditTrackGenre: (UniqueTrackItem, String) -> Unit = { _, _ -> },
    onScopeSelected: (GenreScope) -> Unit,
    modifier: Modifier = Modifier,
    onSeedSampleData: (() -> Unit)? = null
) {
    val selectedGenreData = remember(genreData, selectedGenre) {
        genreData.genres.firstOrNull { it.genreName == selectedGenre }
    }
    val activeGenre = selectedGenreData ?: genreData.genres.firstOrNull()

    Column(
        modifier = modifier.fillMaxWidth().background(BentoBackground).testTag("genre_analytics_card"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ProposalTitle(
            title = "Your genres",
            subtitle = "See where your listening time goes"
        )

        ProposalSegmentedControl(
            options = GenreScope.entries.map { scope ->
                when (scope) {
                    GenreScope.MONTH -> "This month"
                    GenreScope.YEAR -> "This year"
                    GenreScope.ALL_TIME -> "All time"
                }
            },
            selectedIndex = GenreScope.entries.indexOf(genreData.scope),
            onSelect = { onScopeSelected(GenreScope.entries[it]) }
        )

        if (genreData.genres.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = TimeFormatUtils.formatCompactDuration(genreData.totalSeconds),
                        color = BentoTextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = when (genreData.scope) {
                            GenreScope.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                            GenreScope.YEAR -> "${Calendar.getInstance().get(Calendar.YEAR)}"
                            GenreScope.ALL_TIME -> "Across all records"
                        },
                        color = ProposalSub,
                        fontSize = 12.sp
                    )
                }
                MiniDonut(
                    genres = genreData.genres,
                    selectedGenre = activeGenre,
                    onSelectGenre = { onGenreSelected(it.genreName) },
                    modifier = Modifier.size(86.dp)
                )
            }
        }

        if (BuildConfig.LASTFM_API_KEY.isNotBlank() && genreData.genres.isNotEmpty()) {
            Text("Genre data provided in part by Last.fm", color = BentoTextMuted, fontSize = 11.sp)
        }

        if (genreData.genres.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProposalPanel)
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No genre history yet",
                    color = BentoTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Play songs in YouTube Music or load sample data to explore genre breakdowns.",
                    color = BentoTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (onSeedSampleData != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onSeedSampleData,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary)
                    ) {
                        Text("Load Sample Genre Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ProposalSectionHead(
                    title = "Listening breakdown",
                    count = "${genreData.genres.size} ${if (genreData.genres.size == 1) "genre" else "genres"}"
                )
                Text("Tap a genre to see its tracks", color = BentoTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                genreData.genres.forEachIndexed { index, genre ->
                    if (index > 0) ProposalDividerLine()
                    val isSelected = activeGenre?.genreName == genre.genreName
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onGenreSelected(genre.genreName) }
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = genre.genreName,
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                            Text(
                                text = "${TimeFormatUtils.formatCompactDuration(genre.totalSeconds)}  " +
                                    String.format(Locale.US, "%.1f%%", genre.percentage),
                                color = ProposalRowValue,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        ProposalProgressBar(
                            progress = (genre.percentage / 100f).coerceIn(0f, 1f),
                            height = 5.dp,
                            color = genre.color
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedGenreData != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedGenreData?.let { genre ->
                    UniqueTracksListCard(
                        title = "${genre.genreName} Tracks",
                        helper = "Most listened first. Tap a track to edit its genre.",
                        tracks = selectedGenreTracks,
                        onClose = { onGenreSelected(null) },
                        editable = true,
                        onEditGenre = onEditTrackGenre,
                        modifier = Modifier.testTag("genre_unique_tracks_card")
                    )
                }
            }
        }
    }
}

/** Small ring chart; tapping a slice selects that genre. */
@Composable
private fun MiniDonut(
    genres: List<GenreSliceData>,
    selectedGenre: GenreSliceData?,
    onSelectGenre: (GenreSliceData) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(genres) {
            detectTapGestures { offset ->
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val dx = offset.x - centerX
                val dy = offset.y - centerY
                val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val radius = size.width / 2f
                if (distance in (radius * 0.45f)..(radius * 1.25f)) {
                    var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    touchAngle = (touchAngle + 90f + 360f) % 360f
                    var accumulatedAngle = 0f
                    for (genre in genres) {
                        val sweepAngle = (genre.percentage / 100f) * 360f
                        if (touchAngle in accumulatedAngle..(accumulatedAngle + sweepAngle)) {
                            onSelectGenre(genre)
                            break
                        }
                        accumulatedAngle += sweepAngle
                    }
                }
            }
        }
    ) {
        val strokeDefault = size.width * 0.16f
        val strokeSelected = size.width * 0.21f
        val diameter = size.width - strokeSelected
        val topLeft = Offset(strokeSelected / 2f, strokeSelected / 2f)
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f
        genres.forEach { genre ->
            val sweepAngle = (genre.percentage / 100f) * 360f
            val gapAngle = if (genres.size > 1 && sweepAngle > 4f) 2.5f else 0f
            val actualSweep = (sweepAngle - gapAngle).coerceAtLeast(1f)
            val isSelected = selectedGenre?.genreName == genre.genreName
            drawArc(
                color = if (isSelected) genre.color else genre.color.copy(alpha = 0.88f),
                startAngle = startAngle + (gapAngle / 2f),
                sweepAngle = actualSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = if (isSelected) strokeSelected else strokeDefault,
                    cap = StrokeCap.Round
                )
            )
            startAngle += sweepAngle
        }
    }
}
