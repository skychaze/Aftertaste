package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.YTTrackerApplication
import com.example.data.DailyStatEntity
import com.example.data.PlaybackSessionEntity
import com.example.tracker.GenreClassifier
import com.example.tracker.MusicTrackerEngine
import com.example.tracker.TrackerUiState
import com.example.tracker.YouTubeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TrackerTab(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    YEARLY("Yearly"),
    GENRES("Genres")
}

enum class GenreScope(val label: String) {
    MONTH("This Month"),
    YEAR("This Year"),
    ALL_TIME("All Records")
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
    val playCount: Int = 1
)

data class DayChartItem(
    val dateStr: String,
    val dayLabel: String, // e.g., "Mon", "Tue"
    val dayNumber: Int, // e.g., 2
    val seconds: Long,
    val minutes: Int,
    val isToday: Boolean,
    val uniqueTracks: List<UniqueTrackItem> = emptyList()
)

data class MonthChartItem(
    val monthNumber: Int, // 1 - 12
    val monthName: String, // "Jan", "Feb"
    val totalSeconds: Long,
    val totalHours: Float,
    val activeDays: Int,
    val isCurrentMonth: Boolean,
    val uniqueTracks: List<UniqueTrackItem> = emptyList()
)

data class Milestone(
    val title: String,
    val requiredHours: Int,
    val description: String,
    val isUnlocked: Boolean,
    val progressFraction: Float
)

data class MonthlyDayChartItem(
    val dayNumber: Int, // 1..31
    val dateStr: String,
    val dayOfWeekName: String,
    val isWeekend: Boolean,
    val seconds: Long,
    val minutes: Int,
    val hours: Float,
    val isToday: Boolean,
    val isFuture: Boolean
)

data class WeekBreakdownItem(
    val weekNumber: Int,
    val label: String,
    val dateRangeLabel: String,
    val totalSeconds: Long,
    val totalHours: Float,
    val activeDays: Int,
    val percentageOfMonthly: Float
)

data class MonthlyAnalyticsData(
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val monthNumber: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val monthName: String = "Month",
    val totalSeconds: Long = 0L,
    val totalHours: Float = 0f,
    val activeDays: Int = 0,
    val totalDaysInMonth: Int = 30,
    val activeDaysPercentage: Float = 0f,
    val averageDailyMinutes: Int = 0,
    val peakDayNumber: Int = 1,
    val peakDaySeconds: Long = 0L,
    val peakDayMinutes: Int = 0,
    val days: List<MonthlyDayChartItem> = emptyList(),
    val weeks: List<WeekBreakdownItem> = emptyList(),
    val weekdayAverageMinutes: Int = 0,
    val weekendAverageMinutes: Int = 0
)

data class GenreSliceData(
    val genreName: String,
    val totalSeconds: Long,
    val totalMinutes: Int,
    val percentage: Float, // 0..100
    val trackCount: Int,
    val color: Color,
    val topArtists: List<String>,
    val uniqueTracks: List<UniqueTrackItem> = emptyList()
)

data class GenreAnalyticsData(
    val scope: GenreScope = GenreScope.MONTH,
    val genres: List<GenreSliceData> = emptyList(),
    val totalSeconds: Long = 0L,
    val dominantGenre: String = "Pop",
    val dominantGenrePercentage: Float = 0f,
    val totalTracksTracked: Int = 0
)

data class AnalyticsUiState(
    val selectedTab: TrackerTab = TrackerTab.DAILY,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val availableYears: List<Int> = listOf(Calendar.getInstance().get(Calendar.YEAR)),
    val trackerState: TrackerUiState = TrackerUiState(),
    val todayTrackFeed: List<TodayTrackFeedItem> = emptyList(),
    val past7Days: List<DayChartItem> = emptyList(),
    val weekAverageMinutes: Int = 0,
    val yearTotalSeconds: Long = 0L,
    val yearActiveDays: Int = 0,
    val yearAverageMinutesPerDay: Int = 0,
    val monthlyBreakdown: List<MonthChartItem> = emptyList(),
    val peakMonthName: String = "-",
    val peakMonthHours: Float = 0f,
    val milestones: List<Milestone> = emptyList(),
    val currentStreakDays: Int = 0,
    val monthlyData: MonthlyAnalyticsData = MonthlyAnalyticsData(),
    val genreAnalytics: GenreAnalyticsData = GenreAnalyticsData(),
    val genreScope: GenreScope = GenreScope.MONTH
)

