package com.example.tracker

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.MusicTrackerRepository
import com.example.service.MusicNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class TrackerUiState(
    val isActivelyPlaying: Boolean = false,
    val trackTitle: String = "No music playing",
    val artist: String = "Waiting for YouTube Music",
    val album: String = "",
    val currentGenre: String = "Pop",
    val artworkUrl: String? = null,
    val sourcePackage: String = "com.google.android.apps.youtube.music",
    val isYouTubeMusicSource: Boolean = false,
    val currentSessionSeconds: Long = 0L,
    val trackPositionMs: Long = 0L,
    val trackDurationMs: Long = 0L,
    val todayTotalSeconds: Long = 0L,
    val todaySessionCount: Int = 0,
    val isNotificationAccessGranted: Boolean = false,
    val filterOnlyYouTubeMusic: Boolean = true,
    val dailyGoalMinutes: Int = 60
)

class MusicTrackerEngine private constructor(
    private val context: Context,
    private val repository: MusicTrackerRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        TrackerUiState(dailyGoalMinutes = DEFAULT_DAILY_GOAL_MINUTES)
    )
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var currentDbSessionId: Long? = null
    private val pendingSecondsForDb = AtomicLong(0L)
    private var activeController: MediaController? = null

    // Date the in-memory "today" counters and DB writes are anchored to;
    // checkDayRollover() re-anchors it when the calendar day changes
    @Volatile
    private var todayDay = currentDayKey()

    // Date the active session was started on; discard rollbacks must target it
    @Volatile
    private var currentSessionDate: String = todayDay.date

    // Seconds of the open session already flushed to each calendar date.
    // The ticker anchors every flush to the day it ticked under, so this map is
    // the exact per-day split used to roll back discards without touching days
    // that never received these seconds. ConcurrentHashMap because flushes run
    // on the ticker coroutine while session boundaries run on the main thread.
    private val currentSessionSecondsByDate = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Calendar dates whose daily_stats sessionCount already includes the open
    // session (start date, plus each midnight it played across). Guards the
    // rollover/resume increments against double-counting.
    private val countedDatesForCurrentSession: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    // YT Music sometimes reports a shrinking duration mid-track while position
    // keeps growing. stableTrackDurationMs keeps the largest duration observed
    // for the current track so progress rendering and loop detection reason
    // from stable input instead of the flapping values.
    @Volatile
    private var stableTrackDurationMs: Long = 0L

    // Track loop detection state
    private var maxObservedPositionMs: Long = 0L
    private var lastLoopDetectionTimestamp: Long = 0L

    // Plays recorded for the current session; loops absorbed into the session
    // increment this and the session row's playCount
    private var currentSessionPlayCount = 1

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mainHandler.post {
                handlePlaybackState(state, activeController)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mainHandler.post {
                handleMetadata(metadata, activeController)
            }
        }

        override fun onSessionDestroyed() {
            mainHandler.post {
                activeController = null
                onPlaybackPausedOrStopped()
            }
        }
    }

    init {
        checkPermission()
        _uiState.update {
            it.copy(dailyGoalMinutes = prefs.getInt(KEY_DAILY_GOAL_MINUTES, DEFAULT_DAILY_GOAL_MINUTES))
        }
        scope.launch {
            // Resync/cleanup must finish before today's totals are loaded, otherwise
            // the UI can show a stale pre-cleanup value for the rest of the day
            repository.cleanCorruptSessions()
            repository.deleteShortSessions()
            loadTodayStatFromDb()
        }
        // Check active sessions immediately on initialization
        scanActiveMediaSessions()
    }

    fun getCurrentDbSessionId(): Long? = currentDbSessionId

    fun checkPermission(): Boolean {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        _uiState.update { it.copy(isNotificationAccessGranted = granted) }
        return granted
    }

    private suspend fun loadTodayStatFromDb() {
        val stat = repository.getDailyStatSync(todayDay.date)
        // Always set, even when no row exists yet (fresh day), so counters
        // reset instead of carrying yesterday's total
        _uiState.update {
            it.copy(
                todayTotalSeconds = stat?.totalPlayTimeSeconds ?: 0L,
                todaySessionCount = stat?.sessionCount ?: 0
            )
        }
    }

    /**
     * Re-reads today's stored sessionCount and raises the live counter to it.
     * Session starts and resumes bump the live counter synchronously while the
     * DB write lands async; a day-rollover reload in flight at the same moment
     * must never clobber that bump back down, so the DB value wins upwards.
     * Totals are deliberately untouched here so live ticks are never rewound.
     */
    private fun convergeTodayCountFromDb(date: String) {
        scope.launch {
            val dbCount = repository.getDailyStatSync(date)?.sessionCount ?: 0
            _uiState.update {
                it.copy(todaySessionCount = it.todaySessionCount.coerceAtLeast(dbCount))
            }
        }
    }

    /**
     * Re-anchors the in-memory "today" counters when the calendar day changes,
     * so a long-lived process does not keep counting yesterday's total into the
     * new day. Called from the ticker (playing across midnight) and from
     * scanActiveMediaSessions() (app reopened while paused after midnight).
     */
    private fun checkDayRollover() {
        val now = currentDayKey()
        if (now.date == todayDay.date) return
        // Drain unflushed seconds onto the day they were listened on, then
        // re-anchor. Counters are zeroed synchronously so no tick or analytics
        // rebuild can observe yesterday's total; the async reload below
        // corrects them if today's row already holds seconds. The open
        // session keeps its start date, so discards still roll back the
        // day that received its seconds.
        flushPendingSecondsToDb()
        val continuing = _uiState.value.isActivelyPlaying && currentDbSessionId != null
        todayDay = now
        _uiState.update {
            it.copy(todayTotalSeconds = 0L, todaySessionCount = if (continuing) 1 else 0)
        }
        scope.launch {
            if (continuing && countedDatesForCurrentSession.add(now.date)) {
                repository.incrementSessionCount(now.date, now.year, now.month, now.day, now.dayOfWeek)
            }
            loadTodayStatFromDb()
            // The reload above reads the row the increment just wrote, so the
            // live count and the DB stay in exact agreement.
            if (continuing) {
                _uiState.update { it.copy(todaySessionCount = it.todaySessionCount.coerceAtLeast(1)) }
            }
        }
    }

    fun setFilterOnlyYouTubeMusic(onlyYt: Boolean) {
        _uiState.update { it.copy(filterOnlyYouTubeMusic = onlyYt) }
        scanActiveMediaSessions()
    }

    fun setDailyGoalMinutes(minutes: Int) {
        _uiState.update { it.copy(dailyGoalMinutes = minutes) }
        prefs.edit().putInt(KEY_DAILY_GOAL_MINUTES, minutes).apply()
    }

    fun scanActiveMediaSessions() {
        checkPermission()
        checkDayRollover()

        try {
            val mediaSessionManager =
                context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(context, MusicNotificationListenerService::class.java)

            if (checkPermission() && mediaSessionManager != null) {
                val rawControllers = mediaSessionManager.getActiveSessions(component)
                val controllers = rawControllers?.filter { ctrl ->
                    !YouTubeHelper.isYouTubeVideoPackage(ctrl.packageName)
                }
                if (!controllers.isNullOrEmpty()) {
                    evaluateControllers(controllers)
                } else {
                    // Check active notifications from listener service
                    val notifService = MusicNotificationListenerService.instance
                    val activeNotifs = notifService?.activeNotifications
                    var foundMediaNotif = false
                    if (activeNotifs != null) {
                        for (sbn in activeNotifs) {
                            val pkg = sbn.packageName ?: ""
                            if (YouTubeHelper.isYouTubeVideoPackage(pkg)) continue
                            if (YouTubeHelper.isYouTubeMusic(pkg) || (!_uiState.value.filterOnlyYouTubeMusic && (pkg.contains("music", ignoreCase = true) || pkg.contains("spotify", ignoreCase = true)))) {
                                notifService.extractAndNotifyMedia(sbn)
                                foundMediaNotif = true
                                break
                            }
                        }
                    }

                    if (!foundMediaNotif && _uiState.value.isActivelyPlaying) {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        if (audioManager?.isMusicActive != true) {
                            onPlaybackPausedOrStopped()
                        }
                    }
                }
            } else {
                // If notification permission not granted, check AudioManager as simple status check
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val isMusicPlaying = audioManager?.isMusicActive == true
                if (!isMusicPlaying && _uiState.value.isActivelyPlaying) {
                    onPlaybackPausedOrStopped()
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted or listener not enabled yet
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onSessionsChangedFromService(controllers: List<MediaController>?) {
        mainHandler.post {
            evaluateControllers(controllers ?: emptyList())
        }
    }

    fun onTrackDetectedFromNotification(
        title: String,
        artist: String,
        album: String,
        pkg: String,
        isYt: Boolean,
        bitmap: Bitmap? = null
    ) {
        mainHandler.post {
            if (YouTubeHelper.isYouTubeVideoPackage(pkg)) return@post
            if (YouTubeHelper.isYouTubeVideoNotification(pkg, title, artist, album)) return@post
            val isYtMusic = YouTubeHelper.isYouTubeMusic(pkg)
            if (_uiState.value.filterOnlyYouTubeMusic && !isYtMusic) return@post

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager?.isMusicActive != true) {
                // Audio is not actively playing, ignore non-playing notification
                return@post
            }
            val cachedArtUrl = if (bitmap != null) {
                ArtworkResolver.saveBitmapToCache(context, artist, title, bitmap)
            } else null
            onTrackDiscovered(title, artist, album, pkg, isYtMusic, directArtUrl = cachedArtUrl)
        }
    }

    private fun evaluateControllers(controllers: List<MediaController>) {
        // Exclude all YouTube Video apps (main YouTube, kids, tv)
        val nonVideoControllers = controllers.filter { ctrl ->
            !YouTubeHelper.isYouTubeVideoPackage(ctrl.packageName)
        }

        // Look for YouTube Music first, then other media if filter allows
        val targetController = nonVideoControllers.firstOrNull { ctrl ->
            val isPlaying = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
            YouTubeHelper.isYouTubeMusic(ctrl.packageName) && isPlaying
        } ?: nonVideoControllers.firstOrNull { ctrl ->
            if (!_uiState.value.filterOnlyYouTubeMusic) {
                ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
            } else false
        }

        if (targetController != null) {
            switchActiveController(targetController)
        } else {
            // If current activeController is a YouTube Video app, immediately detach it
            val activePkg = activeController?.packageName
            if (activePkg != null && YouTubeHelper.isYouTubeVideoPackage(activePkg)) {
                try {
                    activeController?.unregisterCallback(controllerCallback)
                } catch (e: Exception) {}
                activeController = null
                if (_uiState.value.isActivelyPlaying) {
                    onPlaybackPausedOrStopped()
                }
                return
            }

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val currentIsPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (!currentIsPlaying && audioManager?.isMusicActive != true) {
                if (_uiState.value.isActivelyPlaying) {
                    onPlaybackPausedOrStopped()
                }
            }
        }
    }

    private fun switchActiveController(newController: MediaController) {
        if (activeController?.sessionToken != newController.sessionToken) {
            try {
                activeController?.unregisterCallback(controllerCallback)
            } catch (e: Exception) {
                // Ignore unregister errors
            }
            activeController = newController
            try {
                newController.registerCallback(controllerCallback, mainHandler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        handlePlaybackState(newController.playbackState, newController)
        handleMetadata(newController.metadata, newController)
    }

    private fun handlePlaybackState(state: PlaybackState?, controller: MediaController?) {
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val pkg = controller?.packageName ?: "com.google.android.apps.youtube.music"

        // Strictly reject YouTube Video applications
        if (YouTubeHelper.isYouTubeVideoPackage(pkg)) {
            if (_uiState.value.isActivelyPlaying) {
                onPlaybackPausedOrStopped()
            }
            return
        }

        val isYt = YouTubeHelper.isYouTubeMusic(pkg)

        if (_uiState.value.filterOnlyYouTubeMusic && !isYt) {
            if (_uiState.value.isActivelyPlaying) {
                onPlaybackPausedOrStopped()
            }
            return
        }

        if (isPlaying) {
            val metadata = controller?.metadata
            val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
            val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
            val rawAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim() ?: ""

            // Guard against video metadata
            if (YouTubeHelper.isYouTubeVideoNotification(pkg, rawTitle, rawArtist, rawAlbum)) {
                return
            }

            val title = if (!rawTitle.isNullOrBlank()) rawTitle else (if (isYt) "YouTube Music" else "Music Track")
            val artist = if (!rawArtist.isNullOrBlank()) rawArtist else (if (isYt) "YouTube Music" else "Unknown Artist")

            // Check if active track has repeated / looped
            val pos = state?.position ?: -1L
            if (_uiState.value.isActivelyPlaying && isSameTrack(title, artist, _uiState.value.trackTitle, _uiState.value.artist)) {
                if (checkAndHandleTrackLoop(pos, state, "handlePlaybackState")) {
                    return
                }
            }

            val cachedArtUrl = extractArtworkUrl(metadata, artist, title)

            onTrackDiscovered(title, artist, rawAlbum, pkg, isYt, directArtUrl = cachedArtUrl)
        } else {
            onPlaybackPausedOrStopped()
        }
    }

    private fun handleMetadata(metadata: MediaMetadata?, controller: MediaController?) {
        if (metadata != null && _uiState.value.isActivelyPlaying) {
            val pkg = controller?.packageName ?: _uiState.value.sourcePackage
            if (YouTubeHelper.isYouTubeVideoPackage(pkg)) return

            val isYt = YouTubeHelper.isYouTubeMusic(pkg)
            if (_uiState.value.filterOnlyYouTubeMusic && !isYt) return

            val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
            val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
            val rawAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim() ?: ""

            if (YouTubeHelper.isYouTubeVideoNotification(pkg, rawTitle, rawArtist, rawAlbum)) {
                return
            }

            val title = if (!rawTitle.isNullOrBlank()) rawTitle else _uiState.value.trackTitle
            val artist = if (!rawArtist.isNullOrBlank()) rawArtist else _uiState.value.artist

            val pos = controller?.playbackState?.position ?: -1L
            if (isSameTrack(title, artist, _uiState.value.trackTitle, _uiState.value.artist)) {
                if (checkAndHandleTrackLoop(pos, controller?.playbackState, "handleMetadata")) {
                    return
                }
            }

            val cachedArtUrl = extractArtworkUrl(metadata, artist, title)

            onTrackDiscovered(title, artist, rawAlbum, pkg, isYt, directArtUrl = cachedArtUrl)
        }
    }

    private fun extractArtworkUrl(metadata: MediaMetadata?, artist: String, title: String): String? {
        val artUri = metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        val artBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        return if (artBitmap != null) {
            ArtworkResolver.saveBitmapToCache(context, artist, title, artBitmap)
        } else artUri
    }

    fun cleanArtistName(rawArtist: String?): String {
        if (rawArtist.isNullOrBlank()) return ""
        val cleaned = rawArtist
            .replace("• YouTube Music", "", ignoreCase = true)
            .replace("• YouTube", "", ignoreCase = true)
            .replace("YouTube Music", "", ignoreCase = true)
            .replace("• Spotify", "", ignoreCase = true)
            .replace("• Topic", "", ignoreCase = true)
            .replace("- Topic", "", ignoreCase = true)
            .replace(Regex("\\b(ft|feat|featuring)\\.?\\s+.*$", RegexOption.IGNORE_CASE), "")
            .trim()
        return if (isPlaceholderArtist(cleaned)) "" else cleaned
    }

    fun normalizeTrackTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        var t = title.lowercase(Locale.ROOT)
        // Remove bracketed info like (Official Video), (feat. Artist), [Lyrics], etc.
        t = t.replace(Regex("\\((official|lyrics?|audio|video|visualizer|live|remix|remaster|hd|4k|ft|feat|featuring)[^\\)]*\\)", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\[(official|lyrics?|audio|video|visualizer|live|remix|remaster|hd|4k|ft|feat|featuring)[^\\]]*\\]", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\b(ft|feat|featuring)\\.?\\s+.*$", RegexOption.IGNORE_CASE), "")

        val tags = listOf(
            "(official video)", "[official video]",
            "(official music video)", "[official music video]",
            "(official audio)", "[official audio]",
            "(audio)", "[audio]",
            "(lyrics)", "[lyrics]",
            "(lyric video)", "[lyric video]",
            "(visualizer)", "[visualizer]",
            "(music video)", "[music video]",
            "(live)", "[live]",
            "(remix)", "[remix]",
            "(remastered)", "[remastered]",
            "(hd)", "[hd]",
            "(4k)", "[4k]"
        )
        for (tag in tags) {
            t = t.replace(tag, "")
        }
        return t.replace(Regex("[^a-z0-9 ]"), "").trim().replace(Regex("\\s+"), " ")
    }

    fun normalizeArtistName(artist: String?): String {
        val cleaned = cleanArtistName(artist)
        if (cleaned.isBlank() || isPlaceholderArtist(cleaned)) return "unknown artist"
        return cleaned.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), "").trim().replace(Regex("\\s+"), " ")
    }

    fun isSameTrack(t1: String?, a1: String?, t2: String?, a2: String?): Boolean {
        if (t1.isNullOrBlank() || t2.isNullOrBlank()) return false
        val normT1 = normalizeTrackTitle(t1)
        val normT2 = normalizeTrackTitle(t2)
        if (normT1.isBlank() || normT2.isBlank()) return false
        if (normT1 != normT2 && !normT1.startsWith(normT2) && !normT2.startsWith(normT1)) return false

        val normA1 = normalizeArtistName(a1)
        val normA2 = normalizeArtistName(a2)
        if (normA1 == "unknown artist" || normA2 == "unknown artist") return true
        return normA1 == normA2 || normA1.contains(normA2) || normA2.contains(normA1)
    }

    fun onTrackDiscovered(
        title: String,
        artist: String,
        album: String,
        pkg: String,
        isYt: Boolean,
        directArtUrl: String? = null
    ) {
        // Strictly reject any YouTube Video playback
        if (YouTubeHelper.isYouTubeVideoPackage(pkg)) return
        if (YouTubeHelper.isYouTubeVideoNotification(pkg, title, artist, album)) return
        if (_uiState.value.filterOnlyYouTubeMusic && !YouTubeHelper.isYouTubeMusic(pkg)) return

        val currentTitle = _uiState.value.trackTitle.trim()
        val currentArtist = _uiState.value.artist.trim()
        val isCurrentlyPlaying = _uiState.value.isActivelyPlaying

        val cleanTitle = title.trim()
        // Deliberately no fallback to the raw artist here: strings like "YouTube Music"
        // (used as a display placeholder) must never be persisted as a real artist
        val cleanArtist = cleanArtistName(artist)
        val cleanAlbum = album.trim()

        val currentIsPlaceholder = isPlaceholderTitle(currentTitle)
        val newIsPlaceholder = isPlaceholderTitle(cleanTitle)

        if (newIsPlaceholder) {
            // Do not replace real track with placeholder
            return
        }

        // A paused session resumed after midnight must count toward the new day;
        // without this the Daily feed shows the carried track while the stored
        // sessionCount stays 0.
        checkDayRollover()

        val sameTrack = isSameTrack(cleanTitle, cleanArtist, currentTitle, currentArtist)

        if (sameTrack) {
            // Check if this same-track discovery event is a track repeat/loop!
            val rawPos = activeController?.playbackState?.position ?: -1L
            val estPos = getEstimatedPlaybackPositionMs()
            val posToCheck = if (rawPos in 0..6000L) rawPos else estPos

            if (checkAndHandleTrackLoop(posToCheck, activeController?.playbackState, "onTrackDiscovered")) {
                return
            }

            if (!isCurrentlyPlaying) {
                // Resume existing session
                _uiState.update {
                    it.copy(
                        isActivelyPlaying = true,
                        sourcePackage = pkg,
                        isYouTubeMusicSource = isYt,
                        artist = if (!isPlaceholderArtist(cleanArtist)) cleanArtist else it.artist,
                        artworkUrl = directArtUrl ?: it.artworkUrl
                    )
                }
                if (currentDbSessionId != null && countedDatesForCurrentSession.add(todayDay.date)) {
                    val anchor = todayDay
                    _uiState.update { it.copy(todaySessionCount = it.todaySessionCount + 1) }
                    scope.launch {
                        repository.incrementSessionCount(anchor.date, anchor.year, anchor.month, anchor.day, anchor.dayOfWeek)
                    }
                    convergeTodayCountFromDb(anchor.date)
                }
                startTicker()
            } else {
                // Same track continuing, update album or details if missing
                _uiState.update {
                    it.copy(
                        trackTitle = cleanTitle,
                        artist = if (!isPlaceholderArtist(cleanArtist)) cleanArtist else it.artist,
                        album = cleanAlbum.ifBlank { it.album },
                        artworkUrl = directArtUrl ?: it.artworkUrl,
                        sourcePackage = pkg,
                        isYouTubeMusicSource = isYt
                    )
                }
            }

            // YT Music exposes the title before the artist when a track is started
            // manually, so the session row is created with a blank artist. Backfill
            // the DB row once the real artist shows up; the UI copy above already
            // picks it up.
            if (!isPlaceholderArtist(cleanArtist) && isPlaceholderArtist(currentArtist) && currentDbSessionId != null) {
                val sid = currentDbSessionId!!
                scope.launch { repository.updateSessionArtist(sid, cleanArtist) }
            }

            if (directArtUrl != null && currentDbSessionId != null) {
                scope.launch {
                    repository.updateSessionArtwork(currentDbSessionId!!, directArtUrl)
                }
            }
            return
        }

        if (currentIsPlaceholder) {
            // Previous title was a placeholder, update current session in-place
            if (isCurrentlyPlaying && currentDbSessionId != null) {
                updateCurrentSessionDetails(cleanTitle, cleanArtist, cleanAlbum, pkg, isYt, directArtUrl)
            } else {
                startNewTrackSession(cleanTitle, cleanArtist, cleanAlbum, pkg, isYt, directArtUrl)
            }
        } else {
            // A genuine new song in playlist has started!
            val prevSessionSec = _uiState.value.currentSessionSeconds
            val prevSid = currentDbSessionId
            if (isCurrentlyPlaying) {
                flushPendingSecondsToDb()
            }
            if (prevSessionSec < 5L && prevSid != null) {
                discardShortSession(prevSid, prevSessionSec)
            }
            currentDbSessionId = null
            startNewTrackSession(cleanTitle, cleanArtist, cleanAlbum, pkg, isYt, directArtUrl)
        }
    }

    private fun startNewTrackSession(
        title: String,
        artist: String,
        album: String,
        pkg: String,
        isYt: Boolean,
        directArtUrl: String? = null
    ) {
        maxObservedPositionMs = 0L
        lastLoopDetectionTimestamp = 0L
        currentSessionPlayCount = 1
        stableTrackDurationMs = activeController?.metadata
            ?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L } ?: 0L

        val today = todayDay
        currentSessionDate = today.date
        currentSessionSecondsByDate.clear()
        currentSessionSecondsByDate[today.date] = 0L
        countedDatesForCurrentSession.clear()
        countedDatesForCurrentSession.add(today.date)

        // Instant heuristic genre for zero-lag UI display and database initialization
        val initialGenre = GenreClassifier.classify(artist, title, album)

        _uiState.update {
            it.copy(
                isActivelyPlaying = true,
                trackTitle = title,
                artist = artist,
                album = album,
                currentGenre = initialGenre,
                artworkUrl = directArtUrl,
                sourcePackage = pkg,
                isYouTubeMusicSource = isYt,
                currentSessionSeconds = 0L,
                trackPositionMs = 0L,
                trackDurationMs = activeController?.metadata
                    ?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L } ?: 0L
            )
        }

        startTicker()

        scope.launch {
            // If the app process restarted while this track kept playing, reattach to
            // the still-open session instead of inserting a duplicate row. The
            // lookup spans all recent sessions (not just today's) so a restart
            // across midnight still finds the open row.
            val resumed = repository.getRecentSessionsSync(20).firstOrNull {
                it.endTime >= System.currentTimeMillis() - RESUME_WINDOW_MS &&
                        isSameTrack(title, artist, it.title, it.artist)
            }

            val sid: Long
            if (resumed != null) {
                sid = resumed.id
                // Continue counting from the pre-restart playback time
                val carriedSeconds = resumed.durationSeconds
                currentSessionPlayCount = resumed.playCount
                currentSessionDate = resumed.date
                countedDatesForCurrentSession.clear()
                countedDatesForCurrentSession.add(resumed.date)
                // Post-restart flushes to a new day count toward that day; if the
                // pre-restart process already counted this continuation (played
                // across midnight before dying), the stored count is already 1
                // and must not increment again.
                if (resumed.date != today.date) {
                    val todayStat = repository.getDailyStatSync(today.date)
                    if ((todayStat?.sessionCount ?: 0) == 0) {
                        repository.incrementSessionCount(today.date, today.year, today.month, today.day, today.dayOfWeek)
                        _uiState.update { it.copy(todaySessionCount = it.todaySessionCount + 1) }
                    }
                    countedDatesForCurrentSession.add(today.date)
                    convergeTodayCountFromDb(today.date)
                }
                _uiState.update {
                    it.copy(currentSessionSeconds = carriedSeconds + it.currentSessionSeconds)
                }
            } else {
                sid = repository.startSession(
                    date = today.date,
                    year = today.year,
                    month = today.month,
                    title = title,
                    artist = artist,
                    album = album,
                    genre = initialGenre,
                    sourcePackage = pkg,
                    artworkUrl = directArtUrl
                )
                repository.incrementSessionCount(today.date, today.year, today.month, today.day, today.dayOfWeek)
                _uiState.update { it.copy(todaySessionCount = it.todaySessionCount + 1) }
                convergeTodayCountFromDb(today.date)
            }
            currentDbSessionId = sid

            // Query verified Spotify / Public API genre asynchronously
            if (!isPlaceholderTitle(title)) {
                val resolvedGenre = MusicGenreResolver.resolveGenre(artist, title, album, context)
                if (resolvedGenre.isNotBlank() && resolvedGenre != initialGenre) {
                    _uiState.update { it.copy(currentGenre = resolvedGenre) }
                    repository.updateSessionGenre(sid, resolvedGenre)
                }

                // Resolve artwork if not already provided
                val resolvedArt = ArtworkResolver.resolveArtwork(context, artist, title, directArtUrl)
                if (!resolvedArt.isNullOrBlank() && resolvedArt != directArtUrl) {
                    _uiState.update { it.copy(artworkUrl = resolvedArt) }
                    repository.updateSessionArtwork(sid, resolvedArt)
                }
            }
        }
    }

    private fun getEstimatedPlaybackPositionMs(): Long {
        val state = activeController?.playbackState ?: return -1L
        if (state.state != PlaybackState.STATE_PLAYING) return state.position
        val updateTime = state.lastPositionUpdateTime
        if (updateTime <= 0L) return state.position
        val timeDelta = SystemClock.elapsedRealtime() - updateTime
        val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1.0f
        return (state.position + (timeDelta * speed).toLong()).coerceAtLeast(0L)
    }

    /**
     * Tracks the largest duration reported for the current track. YT Music
     * occasionally reports a shrinking duration mid-track while position keeps
     * growing; those backwards samples are ignored so progress rendering and
     * loop detection never reason from bad input.
     */
    private fun observeDurationSample(rawMs: Long): Long {
        if (rawMs > 0L && (stableTrackDurationMs <= 0L || rawMs >= stableTrackDurationMs)) {
            stableTrackDurationMs = rawMs
        }
        return stableTrackDurationMs
    }

    /**
     * Publishes the live track position and duration to the UI so the live
     * tracking card can render a moving playback timeline. Position comes from
     * the same estimate the loop detector uses; while paused the values freeze
     * because the ticker only runs during active playback. Position is clamped
     * at the stable duration so a flapping metadata shrink can never render a
     * position past the end of the track.
     */
    private fun updatePlaybackProgress(positionMs: Long) {
        val rawDurationMs = activeController?.metadata
            ?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val durationMs = observeDurationSample(rawDurationMs)
        val clampedPos = if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        _uiState.update {
            it.copy(
                trackPositionMs = clampedPos,
                trackDurationMs = durationMs.coerceAtLeast(0L)
            )
        }
    }

    /**
     * Detects when the currently playing track loops and restarts from the beginning.
     * A loop is absorbed into the current session: listening time keeps accumulating
     * and no new play is recorded, so repeated tracks never duplicate in the feed.
     * Returns true when a loop was detected so callers skip new-track handling.
     */
    fun checkAndHandleTrackLoop(
        controllerPosMs: Long,
        state: PlaybackState?,
        triggerSource: String
    ): Boolean {
        if (!_uiState.value.isActivelyPlaying) return false
        if (isPlaceholderTitle(_uiState.value.trackTitle)) return false

        val now = System.currentTimeMillis()
        if (now - lastLoopDetectionTimestamp < 5000L) {
            // Debounce loop triggers within 5 seconds
            return false
        }

        val sessionSec = _uiState.value.currentSessionSeconds
        val rawDurationMs = activeController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L } ?: -1L
        // Backwards-moving durations mid-track are metadata flaps, not real
        // track changes; the stable maximum keeps loop reasoning honest.
        val durationMs = if (rawDurationMs > 0L) observeDurationSample(rawDurationMs) else stableTrackDurationMs.takeIf { it > 0L } ?: -1L

        // Update maximum position reached during this loop iteration
        if (controllerPosMs > maxObservedPositionMs) {
            maxObservedPositionMs = controllerPosMs
        }

        // Loop detection criteria:
        // 1. Position-based rewind: We have progressed at least 15s (or >= 60% of track),
        //    and position suddenly jumps back to near beginning (0..6s).
        val minPositionThresholdMs = if (durationMs > 0L) {
            minOf(20_000L, (durationMs * 0.60f).toLong()).coerceAtLeast(10_000L)
        } else {
            15_000L
        }

        val isPositionRewoundToStart = maxObservedPositionMs >= minPositionThresholdMs &&
                controllerPosMs in 0L..6_000L &&
                sessionSec >= 15L

        // 2. Duration-based wrap: Session time reached or exceeded the track duration,
        //    and position is near beginning (0..8s) or rewound by at least 15s.
        //    maxObservedPositionMs > 8s proves the position has left the near-start
        //    band since the previous loop; without it the per-second ticker re-fires
        //    here for ~8s after every wrap (sessionSec keeps accumulating across
        //    loops) and records the same loop twice.
        val effectiveDurationSec = if (durationMs > 0L) durationMs / 1000L else 0L
        val isDurationWrap = durationMs in 15_000L..1_800_000L &&
                sessionSec >= (effectiveDurationSec - 3L) &&
                maxObservedPositionMs > 8_000L &&
                (controllerPosMs in 0L..8_000L || controllerPosMs < maxObservedPositionMs - 15_000L)

        // 3. Significant rewind when position was near end (> 80% of song) and dropped below 10s
        val isNearEndRewind = durationMs > 15_000L &&
                maxObservedPositionMs >= (durationMs * 0.80f).toLong() &&
                controllerPosMs <= 10_000L &&
                sessionSec >= 15L

        if (isPositionRewoundToStart || isDurationWrap || isNearEndRewind) {
            Log.d("MusicTrackerEngine", "Track loop absorbed into current session via $triggerSource (pos=$controllerPosMs, maxPos=$maxObservedPositionMs, sessionSec=$sessionSec, durationMs=$durationMs)")
            resetLoopTracking()
            recordLoop()
            return true
        }

        return false
    }

    /**
     * Counts a detected loop as an additional play of the current track and
     * persists it to the session row so grouping can show repeat labels like
     * "3x". Loop detection needs at least 12s of session time, by which point
     * the session row always exists.
     */
    private fun recordLoop() {
        currentSessionPlayCount++
        currentDbSessionId?.let { sid ->
            scope.launch { repository.incrementSessionPlayCount(sid) }
        }
    }

    private fun resetLoopTracking() {
        maxObservedPositionMs = 0L
        lastLoopDetectionTimestamp = System.currentTimeMillis()
    }

    private fun updateCurrentSessionDetails(
        title: String,
        artist: String,
        album: String,
        pkg: String,
        isYt: Boolean,
        directArtUrl: String? = null
    ) {
        val initialGenre = GenreClassifier.classify(artist, title, album)

        _uiState.update {
            it.copy(
                trackTitle = title,
                artist = artist,
                album = album,
                currentGenre = initialGenre,
                artworkUrl = directArtUrl ?: it.artworkUrl,
                sourcePackage = pkg,
                isYouTubeMusicSource = isYt
            )
        }

        val sid = currentDbSessionId
        scope.launch {
            if (sid != null) {
                repository.updateSessionDetails(sid, title, artist, album, initialGenre, directArtUrl)
            }
            // Resolve genre asynchronously
            val resolvedGenre = MusicGenreResolver.resolveGenre(artist, title, album, context)
            if (resolvedGenre.isNotBlank()) {
                _uiState.update { it.copy(currentGenre = resolvedGenre) }
                if (sid != null) {
                    repository.updateSessionGenre(sid, resolvedGenre)
                }
            }
            // Resolve artwork asynchronously
            val resolvedArt = ArtworkResolver.resolveArtwork(context, artist, title, directArtUrl)
            if (!resolvedArt.isNullOrBlank()) {
                _uiState.update { it.copy(artworkUrl = resolvedArt) }
                if (sid != null) {
                    repository.updateSessionArtwork(sid, resolvedArt)
                }
            }
        }
    }

    private fun isYouTubePackage(pkg: String): Boolean {
        return YouTubeHelper.isYouTubeMusic(pkg)
    }

    fun isPlaceholderTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val lower = title.lowercase(Locale.ROOT).trim()
        return lower == "no music playing" ||
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

    fun isPlaceholderArtist(artist: String?): Boolean {
        if (artist.isNullOrBlank()) return true
        val lower = artist.lowercase(Locale.ROOT).trim()
        return lower == "waiting for youtube music" ||
                lower == "media player" ||
                lower == "unknown artist"
    }

    fun onPlaybackStarted(
        title: String,
        artist: String,
        album: String,
        pkg: String,
        isYt: Boolean
    ) {
        onTrackDiscovered(title, artist, album, pkg, isYt)
    }

    /**
     * Removes a discarded (< 5s) session and rolls back the seconds it already
     * contributed to daily stats and the live "today" counter, so skipped tracks
     * and ghost sessions do not inflate listening time. Sessions that played
     * across midnight contributed to more than one day; each day rolls back
     * only what it received.
     */
    private fun discardShortSession(sid: Long, seconds: Long) {
        currentDbSessionId = null
        val sessionDate = currentSessionDate
        val perDate = currentSessionSecondsByDate.toMap()
        val mappedSum = perDate.values.sum()
        // Pre-restart seconds of a resumed session were never tracked in the
        // map; they belong to the start date this process did not observe.
        val untracked = (seconds - mappedSum).coerceAtLeast(0L)
        val counted = countedDatesForCurrentSession.toSet()
        scope.launch {
            repository.deleteSession(sid)
            for ((date, secs) in perDate) {
                if (secs > 0L) repository.subtractListeningTime(date, secs)
            }
            if (untracked > 0L) repository.subtractListeningTime(sessionDate, untracked)
            for (date in counted) {
                repository.decrementSessionCount(date)
            }
        }
        _uiState.update {
            val todayRollback = perDate[todayDay.date] ?: 0L
            it.copy(
                todayTotalSeconds = (it.todayTotalSeconds - todayRollback).coerceAtLeast(0L),
                todaySessionCount = (if (todayDay.date in counted) {
                    it.todaySessionCount - 1
                } else {
                    it.todaySessionCount
                }).coerceAtLeast(0)
            )
        }
        currentSessionSecondsByDate.clear()
        countedDatesForCurrentSession.clear()
    }

    fun onPlaybackPausedOrStopped() {
        if (!_uiState.value.isActivelyPlaying) return

        stopTicker()
        flushPendingSecondsToDb()

        val sessionTotalSec = _uiState.value.currentSessionSeconds
        val sid = currentDbSessionId
        if (sessionTotalSec < 5L && sid != null) {
            // Discard session shorter than 5 seconds (ghost/skip)
            discardShortSession(sid, sessionTotalSec)
            _uiState.update {
                it.copy(
                    isActivelyPlaying = false,
                    currentSessionSeconds = 0L
                )
            }
        } else {
            // Keep currentSessionSeconds so looping or unpausing resumes seamlessly without resetting timer!
            _uiState.update {
                it.copy(
                    isActivelyPlaying = false
                )
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                checkDayRollover()

                // Increments run inside update so a concurrent DB reload's
                // write can never be reverted by a stale-base copy
                _uiState.update {
                    it.copy(
                        currentSessionSeconds = it.currentSessionSeconds + 1,
                        todayTotalSeconds = it.todayTotalSeconds + 1
                    )
                }

                pendingSecondsForDb.incrementAndGet()

                // Check for real-time track looping/repeating during active playback.
                // Posted to the main handler so loop detection is serialized with the
                // playback/metadata callbacks and can never double-fire across threads.
                mainHandler.post {
                    val estPos = getEstimatedPlaybackPositionMs()
                    val rawPos = activeController?.playbackState?.position ?: -1L
                    val posToCheck = if (rawPos in 0L..6000L) rawPos else estPos
                    checkAndHandleTrackLoop(posToCheck, activeController?.playbackState, "ticker")
                    updatePlaybackProgress(posToCheck)
                }

                // Every 5 seconds, flush to DB to prevent SQLite disk thrashing while keeping data fresh
                if (pendingSecondsForDb.get() >= 5) {
                    flushPendingSecondsToDb()
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Writes pending seconds to the daily stats row for the anchor day
     * (todayDay), which is still the day the seconds were listened on: the
     * rollover re-anchors it only after draining the buffer, and the pause /
     * 5-second flushes run on the anchor they ticked under. getAndSet(0) is
     * atomic so concurrent flushes (ticker, pause, rollover) can never
     * double-count.
     */
    private fun flushPendingSecondsToDb() {
        val secToSave = pendingSecondsForDb.getAndSet(0L)
        if (secToSave <= 0L) return
        val anchor = todayDay
        val sessionId = currentDbSessionId
        val sessionTotalSec = _uiState.value.currentSessionSeconds
        // Atomic merge so a concurrent flush (ticker vs. day-rollover scan on
        // another thread) can never lose seconds to a read-modify-write race.
        currentSessionSecondsByDate.merge(anchor.date, secToSave) { a, b -> a + b }

        scope.launch {
            repository.addListeningTime(
                date = anchor.date,
                year = anchor.year,
                month = anchor.month,
                day = anchor.day,
                dayOfWeek = anchor.dayOfWeek,
                additionalSeconds = secToSave
            )
            if (sessionId != null) {
                repository.updateSession(
                    sessionId = sessionId,
                    endTime = System.currentTimeMillis(),
                    durationSeconds = sessionTotalSec
                )
            }
        }
    }

    /**
     * Simulates the current active track looping (useful for automated testing and UI verification).
     * A loop stays inside the current session and counts as an additional play.
     */
    fun simulateTrackLoop() {
        if (!_uiState.value.isActivelyPlaying) return
        resetLoopTracking()
        recordLoop()
    }

    private data class DayKey(
        val date: String,
        val year: Int,
        val month: Int,
        val day: Int,
        val dayOfWeek: Int
    )

    private fun currentDayKey(): DayKey {
        val cal = Calendar.getInstance()
        return DayKey(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time),
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        )
    }

    companion object {
        private const val PREFS_NAME = "tracker_settings"
        private const val KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes"
        private const val DEFAULT_DAILY_GOAL_MINUTES = 60

        // How long after a session's last DB flush a process restart may still
        // reattach to it instead of starting a duplicate session
        private const val RESUME_WINDOW_MS = 120_000L

        @Volatile
        private var INSTANCE: MusicTrackerEngine? = null

        fun getInstance(context: Context, repository: MusicTrackerRepository): MusicTrackerEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = MusicTrackerEngine(context.applicationContext, repository)
                INSTANCE = instance
                instance
            }
        }

        fun getExisting(): MusicTrackerEngine? = INSTANCE
    }
}
