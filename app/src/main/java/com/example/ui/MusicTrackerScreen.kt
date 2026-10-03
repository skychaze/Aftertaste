package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.AppUpdateHeaderAction
import com.example.ui.components.DailyListeningView
import com.example.ui.components.GenrePieChartCard
import com.example.ui.components.HistoryHeader
import com.example.ui.components.MiniPlaybackBar
import com.example.ui.components.PermissionBanner
import com.example.ui.components.RecordMark
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.components.YearlyAnalyticsView
import com.example.ui.components.historyTrackItems
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.JournalBackground
import com.example.ui.theme.JournalNavigation
import com.example.ui.theme.JournalNavigationMuted
import com.example.ui.theme.JournalPeach
import com.example.update.AppUpdateManager

@Composable
fun MusicTrackerScreen(
    viewModel: MainViewModel,
    updateManager: AppUpdateManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.analyticsState.collectAsState()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    var selectedTrack by remember { mutableStateOf<UniqueTrackItem?>(null) }
    val scrollStates = TrackerTab.entries.map { rememberLazyListState() }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize().background(JournalBackground),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                if (
                    state.selectedTab != TrackerTab.DAILY &&
                        state.trackerState.playbackControls.canPlayPause
                ) {
                    MiniPlaybackBar(
                        state.trackerState,
                        viewModel::sendPlaybackCommand,
                        { viewModel.selectTab(TrackerTab.DAILY) },
                    )
                }
                NavigationBar(
                    containerColor = JournalNavigation,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                ) {
                    TrackerTab.entries.forEach { tab ->
                        val selected = state.selectedTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector =
                                        when (tab) {
                                            TrackerTab.DAILY ->
                                                if (selected) Icons.Filled.Headphones
                                                else Icons.Outlined.Headphones
                                            TrackerTab.HISTORY ->
                                                if (selected) Icons.AutoMirrored.Filled.QueueMusic
                                                else Icons.AutoMirrored.Outlined.QueueMusic
                                            TrackerTab.INSIGHTS ->
                                                if (selected) Icons.Filled.Equalizer
                                                else Icons.Outlined.Equalizer
                                            TrackerTab.GENRES ->
                                                if (selected) Icons.Filled.Album
                                                else Icons.Outlined.Album
                                        },
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 12.sp,
                                    fontWeight =
                                        if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = JournalNavigation,
                                    selectedTextColor = JournalPeach,
                                    indicatorColor = JournalPeach,
                                    unselectedIconColor = JournalNavigationMuted,
                                    unselectedTextColor = JournalNavigationMuted,
                                ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = scrollStates[state.selectedTab.ordinal],
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp,
                    vertical = 8.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(if (state.selectedTab == TrackerTab.HISTORY) 8.dp else 20.dp),
        ) {
            item(key = "app_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier.size(35.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BentoPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            RecordMark(Modifier.size(29.dp), Color.White)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "AfterTaste",
                            color = BentoTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier =
                                Modifier.size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.7f)),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.HelpOutline,
                                "App info",
                                tint = BentoPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        AppUpdateHeaderAction(
                            state = updateState,
                            onClick = { showUpdateDialog = true },
                        )
                    }
                }
            }

            if (!state.trackerState.isNotificationAccessGranted) {
                item(key = "permission") {
                    PermissionBanner(
                        onGrantPermission = { viewModel.openNotificationListenerSettings(context) }
                    )
                }
            }

            if (state.selectedTab == TrackerTab.HISTORY) {
                item(key = "history_header") {
                    HistoryHeader(
                        state,
                        viewModel::selectHistoryRange,
                        viewModel::selectHistoryDate,
                        viewModel::setHistoryQuery,
                        viewModel::selectHistorySort,
                    )
                }
                historyTrackItems(state, { selectedTrack = it }, viewModel::loadMoreHistory)
            } else {
                item(key = state.selectedTab.name) {
                    Crossfade(targetState = state.selectedTab, label = "destination") { tab ->
                        when (tab) {
                            TrackerTab.DAILY ->
                                DailyListeningView(
                                    state = state,
                                    onSetDailyGoal = viewModel::setDailyGoalMinutes,
                                    onOpenYtMusic = { viewModel.launchYouTubeMusic(context) },
                                    onPlaybackCommand = viewModel::sendPlaybackCommand,
                                    onTrackLiked = viewModel::setTrackLiked,
                                )
                            TrackerTab.HISTORY -> Unit
                            TrackerTab.INSIGHTS ->
                                YearlyAnalyticsView(
                                    state,
                                    viewModel::selectYear,
                                    viewModel::seedSampleData,
                                )
                            TrackerTab.GENRES ->
                                GenrePieChartCard(
                                    genreData = state.genreAnalytics,
                                    selectedGenre = state.selectedGenre,
                                    selectedGenreTracks = state.selectedGenreTracks,
                                    onGenreSelected = viewModel::selectGenre,
                                    onEditTrackGenre = viewModel::setTrackGenre,
                                    onScopeSelected = viewModel::selectGenreScope,
                                    onSeedSampleData = viewModel::seedSampleData,
                                    topTracks = state.tasteTracks,
                                    topArtists = state.tasteArtists,
                                    bounds = state.tasteBounds,
                                    likedTracks = state.likedTracks,
                                    onUnlikeTrack = { viewModel.setTrackLiked(it.title, it.artist, it.artworkUrl, false) },
                                )
                        }
                    }
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
        }
    }

    selectedTrack?.let {
        TrackDetailsDialog(it, { selectedTrack = null }, viewModel::setTrackGenre)
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text("How tracking works", color = BentoTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "AfterTaste records your listening from music notifications and saves your history on this device. Playback controls connect to your music app. Genres are estimates; tap a song to correct its label.",
                    color = BentoTextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it", color = BentoPrimary)
                }
            },
        )
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            manager = updateManager,
            onDismiss = { showUpdateDialog = false },
        )
    }
}
