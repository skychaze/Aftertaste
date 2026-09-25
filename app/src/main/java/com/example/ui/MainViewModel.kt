package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.YTTrackerApplication
import com.example.data.DailyStatEntity
import com.example.data.GenreAggregateRow
import com.example.data.PlaybackSessionDurations
import com.example.data.PlaybackSessionEntity
import com.example.data.TrackAggregateRow
import com.example.tracker.ArtworkResolver
import com.example.tracker.GenreClassifier
import com.example.tracker.TrackerUiState
import com.example.tracker.YouTubeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TrackerTab(val label: String) {
    DAILY("Today"), HISTORY("History"), INSIGHTS("Insights"), GENRES("Genres")
}

enum class HistoryRange(val days: Int, val label: String) {
    SEVEN_DAYS(7, "7 days"), THIRTY_DAYS(30, "30 days"), NINETY_DAYS(90, "90 days")
}

enum class GenreScope(val label: String) {
    MONTH("This month"), YEAR("This year"), ALL_TIME("All time")
}

data class UniqueTrackItem(
    val title: String,
    val artist: String,
    val album: String?,
    val genre: String,
    val artworkUrl: String? = null,
    val totalSeconds: Long,
    val playCount: Int,
    val lastPlayedTimestamp: Long
)

data class TodayTrackFeedItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val genre: String,
    val artworkUrl: String? = null,
    val durationSeconds: Long,
    val timestamp: Long,
    val isActivelyPlaying: Boolean = false,
    val playCount: Int = 1,
    val pastDurationSeconds: Long = 0L
)

data class DayChartItem(
    val dateStr: String,
    val dayLabel: String,
    val dayNumber: Int,
    val seconds: Long,
    val minutes: Int,
    val isToday: Boolean,
    val uniqueTracks: List<UniqueTrackItem> = emptyList()
)

data class MonthChartItem(
    val monthNumber: Int,
    val monthName: String,
    val totalSeconds: Long,
    val totalHours: Float,
    val activeDays: Int,
    val isCurrentMonth: Boolean
)

data class Milestone(
    val title: String,
    val requiredHours: Int,
    val description: String,
    val isUnlocked: Boolean,
    val progressFraction: Float
)

data class GenreSliceData(
    val genreName: String,
    val totalSeconds: Long,
    val totalMinutes: Int,
    val percentage: Float,
    val trackCount: Int,
    val color: Color,
    val topArtists: List<String> = emptyList(),
    val uniqueTracks: List<UniqueTrackItem> = emptyList()
)

data class GenreAnalyticsData(
    val scope: GenreScope = GenreScope.MONTH,
    val genres: List<GenreSliceData> = emptyList(),
    val totalSeconds: Long = 0L,
    val dominantGenre: String = "-",
    val dominantGenrePercentage: Float = 0f,
    val totalTracksTracked: Int = 0
)

data class AnalyticsUiState(
    val selectedTab: TrackerTab = TrackerTab.DAILY,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val availableYears: List<Int> = listOf(Calendar.getInstance().get(Calendar.YEAR)),
    val trackerState: TrackerUiState = TrackerUiState(),
    val todayTrackFeed: List<TodayTrackFeedItem> = emptyList(),
    val past7Days: List<DayChartItem> = emptyList(),
    val weekAverageMinutes: Int = 0,
    val historyRange: HistoryRange = HistoryRange.SEVEN_DAYS,
    val selectedHistoryDate: String? = null,
    val selectedDayTracks: List<UniqueTrackItem> = emptyList(),
    val yearTotalSeconds: Long = 0L,
    val yearActiveDays: Int = 0,
    val yearAverageMinutesPerDay: Int = 0,
    val monthlyBreakdown: List<MonthChartItem> = emptyList(),
    val peakMonthName: String = "-",
    val peakMonthHours: Float = 0f,
    val milestones: List<Milestone> = emptyList(),
    val currentStreakDays: Int = 0,
    val genreAnalytics: GenreAnalyticsData = GenreAnalyticsData(),
    val genreScope: GenreScope = GenreScope.MONTH,
    val selectedGenre: String? = null,
    val selectedGenreTracks: List<UniqueTrackItem> = emptyList()
)