private data class UISelections(
    val tab: TrackerTab,
    val year: Int,
    val month: Int,
    val genreScope: GenreScope
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YTTrackerApplication
    private val repository = app.repository
    private val engine = app.trackerEngine

    private val _selectedTab = MutableStateFlow(TrackerTab.DAILY)
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val _genreScope = MutableStateFlow(GenreScope.MONTH)

    private val _analyticsState = MutableStateFlow(AnalyticsUiState())
    val analyticsState: StateFlow<AnalyticsUiState> = _analyticsState.asStateFlow()

    // Cache for the expensive analytics rebuild; per-second tracker ticks only
    // patch live values instead of re-grouping every session in the database
    private var cachedAnalytics: AnalyticsUiState? = null
    private var cachedSelections: UISelections? = null
    private var cachedDailyStats: List<DailyStatEntity>? = null
    private var cachedSessions: List<PlaybackSessionEntity>? = null
    private var cachedTrackerSignature: String? = null

    init {
        viewModelScope.launch {
            repository.cleanCorruptSessions()
        }
        observeData()
    }

    private fun observeData() {
        val selectionsFlow = combine(
            _selectedTab,
            _selectedYear,
            _selectedMonth,
            _genreScope
        ) { tab, year, month, scope ->
            UISelections(tab, year, month, scope)
        }

        viewModelScope.launch {
            combine(
                engine.uiState,
                selectionsFlow,
                repository.getAllDailyStats(),
                repository.getAllSessions()
            ) { trackerState, selections, allDailyStats, allSessions ->
                val signature = trackerSignature(trackerState)
                val canReuse = cachedAnalytics != null &&
                        cachedSelections === selections &&
                        cachedDailyStats === allDailyStats &&
                        cachedSessions === allSessions &&
                        cachedTrackerSignature == signature

                val built = if (canReuse) {
                    cachedAnalytics!!
                } else {
                    buildAnalyticsUiState(trackerState, selections, allDailyStats, allSessions)
                }

                // Keep per-second values exact without redoing the heavy grouping work
                val fresh = built.copy(
                    trackerState = trackerState,
                    todayTrackFeed = built.todayTrackFeed.map { item ->
                        if (item.isActivelyPlaying) {
                            item.copy(durationSeconds = trackerState.currentSessionSeconds)
                        } else item
                    }
                )

                cachedAnalytics = fresh
                cachedSelections = selections
                cachedDailyStats = allDailyStats
                cachedSessions = allSessions
                cachedTrackerSignature = signature
                fresh
            }
                // Analytics rebuilds stay off the main thread
                .flowOn(Dispatchers.Default)
                .collect { newState ->
                    _analyticsState.value = newState
                }
        }
    }

    /**
     * Signature of the tracker state fields analytics depend on. Ticking seconds are
     * quantized to 5s buckets since chart bars tolerate that much staleness; the
     * hero timer and live feed duration are patched per tick in observeData().
     */
    private fun trackerSignature(state: TrackerUiState): String =
        "${state.isActivelyPlaying}|${state.trackTitle}|${state.artist}|${state.album}|" +
                "${state.artworkUrl}|${state.currentGenre}|" +
                "${state.currentSessionSeconds / 5}|${state.todayTotalSeconds / 5}"

    private fun buildAnalyticsUiState(
        trackerState: TrackerUiState,
        selections: UISelections,
        allDailyStats: List<DailyStatEntity>,
        rawSessions: List<PlaybackSessionEntity>
    ): AnalyticsUiState {
        val allSessions = rawSessions.filter { !YouTubeHelper.isYouTubeVideoPackage(it.sourcePackage) }
        val tab = selections.tab
        val year = selections.year
        val month = selections.month
        val genreScope = selections.genreScope

        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayNameFmt = SimpleDateFormat("EEE", Locale.US)
        val todayStr = sdf.format(Date())

        // Available years list
        val dbYears = allDailyStats.map { it.year }.distinct()
        val availableYears = (dbYears + currentYear).distinct().sortedDescending()

        // Helper to detect placeholder track names
        val isPlaceholder = { t: String? ->
            if (t.isNullOrBlank()) true
            else {
                val lower = t.lowercase(Locale.ROOT).trim()
                lower == "no music playing" ||
                lower == "waiting for youtube music" ||
                lower == "background audio active" ||
                lower == "background music playing" ||
                lower == "media player" ||
                lower == "youtube music track" ||
                lower == "syncing track info..." ||
                lower == "detecting..." ||
                lower == "detecting track..." ||
                lower == "unknown track"
            }
        }

        // Helper to normalize artist string
        val cleanArtistStr = { artist: String? ->
            if (artist.isNullOrBlank()) "Unknown Artist"
            else {
                val cleaned = artist
                    .replace("• YouTube Music", "", ignoreCase = true)
                    .replace("• YouTube", "", ignoreCase = true)
                    .replace("YouTube Music", "", ignoreCase = true)
                    .replace("• Spotify", "", ignoreCase = true)
                    .trim()
                if (cleaned.isBlank() || isPlaceholder(cleaned)) "Unknown Artist" else cleaned
            }
        }

        // Helper to extract non-repeating unique tracks from playback records
        fun normalizeKey(title: String?, artist: String?): String {
            val t = engine.normalizeTrackTitle(title)
            val a = engine.normalizeArtistName(artist)
            return if (a == "unknown artist" || a.isBlank()) t else "$t|$a"
        }

        val app = getApplication<Application>()

        fun isLiveGroup(sessions: List<PlaybackSessionEntity>, key: String): Boolean {
            return trackerState.isActivelyPlaying &&
                    (sessions.any { it.id == engine.getCurrentDbSessionId() } ||
                     sessions.any { engine.isSameTrack(it.title, it.artist, trackerState.trackTitle, trackerState.artist) } ||
                     normalizeKey(trackerState.trackTitle, trackerState.artist) == key)
        }

        fun groupTotalSeconds(sessions: List<PlaybackSessionEntity>, isCurrent: Boolean): Long {
            val pastSessionsTotal = sessions
                .filter { it.id != engine.getCurrentDbSessionId() }
                .sumOf { it.durationSeconds }
            return if (isCurrent) {
                pastSessionsTotal + trackerState.currentSessionSeconds
            } else {
                sessions.sumOf { it.durationSeconds }
            }
        }

        fun groupGenre(sessions: List<PlaybackSessionEntity>, first: PlaybackSessionEntity): String {
            return sessions.mapNotNull { it.genre }.firstOrNull { it.isNotBlank() }
                ?: GenreClassifier.classify(first.artist, first.title, first.album)
        }

        fun groupArtwork(sessions: List<PlaybackSessionEntity>, first: PlaybackSessionEntity, isCurrent: Boolean): String? {
            return sessions.mapNotNull { it.artworkUrl }.firstOrNull { it.isNotBlank() }
                ?: (if (isCurrent) trackerState.artworkUrl else null)
                ?: com.example.tracker.ArtworkResolver.getCachedArtwork(app, cleanArtistStr(first.artist), first.title ?: "")
        }

        fun List<PlaybackSessionEntity>.toUniqueTracks(): List<UniqueTrackItem> {
            return this.filter { !isPlaceholder(it.title) && (it.durationSeconds >= 5L || it.id == engine.getCurrentDbSessionId()) }
                .groupBy { normalizeKey(it.title, it.artist) }
                .map { (key, sessions) ->
                    val first = sessions.first()
                    val isCurrent = isLiveGroup(sessions, key)
                    val totalSec = groupTotalSeconds(sessions, isCurrent)
                    // Looped tracks keep a single session row; their playCount
                    // column carries the extra plays
                    val playCount = sessions.sumOf { it.playCount }
                    val latestTime = sessions.maxOfOrNull { it.startTime } ?: 0L

                    UniqueTrackItem(
                        title = first.title ?: "Unknown Track",
                        artist = cleanArtistStr(first.artist),
                        album = first.album,
                        genre = groupGenre(sessions, first),
                        artworkUrl = groupArtwork(sessions, first, isCurrent),
                        totalSeconds = totalSec,
                        playCount = playCount,
                        lastPlayedTimestamp = latestTime
                    )
                }
                .sortedByDescending { it.totalSeconds }
        }

        val todaySessions = allSessions.filter {
            it.date == todayStr && !isPlaceholder(it.title) &&
            (it.durationSeconds >= 5L || it.id == engine.getCurrentDbSessionId())
        }

        // Sessions that started yesterday but kept playing past midnight have no
        // row dated today, yet the new day's sessionCount includes them via the
        // rollover increment. Surface a carried row so the feed and the stored
        // count agree instead of disagreeing on exactly the day boundary.
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // Upper bound excludes rows dated after today (e.g. leftovers from a
        // root clock jump); only sessions actually ending today can carry over.
        val startOfTomorrow = startOfToday + 24L * 60L * 60L * 1000L
        val carriedSessions = allSessions.filter {
            it.date != todayStr && it.endTime >= startOfToday && it.endTime < startOfTomorrow && !isPlaceholder(it.title) &&
            (it.durationSeconds >= 5L || it.id == engine.getCurrentDbSessionId())
        }
        val todayAndCarriedSessions = todaySessions + carriedSessions

        val todayGrouped = todayAndCarriedSessions
            .groupBy { normalizeKey(it.title, it.artist) }
            .map { (key, sessions) ->
                val first = sessions.first()
                val isCurrentlyPlaying = isLiveGroup(sessions, key)
                val duration = groupTotalSeconds(sessions, isCurrentlyPlaying)
                val playCount = sessions.sumOf { it.playCount }
                val latestTime = sessions.maxOfOrNull { it.startTime } ?: 0L

                TodayTrackFeedItem(
                    id = first.id,
                    title = first.title ?: "Unknown Track",
                    artist = cleanArtistStr(first.artist),
                    album = first.album,
                    genre = groupGenre(sessions, first),
                    artworkUrl = groupArtwork(sessions, first, isCurrentlyPlaying),
                    durationSeconds = duration,
                    timestamp = latestTime,
                    isActivelyPlaying = isCurrentlyPlaying,
                    playCount = playCount
                )
            }

        val hasActiveMatchInDb = trackerState.isActivelyPlaying && !isPlaceholder(trackerState.trackTitle) && todayGrouped.any { item ->
            item.id == engine.getCurrentDbSessionId() ||
                    engine.isSameTrack(item.title, item.artist, trackerState.trackTitle, trackerState.artist) ||
                    normalizeKey(item.title, item.artist) == normalizeKey(trackerState.trackTitle, trackerState.artist)
        }

        val todayTrackFeed = if (trackerState.isActivelyPlaying && !isPlaceholder(trackerState.trackTitle) && !hasActiveMatchInDb) {
            val liveArtwork = trackerState.artworkUrl
                ?: com.example.tracker.ArtworkResolver.getCachedArtwork(app, cleanArtistStr(trackerState.artist), trackerState.trackTitle)

            val liveItem = TodayTrackFeedItem(
                id = -1L,
                title = trackerState.trackTitle,
                artist = cleanArtistStr(trackerState.artist),
                album = trackerState.album,
                genre = trackerState.currentGenre,
                artworkUrl = liveArtwork,
                durationSeconds = trackerState.currentSessionSeconds,
                timestamp = System.currentTimeMillis(),
                isActivelyPlaying = true,
                playCount = 1
            )
            listOf(liveItem) + todayGrouped.sortedByDescending { it.timestamp }
        } else {
            val activeKey = if (trackerState.isActivelyPlaying && !isPlaceholder(trackerState.trackTitle)) {
                normalizeKey(trackerState.trackTitle, trackerState.artist)
            } else null

            todayGrouped.sortedWith(
                compareByDescending<TodayTrackFeedItem> { item ->
                    activeKey != null && (normalizeKey(item.title, item.artist) == activeKey ||
                            engine.isSameTrack(item.title, item.artist, trackerState.trackTitle, trackerState.artist))
                }.thenByDescending { it.timestamp }
            )
        }

        // 2. Calculate Past 7 Days for Weekly Histogram
        val past7Days = mutableListOf<DayChartItem>()
        var weekTotalSeconds = 0L

        for (i in 6 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            val dStr = sdf.format(c.time)
            val dName = dayNameFmt.format(c.time)
            val dNum = c.get(Calendar.DAY_OF_MONTH)
            val isToday = (dStr == todayStr)

            val seconds = if (isToday) {
                trackerState.todayTotalSeconds
            } else {
                allDailyStats.firstOrNull { it.date == dStr }?.totalPlayTimeSeconds ?: 0L
            }

            weekTotalSeconds += seconds
            val daySessions = allSessions.filter { it.date == dStr }

            past7Days.add(
                DayChartItem(
                    dateStr = dStr,
                    dayLabel = dName,
                    dayNumber = dNum,
                    seconds = seconds,
                    minutes = (seconds / 60).toInt(),
                    isToday = isToday,
                    uniqueTracks = daySessions.toUniqueTracks()
                )
            )
        }

        val weekAverageMinutes = if (past7Days.isNotEmpty()) {
            (weekTotalSeconds / 60 / past7Days.size).toInt()
        } else 0

        // 3. Yearly Analytics & 12-Month Histogram
        val yearStats = allDailyStats.filter { it.year == year }
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val fullMonthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val monthlyBreakdown = mutableListOf<MonthChartItem>()

        var yearTotalSec = 0L
        var yearActiveDays = 0

        for (m in 1..12) {
            val statsForMonth = yearStats.filter { it.month == m }
            var monthSec = statsForMonth.sumOf { it.totalPlayTimeSeconds }
            var monthActive = statsForMonth.count { it.totalPlayTimeSeconds > 0 }

            if (year == currentYear && m == currentMonth) {
                val todayDbStat = statsForMonth.firstOrNull { it.date == todayStr }
                val todayDbSec = todayDbStat?.totalPlayTimeSeconds ?: 0L
                val diff = trackerState.todayTotalSeconds - todayDbSec
                if (diff > 0) {
                    monthSec += diff
                }
                if (trackerState.todayTotalSeconds > 0 && todayDbStat == null) {
                    monthActive += 1
                }
            }

            yearTotalSec += monthSec
            yearActiveDays += monthActive

            val hours = (monthSec / 3600f)
            val monthSessions = allSessions.filter { it.year == year && it.month == m }

            monthlyBreakdown.add(
                MonthChartItem(
                    monthNumber = m,
                    monthName = monthNames[m - 1],
                    totalSeconds = monthSec,
                    totalHours = String.format(Locale.US, "%.1f", hours).toFloatOrNull() ?: hours,
                    activeDays = monthActive,
                    isCurrentMonth = (year == currentYear && m == currentMonth),
                    uniqueTracks = monthSessions.toUniqueTracks()
                )
            )
        }

        val yearAvgMinutes = if (yearActiveDays > 0) {
            (yearTotalSec / 60 / yearActiveDays).toInt()
        } else 0

        val peakMonth = monthlyBreakdown.maxByOrNull { it.totalSeconds }
        val peakMonthName = if (peakMonth != null && peakMonth.totalSeconds > 0) peakMonth.monthName else "-"
        val peakMonthHours = peakMonth?.totalHours ?: 0f

        // 4. Milestones
        val totalHoursFloat = yearTotalSec / 3600f
        val milestoneDefs = listOf(
            Triple("Bronze Listener", 5, "First 5 hours of music listened"),
            Triple("Silver Beat", 25, "25 hours of active tunes"),
            Triple("Gold Melophile", 50, "50 hours musical dedication"),
            Triple("Platinum Virtuoso", 100, "100 hours of rhythm"),
            Triple("Diamond Maestro", 250, "250 hours ultimate music journey")
        )

        val milestones = milestoneDefs.map { (title, reqHours, desc) ->
            val unlocked = totalHoursFloat >= reqHours
            val fraction = (totalHoursFloat / reqHours).coerceIn(0f, 1f)
            Milestone(
                title = title,
                requiredHours = reqHours,
                description = desc,
                isUnlocked = unlocked,
                progressFraction = fraction
            )
        }

        // 5. Consecutive streak calculation
        var streak = 0
        val checkCal = Calendar.getInstance()
        if (trackerState.todayTotalSeconds > 0) {
            streak++
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val d = sdf.format(checkCal.time)
            val stat = allDailyStats.firstOrNull { it.date == d }
            if (stat != null && stat.totalPlayTimeSeconds > 0) {
                streak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        // 6. Monthly Data Visualization calculation (for deep monthly views)
        val monthCal = Calendar.getInstance()
        monthCal.set(Calendar.YEAR, year)
        monthCal.set(Calendar.MONTH, month - 1)
        monthCal.set(Calendar.DAY_OF_MONTH, 1)
        val totalDaysInSelectedMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val statsForSelectedMonth = allDailyStats.filter { it.year == year && it.month == month }

        val monthlyDays = mutableListOf<MonthlyDayChartItem>()
        var monthlyTotalSec = 0L
        var monthlyActiveDays = 0

        var weekdayTotalSec = 0L
        var weekdayCount = 0
        var weekendTotalSec = 0L
        var weekendCount = 0

        for (d in 1..totalDaysInSelectedMonth) {
            monthCal.set(Calendar.DAY_OF_MONTH, d)
            val dStr = sdf.format(monthCal.time)
            val dName = dayNameFmt.format(monthCal.time)
            val dayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
            val isToday = (year == currentYear && month == currentMonth && d == currentDay)
            val isFuture = (year > currentYear) || (year == currentYear && month > currentMonth) || (year == currentYear && month == currentMonth && d > currentDay)

            val dbStat = statsForSelectedMonth.firstOrNull { it.day == d || it.date == dStr }
            val dbSec = dbStat?.totalPlayTimeSeconds ?: 0L

            val seconds = if (isToday) {
                maxOf(dbSec, trackerState.todayTotalSeconds)
            } else {
                dbSec
            }

            if (seconds > 0) {
                monthlyActiveDays++
                if (isWeekend) {
                    weekendTotalSec += seconds
                    weekendCount++
                } else {
                    weekdayTotalSec += seconds
                    weekdayCount++
                }
            }

            monthlyTotalSec += seconds
            val minutes = (seconds / 60).toInt()
            val hours = seconds / 3600f

            monthlyDays.add(
                MonthlyDayChartItem(
                    dayNumber = d,
                    dateStr = dStr,
                    dayOfWeekName = dName,
                    isWeekend = isWeekend,
                    seconds = seconds,
                    minutes = minutes,
                    hours = String.format(Locale.US, "%.1f", hours).toFloatOrNull() ?: hours,
                    isToday = isToday,
                    isFuture = isFuture
                )
            )
        }

        val monthlyTotalHours = String.format(Locale.US, "%.1f", monthlyTotalSec / 3600f).toFloatOrNull() ?: (monthlyTotalSec / 3600f)
        val activeDaysPercentage = if (totalDaysInSelectedMonth > 0) {
            (monthlyActiveDays.toFloat() / totalDaysInSelectedMonth.toFloat()) * 100f
        } else 0f

        val monthlyAverageDailyMinutes = if (monthlyActiveDays > 0) {
            (monthlyTotalSec / 60 / monthlyActiveDays).toInt()
        } else 0

        val peakDay = monthlyDays.maxByOrNull { it.seconds }
        val peakDayNumber = peakDay?.dayNumber ?: 1
        val peakDaySeconds = peakDay?.seconds ?: 0L
        val peakDayMinutes = peakDay?.minutes ?: 0

        val weekRanges = listOf(
            Triple(1, "Week 1", "Days 1–7" to (1..7)),
            Triple(2, "Week 2", "Days 8–14" to (8..14)),
            Triple(3, "Week 3", "Days 15–21" to (15..21)),
            Triple(4, "Week 4", "Days 22–28" to (22..28)),
            Triple(5, "Week 5", "Days 29–$totalDaysInSelectedMonth" to (29..totalDaysInSelectedMonth))
        )

        val weeks = mutableListOf<WeekBreakdownItem>()
        for ((wNum, wLabel, rangePair) in weekRanges) {
            val (rangeLabel, range) = rangePair
            val daysInWeek = monthlyDays.filter { it.dayNumber in range }
            if (daysInWeek.isNotEmpty()) {
                val wSec = daysInWeek.sumOf { it.seconds }
                val wHours = String.format(Locale.US, "%.1f", wSec / 3600f).toFloatOrNull() ?: (wSec / 3600f)
                val wActive = daysInWeek.count { it.seconds > 0 }
                val wPct = if (monthlyTotalSec > 0) {
                    (wSec.toFloat() / monthlyTotalSec.toFloat()) * 100f
                } else 0f

                weeks.add(
                    WeekBreakdownItem(
                        weekNumber = wNum,
                        label = wLabel,
                        dateRangeLabel = rangeLabel,
                        totalSeconds = wSec,
                        totalHours = wHours,
                        activeDays = wActive,
                        percentageOfMonthly = wPct
                    )
                )
            }
        }

        val weekdayAvgMin = if (weekdayCount > 0) (weekdayTotalSec / 60 / weekdayCount).toInt() else 0
        val weekendAvgMin = if (weekendCount > 0) (weekendTotalSec / 60 / weekendCount).toInt() else 0

        val monthlyData = MonthlyAnalyticsData(
            year = year,
            monthNumber = month,
            monthName = fullMonthNames.getOrElse(month - 1) { "Month" },
            totalSeconds = monthlyTotalSec,
            totalHours = monthlyTotalHours,
            activeDays = monthlyActiveDays,
            totalDaysInMonth = totalDaysInSelectedMonth,
            activeDaysPercentage = activeDaysPercentage,
            averageDailyMinutes = monthlyAverageDailyMinutes,
            peakDayNumber = peakDayNumber,
            peakDaySeconds = peakDaySeconds,
            peakDayMinutes = peakDayMinutes,
            days = monthlyDays,
            weeks = weeks,
            weekdayAverageMinutes = weekdayAvgMin,
            weekendAverageMinutes = weekendAvgMin
        )

        // 7. Genre Analytics & Distribution calculation (with unique non-repeating tracks per genre)
        val genreTargetSessions = when (genreScope) {
            GenreScope.MONTH -> allSessions.filter { it.year == year && it.month == month }
            GenreScope.YEAR -> allSessions.filter { it.year == year }
            GenreScope.ALL_TIME -> allSessions
        }.filter { !isPlaceholder(it.title) }

        val genreGroups = genreTargetSessions.groupBy { session ->
            session.genre?.takeIf { it.isNotBlank() }
                ?: GenreClassifier.classify(session.artist, session.title, session.album)
        }

        // Calculate total effective seconds across all genre groups taking live playback into account
        val genreGroupDurations = genreGroups.mapValues { (_, sList) ->
            val sum = sList.sumOf { s ->
                if (trackerState.isActivelyPlaying && s.title.equals(trackerState.trackTitle, ignoreCase = true)) {
                    maxOf(s.durationSeconds, trackerState.currentSessionSeconds)
                } else {
                    s.durationSeconds
                }
            }
            if (sum > 0L) sum else (sList.size * 60L)
        }

        val totalEffectiveGenreSeconds = genreGroupDurations.values.sum().coerceAtLeast(1L)
        val genreSlices = mutableListOf<GenreSliceData>()

        for ((genreName, sList) in genreGroups) {
            val groupSec = genreGroupDurations[genreName] ?: (sList.size * 60L)
            val pct = (groupSec.toFloat() / totalEffectiveGenreSeconds.toFloat()) * 100f

            val topArtists = sList
                .mapNotNull { it.artist }
                .filter { it.isNotBlank() && !isPlaceholder(it) }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(3)

            val uniqueTracks = sList.toUniqueTracks()

            genreSlices.add(
                GenreSliceData(
                    genreName = genreName,
                    totalSeconds = groupSec,
                    totalMinutes = (groupSec / 60).toInt(),
                    percentage = String.format(Locale.US, "%.1f", pct).toFloatOrNull() ?: pct,
                    trackCount = uniqueTracks.size,
                    color = GenreClassifier.getColorForGenre(genreName),
                    topArtists = topArtists,
                    uniqueTracks = uniqueTracks
                )
            )
        }

        genreSlices.sortByDescending { it.percentage }

        val dominantGenre = genreSlices.firstOrNull()?.genreName ?: "Pop"
        val dominantGenrePct = genreSlices.firstOrNull()?.percentage ?: 0f

        val genreAnalytics = GenreAnalyticsData(
            scope = genreScope,
            genres = genreSlices,
            totalSeconds = totalEffectiveGenreSeconds,
            dominantGenre = dominantGenre,
            dominantGenrePercentage = dominantGenrePct,
            totalTracksTracked = genreTargetSessions.size
        )

        return AnalyticsUiState(
            selectedTab = tab,
            selectedYear = year,
            selectedMonth = month,
            availableYears = availableYears,
            trackerState = trackerState,
            todayTrackFeed = todayTrackFeed,
            past7Days = past7Days,
            weekAverageMinutes = weekAverageMinutes,
            yearTotalSeconds = yearTotalSec,
            yearActiveDays = yearActiveDays,
            yearAverageMinutesPerDay = yearAvgMinutes,
            monthlyBreakdown = monthlyBreakdown,
            peakMonthName = peakMonthName,
            peakMonthHours = peakMonthHours,
            milestones = milestones,
            currentStreakDays = streak,
            monthlyData = monthlyData,
            genreAnalytics = genreAnalytics,
            genreScope = genreScope
        )
    }

    fun selectTab(tab: TrackerTab) {
        _selectedTab.value = tab
    }

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }

    fun selectMonth(month: Int) {
        _selectedMonth.value = month.coerceIn(1, 12)
    }

    fun previousMonth() {
        val currentM = _selectedMonth.value
        if (currentM > 1) {
            _selectedMonth.value = currentM - 1
        } else {
            _selectedMonth.value = 12
            _selectedYear.value = _selectedYear.value - 1
        }
    }

    fun nextMonth() {
        val currentM = _selectedMonth.value
        if (currentM < 12) {
            _selectedMonth.value = currentM + 1
        } else {
            _selectedMonth.value = 1
            _selectedYear.value = _selectedYear.value + 1
        }
    }

    fun selectGenreScope(scope: GenreScope) {
        _genreScope.value = scope
    }

    fun setFilterOnlyYouTubeMusic(onlyYt: Boolean) {
        engine.setFilterOnlyYouTubeMusic(onlyYt)
    }

    fun setDailyGoalMinutes(minutes: Int) {
        engine.setDailyGoalMinutes(minutes)
    }

    fun refreshTrackingState() {
        engine.scanActiveMediaSessions()
    }

    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun launchYouTubeMusic(context: Context) {
        val ytmPackage = "com.google.android.apps.youtube.music"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(ytmPackage)
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
        } else {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com"))
                webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(webIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seedSampleData() {
        viewModelScope.launch {
            repository.seedSampleAnalyticsForYear(_selectedYear.value)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
