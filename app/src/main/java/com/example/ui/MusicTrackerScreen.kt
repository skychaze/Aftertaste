package com.example.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.example.ui.components.PermissionBanner
import com.example.ui.components.WeeklyAnalyticsView
import com.example.ui.components.YearlyAnalyticsView
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoHeroContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTileBorder
import com.example.update.AppUpdateManager

@Composable
fun MusicTrackerScreen(
    viewModel: MainViewModel,
    updateManager: AppUpdateManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.analyticsState.collectAsState()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    var showInfoDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize().background(BentoBackground),
        containerColor = BentoBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                TrackerTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    TrackerTab.DAILY -> Icons.Default.Today
                                    TrackerTab.HISTORY -> Icons.Default.DateRange
                                    TrackerTab.INSIGHTS -> Icons.Default.BarChart
                                    TrackerTab.GENRES -> Icons.Default.PieChart
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "app_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(BentoHeroContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Headphones, null, tint = BentoPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("AfterTaste", color = BentoTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(state.selectedTab.label, color = BentoTextSecondary, fontSize = 12.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                                .border(1.dp, BentoTileBorder, RoundedCornerShape(12.dp))
                                .clickable { showInfoDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, "App info", tint = BentoTextSecondary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        AppUpdateHeaderAction(state = updateState, onClick = { showUpdateDialog = true })
                    }
                }
            }

            if (!state.trackerState.isNotificationAccessGranted) {
                item(key = "permission") {
                    PermissionBanner(onGrantPermission = { viewModel.openNotificationListenerSettings(context) })
                }
            }

            item(key = state.selectedTab.name) {
                Crossfade(targetState = state.selectedTab, label = "destination") { tab ->
                    when (tab) {
                        TrackerTab.DAILY -> DailyListeningView(
                            state = state,
                            onSetDailyGoal = viewModel::setDailyGoalMinutes,
                            onOpenYtMusic = { viewModel.launchYouTubeMusic(context) }
                        )
                        TrackerTab.HISTORY -> WeeklyAnalyticsView(
                            state = state,
                            onRangeSelected = viewModel::selectHistoryRange,
                            onDaySelected = viewModel::selectHistoryDate
                        )
                        TrackerTab.INSIGHTS -> YearlyAnalyticsView(
                            state = state,
                            onSelectYear = viewModel::selectYear,
                            onSeedData = viewModel::seedSampleData
                        )
                        TrackerTab.GENRES -> GenrePieChartCard(
                            genreData = state.genreAnalytics,
                            selectedGenre = state.selectedGenre,
                            selectedGenreTracks = state.selectedGenreTracks,
                            onGenreSelected = viewModel::selectGenre,
                            onEditTrackGenre = viewModel::setTrackGenre,
                            onScopeSelected = viewModel::selectGenreScope,
                            onSeedSampleData = viewModel::seedSampleData
                        )
                    }
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("How tracking works", color = BentoTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "AfterTaste counts playback time while supported music is actively playing. History stays bounded to your selected period, and track details load only when you open a day or genre.",
                    color = BentoTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Got it", color = BentoPrimary) }
            }
        )
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            manager = updateManager,
            onDismiss = { showUpdateDialog = false }
        )
    }
}
