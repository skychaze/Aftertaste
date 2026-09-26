package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GenreAnalyticsData
import com.example.BuildConfig
import com.example.ui.GenreScope
import com.example.ui.GenreSliceData
import com.example.ui.UniqueTrackItem
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
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.atan2

/**
 * Modern Bento-styled interactive Pie / Donut Chart component for music genre analytics.
 * - Displays genre distribution.
 * - Clicking a genre slice displays a filtered, non-repeating list of unique tracks within that genre.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("genre_analytics_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
            border = BorderStroke(1.dp, BentoTileBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = "Genre Distribution",
                                tint = BentoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Your genres",
                                color = BentoTextSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "See where your listening time goes",
                                color = BentoTextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(BentoTileBg).padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        GenreScope.values().forEach { scope ->
                            val isSelected = scope == genreData.scope
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(if (isSelected) BentoPrimary else Color.Transparent)
                                    .clickable { onScopeSelected(scope) }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (scope) {
                                        GenreScope.MONTH -> "This month"
                                        GenreScope.YEAR -> "This year"
                                        GenreScope.ALL_TIME -> "All time"
                                    },
                                    color = if (isSelected) Color.White else BentoTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (genreData.genres.isNotEmpty()) {
                    Text(
                        text = TimeFormatUtils.formatCompactDuration(genreData.totalSeconds),
                        color = BentoTextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = when (genreData.scope) {
                            GenreScope.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                            GenreScope.YEAR -> "${Calendar.getInstance().get(Calendar.YEAR)}"
                            GenreScope.ALL_TIME -> "Across all records"
                        },
                        color = BentoTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (BuildConfig.LASTFM_API_KEY.isNotBlank()) {
                    Text("Genre data provided in part by Last.fm", color = BentoTextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (genreData.genres.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BentoTileBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = BentoTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Genre History Yet",
                            color = BentoTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Play songs in YouTube Music or load sample data to explore genre breakdowns.",
                            color = BentoTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                        if (onSeedSampleData != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BentoHeroContainer)
                                    .clickable { onSeedSampleData() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Load Sample Genre Data",
                                    color = BentoHeroOnContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // Interactive Donut Chart & Center Metric Display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DonutPieChart(
                            genres = genreData.genres,
                            selectedGenre = activeGenre,
                            onSelectGenre = { genre ->
                                onGenreSelected(genre.genreName)
                            },
                            modifier = Modifier.size(132.dp)
                        )

                        // Center information card inside the donut hole
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(110.dp)
                                .padding(4.dp)
                        ) {
                            if (activeGenre != null) {
                                Text(
                                    text = activeGenre.genreName,
                                    color = activeGenre.color,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", activeGenre.percentage),
                                    color = BentoTextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = TimeFormatUtils.formatCompactDuration(activeGenre.totalSeconds),
                                    color = BentoTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "${genreData.genres.size} Genres",
                                    color = BentoTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap a slice",
                                    color = BentoTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Genre Ranking List with Visual Proportion Bars
                    Text(
                        text = "Listening breakdown",
                        color = BentoTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Tap a genre to see its tracks", color = BentoTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        genreData.genres.forEach { genre ->
                            val isSelected = activeGenre?.genreName == genre.genreName
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable { onGenreSelected(genre.genreName) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) BentoHeroContainer.copy(alpha = 0.45f) else BentoSurfaceCard
                                ),
                                border = BorderStroke(1.dp, if (isSelected) genre.color.copy(alpha = 0.4f) else BentoTileBg)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(genre.color)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = genre.genreName,
                                                color = BentoTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = TimeFormatUtils.formatCompactDuration(genre.totalSeconds),
                                                color = BentoTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(genre.color.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = String.format(Locale.US, "%.1f%%", genre.percentage),
                                                    color = genre.color,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { (genre.percentage / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = genre.color,
                                        trackColor = BentoTileBorder.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
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
                    subtitle = if (selectedGenreTracks.size == 100) "Top 100 tracks by listening time" else "Tracks by listening time",
                    tracks = selectedGenreTracks,
                    onClose = { onGenreSelected(null) },
                    onEditGenre = onEditTrackGenre,
                    modifier = Modifier.testTag("genre_unique_tracks_card")
                )
            }
        }

    }
}

/**
 * Canvas-based Donut Pie Chart with touch selection and responsive slice highlighting.
 */
@Composable
private fun DonutPieChart(
    genres: List<GenreSliceData>,
    selectedGenre: GenreSliceData?,
    onSelectGenre: (GenreSliceData) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .pointerInput(genres) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val dx = offset.x - centerX
                    val dy = offset.y - centerY
                    val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                    val radius = size.width / 2f
                    val innerRadius = radius * 0.58f

                    // Select if touch fell inside the ring
                    if (distance in (innerRadius * 0.75f)..(radius * 1.25f)) {
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
        val strokeWidthDefault = size.width * 0.16f
        val strokeWidthSelected = size.width * 0.20f
        val diameter = size.width - strokeWidthSelected
        val topLeft = Offset(strokeWidthSelected / 2f, strokeWidthSelected / 2f)
        val arcSize = Size(diameter, diameter)

        var startAngle = -90f

        genres.forEach { genre ->
            val sweepAngle = (genre.percentage / 100f) * 360f
            val gapAngle = if (genres.size > 1 && sweepAngle > 4f) 2.5f else 0f
            val actualSweep = (sweepAngle - gapAngle).coerceAtLeast(1f)
            val isSelected = selectedGenre?.genreName == genre.genreName

            val currentStrokeWidth = if (isSelected) strokeWidthSelected else strokeWidthDefault
            val sliceColor = if (isSelected) genre.color else genre.color.copy(alpha = 0.88f)

            drawArc(
                color = sliceColor,
                startAngle = startAngle + (gapAngle / 2f),
                sweepAngle = actualSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = currentStrokeWidth,
                    cap = StrokeCap.Round
                )
            )

            startAngle += sweepAngle
        }
    }
}
