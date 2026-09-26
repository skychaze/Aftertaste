package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.ProposalArtBg
import com.example.ui.theme.ProposalArtIcon
import com.example.ui.theme.ProposalBarTrack
import com.example.ui.theme.ProposalDivider
import com.example.ui.theme.ProposalMuted
import com.example.ui.theme.ProposalPanel
import com.example.ui.theme.ProposalRowValue
import com.example.ui.theme.ProposalSegment
import com.example.ui.theme.ProposalSegmentText
import com.example.ui.theme.ProposalSelectedNav
import com.example.ui.theme.ProposalSub

/** Large tab heading used across Today, History, Insights, and Genres. */
@Composable
fun ProposalTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    status: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = BentoTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )
            status?.invoke()
        }
        Text(subtitle, color = ProposalSub, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

/**
 * Hero duration total with smaller unit suffixes, e.g. 42m 18s or 18h 40m.
 * Rendered as one Text node so the full string stays searchable in tests.
 */
@Composable
fun ProposalTotal(
    text: String,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 56.sp,
    unitSize: TextUnit = 25.sp,
    color: Color = BentoTextPrimary,
    unitColor: Color = ProposalMuted
) {
    val styled = remember(text, numberSize, unitSize) {
        buildAnnotatedString {
            text.split(" ").forEachIndexed { index, token ->
                if (index > 0) append(" ")
                val digits = token.takeWhile { it.isDigit() || it == '<' }
                val unit = token.drop(digits.length)
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontSize = numberSize,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-2).sp,
                        color = color
                    )
                ) { append(digits) }
                if (unit.isNotEmpty()) {
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontSize = unitSize,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.5).sp,
                            color = unitColor
                        )
                    ) { append(unit) }
                }
            }
        }
    }
    Text(styled, modifier = modifier, lineHeight = numberSize)
}

/** Thin rounded progress bar over the proposal track color. */
@Composable
fun ProposalProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 7.dp,
    color: Color = BentoPrimary
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)),
        color = color,
        trackColor = ProposalBarTrack,
        drawStopIndicator = {}
    )
}

/**
 * Segmented pill control: muted track, white chosen segment.
 * Options share one row; pass [optionTestTag] for stable test handles.
 */
@Composable
fun ProposalSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTag: ((Int) -> String)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(ProposalSegment)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selectedIndex
            val shape = RoundedCornerShape(10.dp)
            var box = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .then(if (chosen) Modifier.shadow(2.dp, shape) else Modifier)
                .clip(shape)
                .background(if (chosen) Color.White else Color.Transparent)
                .clickable { onSelect(index) }
            val tag = optionTestTag?.invoke(index)
            if (tag != null) box = box.testTag(tag)
            Box(
                modifier = box,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    color = if (chosen) ProposalSelectedNav else ProposalSegmentText,
                    fontSize = 12.sp,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Section heading with a trailing count, e.g. Today's tracks + 12 tracks. */
@Composable
fun ProposalSectionHead(
    title: String,
    count: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = BentoTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp
        )
        Text(count, color = ProposalSub, fontSize = 12.sp)
    }
}

/**
 * Slim divider-separated track row from the proposal: artwork, title/artist,
 * duration with a play-count line underneath.
 */
@Composable
fun SlimTrackRow(
    title: String,
    artist: String,
    duration: String,
    plays: String,
    modifier: Modifier = Modifier,
    artworkUrl: String? = null,
    artColor: Color = ProposalArtIcon,
    onClick: (() -> Unit)? = null
) {
    var row = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
    if (onClick != null) row = row.clickable(onClick = onClick)
    Row(
        modifier = row.padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(39.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (artworkUrl.isNullOrBlank()) ProposalArtBg else artColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(39.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = artColor, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = BentoTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(artist, color = ProposalSub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(duration, color = ProposalRowValue, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text(plays, color = ProposalMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

/** Flat detail panel that opens below a breakdown, e.g. Pop tracks or a day's tracks. */
@Composable
fun ProposalDetailPanel(
    title: String,
    helper: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProposalPanel)
            .padding(horizontal = 12.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = BentoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            TextButton(
                onClick = onClose,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("Close", color = BentoPrimary, fontSize = 12.sp)
            }
        }
        Text(helper, color = ProposalSub, fontSize = 11.sp)
        content()
    }
}

/** Divider used between slim rows. */
@Composable
fun ProposalDividerLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(ProposalDivider))
}