data class DateBounds(val startDate: String, val endDate: String) {
    operator fun contains(date: String): Boolean = date >= startDate && date <= endDate
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as YTTrackerApplication
    private val repository = app.repository
    private val engine = app.trackerEngine
    private val _selectedTab = MutableStateFlow(TrackerTab.DAILY)
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _yearPinnedByUser = MutableStateFlow(false)
    private val _currentDate = MutableStateFlow(currentDate())
    private val _historyRange = MutableStateFlow(HistoryRange.SEVEN_DAYS)
    private val _selectedHistoryDate = MutableStateFlow<String?>(null)
    private val _genreScope = MutableStateFlow(GenreScope.MONTH)
    private val _selectedGenre = MutableStateFlow<String?>(null)
    private val _analyticsState = MutableStateFlow(AnalyticsUiState())
    val analyticsState: StateFlow<AnalyticsUiState> = _analyticsState.asStateFlow()
    @Volatile private var isSeedingSampleData = false

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                _currentDate.value = currentDate()
            }
        }
        viewModelScope.launch {
            _currentDate.collect { date ->
                if (!_yearPinnedByUser.value) {
                    _selectedYear.value = date.substringBefore('-').toInt()
                }
            }
        }
        observeTracker()
        observeActiveSurface()
    }

    private fun observeTracker() {
        viewModelScope.launch {
            engine.uiState.collect { trackerState ->
                val liveSeconds = engine.getCurrentSessionSecondsForDate(currentDate())
                _analyticsState.value = _analyticsState.value.let { state ->
                    state.copy(
                        trackerState = trackerState,
                        todayTrackFeed = if (state.selectedTab == TrackerTab.DAILY) {
                            state.todayTrackFeed.map { item ->
                                if (item.isActivelyPlaying) {
                                    item.copy(durationSeconds = item.pastDurationSeconds + liveSeconds)
                                } else item
                            }
                        } else state.todayTrackFeed
                    )
                }
            }
        }
    }

    private fun observeActiveSurface() {
        viewModelScope.launch {
            _selectedTab.flatMapLatest { tab ->
                when (tab) {
                    TrackerTab.DAILY -> todaySurface()
                    TrackerTab.HISTORY -> historySurface()
                    TrackerTab.INSIGHTS -> insightsSurface()
                    TrackerTab.GENRES -> genresSurface()
                }
            }.flowOn(Dispatchers.Default).collect { update -> update() }
        }
    }

    private fun todaySurface(): Flow<() -> Unit> = _currentDate.flatMapLatest { today ->
        repository.getSessionsOverlappingRange(today, today, parseDate(today).time).map { sessions ->
            val feed = buildTodayFeed(today, sessions, engine.uiState.value)
            val update: () -> Unit = {
                _analyticsState.value = _analyticsState.value.copy(todayTrackFeed = feed)
            }
            update
        }
    }

    private fun historySurface(): Flow<() -> Unit> =
        combine(_historyRange, _selectedHistoryDate, _currentDate) { range, date, today ->
            Triple(range, date, today)
        }.flatMapLatest { (range, selectedDate, today) ->
                val bounds = historyBounds(range, today)
                val details = selectedDate?.let {
                    repository.getSessionsOverlappingRange(it, it, parseDate(it).time)
                } ?: flowOf(emptyList())
                combine(repository.getDailyStatsBetween(bounds.startDate, bounds.endDate), details) { stats, sessions ->
                    val days = buildHistoryDays(bounds, stats)
                    val tracks = selectedDate?.let { buildUniqueTracks(sessions, it) }.orEmpty()
                    val average = if (days.isEmpty()) 0 else (days.sumOf { it.seconds } / 60L / days.size).toInt()
                    val update: () -> Unit = {
                        _analyticsState.value = _analyticsState.value.copy(
                            past7Days = days,
                            weekAverageMinutes = average,
                            historyRange = range,
                            selectedHistoryDate = selectedDate,
                            selectedDayTracks = tracks
                        )
                    }
                    update
                }
            }

    private fun insightsSurface(): Flow<() -> Unit> = combine(_selectedYear, _currentDate) { year, today ->
        year to today
    }.flatMapLatest { (year, today) ->
        combine(
            repository.getDailyStatsForYear(year),
            repository.getAvailableYears(),
            repository.getCurrentStreak(today, dateDaysAgo(1, today))
        ) { stats, years, streak ->
            val summary = buildYearSummary(year, stats, streak)
            val update: () -> Unit = {
                _analyticsState.value = _analyticsState.value.copy(
                    selectedYear = year,
                    availableYears = (years + Calendar.getInstance().get(Calendar.YEAR)).distinct().sortedDescending(),
                    yearTotalSeconds = summary.totalSeconds,
                    yearActiveDays = summary.activeDays,
                    yearAverageMinutesPerDay = summary.averageMinutes,
                    monthlyBreakdown = summary.months,
                    peakMonthName = summary.peakMonthName,
                    peakMonthHours = summary.peakMonthHours,
                    milestones = summary.milestones,
                    currentStreakDays = summary.streak
                )
            }
            update
        }
    }

    private fun genresSurface(): Flow<() -> Unit> = combine(_genreScope, _currentDate) { scope, today ->
        scope to today
    }.flatMapLatest { (scope, today) ->
        val calendar = Calendar.getInstance().apply {
            time = parseDate(today)
        }
        val bounds = genreBounds(scope, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
        val boundarySessions = if (scope == GenreScope.ALL_TIME) {
            flowOf(emptyList())
        } else {
            val rangeStart = parseDate(bounds.startDate).time
            val rangeEnd = Calendar.getInstance().apply {
                time = parseDate(bounds.endDate)
                add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            repository.getSessionsCrossingRange(rangeStart, rangeEnd)
        }
        val analytics = repository.getGenreAggregates(bounds.startDate, bounds.endDate)
        val selectedTracks = _selectedGenre.flatMapLatest { genre ->
            if (genre == null) {
                flowOf(null to emptyList<UniqueTrackItem>())
            } else {
                boundarySessions.flatMapLatest { sessions ->
                    val scopedSessions = validBoundarySessions(sessions, bounds)
                    val limit = GenrePeriodAdjustments.topTracksQueryLimit(scopedSessions, genre, bounds)
                    repository.getTopTracksForGenre(bounds.startDate, bounds.endDate, genre, limit)
                        .mapLatest { rows ->
                            val tracks = if (scope == GenreScope.ALL_TIME) {
                                rows.map(::mapTrackAggregate)
                            } else {
                                val trackBases = queryBoundaryTrackAggregates(
                                    scopedSessions.filter { it.genre == genre },
                                    bounds
                                )
                                GenrePeriodAdjustments.adjustGenreTracks(
                                    rows,
                                    scopedSessions,
                                    genre,
                                    bounds,
                                    trackBases
                                ).map(::mapTrackAggregate)
                            }
                            genre to tracks
                        }
                }
            }
        }
        combine(analytics, boundarySessions, selectedTracks) { rows, sessions, selection ->
            Triple(rows, sessions, selection)
        }.mapLatest { (rows, sessions, selection) ->
            val (genre, tracks) = selection
            val adjustedRows = if (scope == GenreScope.ALL_TIME) rows else {
                val scopedSessions = validBoundarySessions(sessions, bounds)
                val trackBases = queryBoundaryTrackAggregates(scopedSessions, bounds)
                GenrePeriodAdjustments.adjustGenreAggregates(
                    rows,
                    scopedSessions,
                    bounds,
                    trackBases
                )
            }
            val analyticsState = buildGenreAnalytics(scope, adjustedRows)
            val update: () -> Unit = {
                _analyticsState.value = _analyticsState.value.copy(
                    genreAnalytics = analyticsState,
                    genreScope = scope,
                    selectedGenre = genre,
                    selectedGenreTracks = tracks
                )
            }
            update
        }
    }

    private fun buildTodayFeed(
        date: String,
        rawSessions: List<PlaybackSessionEntity>,
        trackerState: TrackerUiState
    ): List<TodayTrackFeedItem> {
        val sessions = rawSessions.filter {
            !YouTubeHelper.isYouTubeVideoPackage(it.sourcePackage) && !engine.isPlaceholderTitle(it.title) &&
                (PlaybackSessionDurations.durationForDate(it, date) >= 5L || it.id == engine.getCurrentDbSessionId())
        }
        val grouped = sessions.groupBy { normalizedTrackKey(it.title, it.artist) }.map { (key, group) ->
            val first = group.first()
            val current = trackerState.isActivelyPlaying && (
                group.any { it.id == engine.getCurrentDbSessionId() } ||
                    key == normalizedTrackKey(trackerState.trackTitle, trackerState.artist)
                )
            val pastSeconds = group.filter { it.id != engine.getCurrentDbSessionId() }
                .sumOf { PlaybackSessionDurations.durationForDate(it, date) }
            TodayTrackFeedItem(
                id = first.id,
                title = first.title ?: "Unknown track",
                artist = cleanArtist(first.artist),
                album = first.album,
                genre = group.firstNotNullOfOrNull { it.genre?.takeIf(String::isNotBlank) }
                    ?: GenreClassifier.classify(first.artist, first.title, first.album),
                artworkUrl = group.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) }
                    ?: if (current) trackerState.artworkUrl else null,
                durationSeconds = if (current) pastSeconds + engine.getCurrentSessionSecondsForDate(date)
                    else group.sumOf { PlaybackSessionDurations.durationForDate(it, date) },
                timestamp = group.maxOf { it.startTime },
                isActivelyPlaying = current,
                playCount = group.sumOf { it.playCount },
                pastDurationSeconds = pastSeconds
            )
        }
        val activeKey = normalizedTrackKey(trackerState.trackTitle, trackerState.artist)
        val withLive = if (
            trackerState.isActivelyPlaying && !engine.isPlaceholderTitle(trackerState.trackTitle) &&
            grouped.none { it.isActivelyPlaying }
        ) {
            listOf(
                TodayTrackFeedItem(
                    id = -1L,
                    title = trackerState.trackTitle,
                    artist = cleanArtist(trackerState.artist),
                    album = trackerState.album,
                    genre = trackerState.currentGenre,
                    artworkUrl = trackerState.artworkUrl ?: ArtworkResolver.getCachedArtwork(
                        getApplication(), cleanArtist(trackerState.artist), trackerState.trackTitle
                    ),
                    durationSeconds = engine.getCurrentSessionSecondsForDate(date),
                    timestamp = System.currentTimeMillis(),
                    isActivelyPlaying = true
                )
            ) + grouped
        } else grouped
        return withLive.sortedWith(
            compareByDescending<TodayTrackFeedItem> {
                it.isActivelyPlaying || normalizedTrackKey(it.title, it.artist) == activeKey
            }.thenByDescending { it.timestamp }
        ).take(30)
    }

    private fun buildHistoryDays(bounds: DateBounds, stats: List<DailyStatEntity>): List<DayChartItem> {
        val byDate = stats.associateBy { it.date }
        val dayLabel = SimpleDateFormat("EEE", Locale.getDefault())
        val calendar = Calendar.getInstance().apply { time = parseDate(bounds.startDate) }
        val end = parseDate(bounds.endDate)
        return buildList {
            while (!calendar.time.after(end)) {
                val date = formatDate(calendar.time)
                val seconds = byDate[date]?.totalPlayTimeSeconds ?: 0L
                add(
                    DayChartItem(
                        date, dayLabel.format(calendar.time), calendar.get(Calendar.DAY_OF_MONTH),
                        seconds, (seconds / 60L).toInt(), date == bounds.endDate
                    )
                )
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }.asReversed()
    }

    private fun buildUniqueTracks(rawSessions: List<PlaybackSessionEntity>, date: String): List<UniqueTrackItem> =
        rawSessions.filter {
            !YouTubeHelper.isYouTubeVideoPackage(it.sourcePackage) && !engine.isPlaceholderTitle(it.title) &&
                PlaybackSessionDurations.durationForDate(it, date) >= 5L
        }.groupBy { normalizedTrackKey(it.title, it.artist) }.map { (_, sessions) ->
            val first = sessions.first()
            UniqueTrackItem(
                title = first.title ?: "Unknown track",
                artist = cleanArtist(first.artist),
                album = first.album,
                genre = sessions.firstNotNullOfOrNull { it.genre?.takeIf(String::isNotBlank) }
                    ?: GenreClassifier.classify(first.artist, first.title, first.album),
                artworkUrl = sessions.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) },
                totalSeconds = sessions.sumOf { PlaybackSessionDurations.durationForDate(it, date) },
                playCount = sessions.sumOf { it.playCount },
                lastPlayedTimestamp = sessions.maxOf { it.startTime }
            )
        }.sortedByDescending { it.totalSeconds }

    private data class YearSummary(
        val totalSeconds: Long,
        val activeDays: Int,
        val averageMinutes: Int,
        val months: List<MonthChartItem>,
        val peakMonthName: String,
        val peakMonthHours: Float,
        val milestones: List<Milestone>,
        val streak: Int
    )

    private fun buildYearSummary(
        year: Int,
        stats: List<DailyStatEntity>,
        streak: Int
    ): YearSummary {
        val current = Calendar.getInstance()
        val names = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val months = (1..12).map { month ->
            val monthStats = stats.filter { it.month == month }
            val seconds = monthStats.sumOf { it.totalPlayTimeSeconds }
            MonthChartItem(
                month, names[month - 1], seconds, seconds / 3600f,
                monthStats.count { it.totalPlayTimeSeconds > 0L },
                year == current.get(Calendar.YEAR) && month == current.get(Calendar.MONTH) + 1
            )
        }
        val total = months.sumOf { it.totalSeconds }
        val activeDays = months.sumOf { it.activeDays }
        val peak = months.maxByOrNull { it.totalSeconds }
        val milestoneDefinitions = listOf(
            Triple("Bronze listener", 5, "First 5 hours listened"),
            Triple("Silver beat", 25, "25 hours listened"),
            Triple("Gold listener", 50, "50 hours listened"),
            Triple("Platinum listener", 100, "100 hours listened"),
            Triple("Diamond listener", 250, "250 hours listened")
        )
        val hours = total / 3600f
        return YearSummary(
            total,
            activeDays,
            if (activeDays == 0) 0 else (total / 60L / activeDays).toInt(),
            months,
            peak?.takeIf { it.totalSeconds > 0L }?.monthName ?: "-",
            peak?.totalHours ?: 0f,
            milestoneDefinitions.map { (title, requiredHours, description) ->
                Milestone(
                    title, requiredHours, description, hours >= requiredHours,
                    (hours / requiredHours).coerceIn(0f, 1f)
                )
            },
            streak
        )
    }

    private fun buildGenreAnalytics(scope: GenreScope, rows: List<GenreAggregateRow>): GenreAnalyticsData {
        val total = rows.sumOf { it.totalSeconds }
        val genres = rows.map { row ->
            GenreSliceData(
                row.genreName,
                row.totalSeconds,
                (row.totalSeconds / 60L).toInt(),
                if (total == 0L) 0f else row.totalSeconds.toFloat() / total * 100f,
                row.trackCount,
                GenreClassifier.getColorForGenre(row.genreName)
            )
        }
        return GenreAnalyticsData(
            scope,
            genres,
            total,
            genres.firstOrNull()?.genreName ?: "-",
            genres.firstOrNull()?.percentage ?: 0f,
            genres.sumOf { it.trackCount }
        )
    }

    private fun validBoundarySessions(
        sessions: List<PlaybackSessionEntity>,
        bounds: DateBounds
    ): List<ScopedGenreSession> =
        sessions.mapNotNull { session ->
            if (YouTubeHelper.isYouTubeVideoPackage(session.sourcePackage) ||
                engine.isPlaceholderTitle(session.title) || session.title.isNullOrBlank() || session.durationSeconds < 5L
            ) return@mapNotNull null
            ScopedGenreSession(
                session = session,
                genre = session.genre?.trim()?.takeIf(String::isNotEmpty) ?: "Other",
                seconds = PlaybackSessionDurations.durationForPeriod(session) { date ->
                    date in bounds
                }
            )
        }

    private suspend fun queryBoundaryTrackAggregates(
        sessions: List<ScopedGenreSession>,
        bounds: DateBounds
    ): Map<GenreTrackKey, TrackAggregateRow?> {
        val trackBases = mutableMapOf<GenreTrackKey, TrackAggregateRow?>()
        for (contribution in sessions) {
            val session = contribution.session
            val key = GenrePeriodAdjustments.trackKey(contribution.genre, session.title, session.artist)
            if (!trackBases.containsKey(key)) {
                trackBases[key] = repository.getTrackAggregateForGenre(
                    bounds.startDate,
                    bounds.endDate,
                    contribution.genre,
                    session.title,
                    session.artist
                )
            }
        }
        return trackBases
    }

    private fun mapTrackAggregate(row: TrackAggregateRow) = UniqueTrackItem(
        row.title, cleanArtist(row.artist), row.album, row.genre, row.artworkUrl,
        row.totalSeconds, row.playCount, row.lastPlayedTimestamp
    )

    private fun normalizedTrackKey(title: String?, artist: String?) =
        "${engine.normalizeTrackTitle(title)}|${engine.normalizeArtistName(artist)}"

    private fun cleanArtist(artist: String?): String = artist.orEmpty()
        .replace("• YouTube Music", "", ignoreCase = true)
        .replace("• YouTube", "", ignoreCase = true)
        .replace("• Spotify", "", ignoreCase = true)
        .trim()
        .ifBlank { "Unknown artist" }

    private fun dateFormatter() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun parseDate(date: String) = requireNotNull(dateFormatter().parse(date))

    private fun formatDate(date: Date) = dateFormatter().format(date)

    private fun currentDate() = formatDate(Date())

    private fun dateDaysAgo(days: Int, fromDate: String = currentDate()) = formatDate(
        Calendar.getInstance().apply {
            time = parseDate(fromDate)
            add(Calendar.DAY_OF_YEAR, -days)
        }.time
    )

    fun historyBounds(range: HistoryRange, today: String = currentDate()): DateBounds {
        val start = Calendar.getInstance().apply {
            time = parseDate(today)
            add(Calendar.DAY_OF_YEAR, -(range.days - 1))
        }
        return DateBounds(formatDate(start.time), today)
    }

    private fun genreBounds(scope: GenreScope, year: Int, month: Int): DateBounds = when (scope) {
        GenreScope.MONTH -> {
            val first = Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }
            val last = (first.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            DateBounds(formatDate(first.time), formatDate(last.time))
        }
        GenreScope.YEAR -> DateBounds("%04d-01-01".format(Locale.US, year), "%04d-12-31".format(Locale.US, year))
        GenreScope.ALL_TIME -> DateBounds("0001-01-01", "9999-12-31")
    }

    fun selectTab(tab: TrackerTab) {
        _selectedTab.value = tab
        _analyticsState.value = _analyticsState.value.copy(selectedTab = tab)
    }

    fun selectHistoryRange(range: HistoryRange) {
        _selectedHistoryDate.value = null
        _historyRange.value = range
    }

    fun selectHistoryDate(date: String?) { _selectedHistoryDate.value = date }
    fun selectYear(year: Int) {
        _yearPinnedByUser.value = year != _currentDate.value.substringBefore('-').toInt()
        _selectedYear.value = year
    }

    fun selectGenreScope(scope: GenreScope) {
        _selectedGenre.value = null
        _genreScope.value = scope
    }

    fun selectGenre(genre: String?) { _selectedGenre.value = genre }

    fun setTrackGenre(track: UniqueTrackItem, genre: String) {
        val clean = genre.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank() || clean.length > 60) return
        viewModelScope.launch {
            repository.setManualGenre(track.artist, track.title, clean)
            engine.applyManualGenre(track.artist, track.title, clean)
        }
    }
    fun setFilterOnlyYouTubeMusic(onlyYt: Boolean) { engine.setFilterOnlyYouTubeMusic(onlyYt) }
    fun setDailyGoalMinutes(minutes: Int) { engine.setDailyGoalMinutes(minutes) }
    fun refreshTrackingState() { engine.scanActiveMediaSessions() }

    fun openNotificationListenerSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (error: Exception) {
            Log.e(TAG, "Intent launch failed", error)
        }
    }

    fun launchYouTubeMusic(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(YOUTUBE_MUSIC_PACKAGE)
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://music.youtube.com".toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (error: Exception) {
            Log.e(TAG, "Intent launch failed", error)
        }
    }

    fun seedSampleData() {
        if (isSeedingSampleData) return
        isSeedingSampleData = true
        viewModelScope.launch {
            try {
                repository.seedSampleAnalyticsForYear(_selectedYear.value)
            } finally {
                isSeedingSampleData = false
            }
        }
    }

    fun clearAllData() { engine.clearAllData() }

    private companion object {
        const val TAG = "MainViewModel"
        const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
    }
}
