package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DailyListeningView
import com.example.ui.components.GenrePieChartCard
import com.example.ui.components.NowPlayingCard
import com.example.ui.components.PermissionBanner
import com.example.ui.components.SimulationControlStrip
import com.example.ui.components.WeeklyAnalyticsView
import com.example.ui.components.YearlyAnalyticsView
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoHeroAccent
import com.example.ui.theme.BentoHeroContainer
import com.example.ui.theme.BentoHeroOnContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTileBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTrackerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.analyticsState.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(BentoBackground),
        containerColor = BentoBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoHeroContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "App Logo",
                            tint = BentoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AfterTaste",
                            color = BentoTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                        Text(
                            text = "Music Time Tracker",
                            color = BentoTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Action icons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, BentoTileBorder, RoundedCornerShape(12.dp))
                            .clickable { showInfoDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "App Info",
                            tint = BentoTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Permission Request Banner (if listener access not yet granted)
            if (!state.trackerState.isNotificationAccessGranted) {
                PermissionBanner(
                    onGrantPermission = { viewModel.openNotificationListenerSettings(context) }
                )
            }

            // Real-time Now Playing Card
            NowPlayingCard(
                state = state.trackerState,
                onOpenYtMusic = { viewModel.launchYouTubeMusic(context) }
            )

            // Playback Simulator (test tracking without the YT Music app)
            SimulationControlStrip(
                isSimulationActive = state.trackerState.isSimulationActive,
                onToggleSimulation = { viewModel.toggleSimulation() }
            )

            // Bento Tab Navigation: Daily, Weekly, Yearly, Genres
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, BentoTileBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TrackerTab.values().forEach { tab ->
                    val isSelected = state.selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) BentoHeroContainer else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) BentoHeroAccent else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectTab(tab) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (tab) {
                                    TrackerTab.DAILY -> Icons.Default.Today
                                    TrackerTab.WEEKLY -> Icons.Default.DateRange
                                    TrackerTab.YEARLY -> Icons.Default.BarChart
                                    TrackerTab.GENRES -> Icons.Default.PieChart
                                },
                                contentDescription = tab.label,
                                tint = if (isSelected) BentoHeroOnContainer else BentoTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (tab) {
                                    TrackerTab.DAILY -> "Daily"
                                    TrackerTab.WEEKLY -> "Weekly"
                                    TrackerTab.YEARLY -> "Yearly"
                                    TrackerTab.GENRES -> "Genres"
                                },
                                color = if (isSelected) BentoHeroOnContainer else BentoTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Tab Content Switcher
            Crossfade(
                targetState = state.selectedTab,
                label = "tab_crossfade"
            ) { activeTab ->
                when (activeTab) {
                    TrackerTab.DAILY -> {
                        DailyListeningView(
                            state = state,
                            todayTracks = state.todayTrackFeed,
                            onSetDailyGoal = { viewModel.setDailyGoalMinutes(it) }
                        )
                    }
                    TrackerTab.WEEKLY -> {
                        WeeklyAnalyticsView(
                            state = state
                        )
                    }
                    TrackerTab.YEARLY -> {
                        YearlyAnalyticsView(
                            state = state,
                            onSelectYear = { viewModel.selectYear(it) },
                            onSeedData = { viewModel.seedSampleData() }
                        )
                    }
                    TrackerTab.GENRES -> {
                        GenrePieChartCard(
                            genreData = state.genreAnalytics,
                            onScopeSelected = { viewModel.selectGenreScope(it) },
                            onSeedSampleData = { viewModel.seedSampleData() }
                        )
                    }
                }
            }

            // Bottom Safe Inset Spacer
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(24.dp)
            )
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BentoHeroContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Headphones, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("How YT Track Works", color = BentoTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "• Pure Playback Time: We strictly track only the seconds audio is actively playing in YouTube Music.",
                        color = BentoTextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• No App Run Time: When music pauses, or when playback stops, tracking pauses immediately. Foreground or background running time of the app is never counted.",
                        color = BentoTextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• Interactive Analytics: Click any bar in the Weekly or Yearly histograms, or any genre slice in the Pie Chart, to reveal the non-repeating list of unique tracks played.",
                        color = BentoTextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• Official Spotify Genre API: Configure your Spotify Developer API key to unlock strict, official artist genre categorization.",
                        color = BentoTextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got It", color = BentoPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
