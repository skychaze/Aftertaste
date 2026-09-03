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
import java.util.Date
import java.util.Locale

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
    val todayTotalSeconds: Long = 0L,
    val todaySessionCount: Int = 0,
    val isNotificationAccessGranted: Boolean = false,
    val isSimulationActive: Boolean = false,
    val filterOnlyYouTubeMusic: Boolean = true,
    val dailyGoalMinutes: Int = 60
)

class MusicTrackerEngine private constructor(
    private val context: Context,
    private val repository: MusicTrackerRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _uiState = MutableStateFlow(TrackerUiState())
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var currentDbSessionId: Long? = null
    private var pendingSecondsForDb: Long = 0L
    private var activeController: MediaController? = null

    // Track loop detection state
    private var maxObservedPositionMs: Long = 0L
    private var lastObservedPositionMs: Long = 0L
    private var lastLoopDetectionTimestamp: Long = 0L

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
        val todayStr = getTodayDateString()
        val stat = repository.getDailyStatSync(todayStr)
        if (stat != null) {
            _uiState.update {
                it.copy(
                    todayTotalSeconds = stat.totalPlayTimeSeconds,
                    todaySessionCount = stat.sessionCount
                )
            }
        }
    }

    fun setFilterOnlyYouTubeMusic(onlyYt: Boolean) {
        _uiState.update { it.copy(filterOnlyYouTubeMusic = onlyYt) }
        scanActiveMediaSessions()
    }

    fun setDailyGoalMinutes(minutes: Int) {
        _uiState.update { it.copy(dailyGoalMinutes = minutes) }
    }

    fun scanActiveMediaSessions() {
        checkPermission()
        if (_uiState.value.isSimulationActive) return

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
            if (_uiState.value.isSimulationActive) return@post
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
        if (_uiState.value.isSimulationActive) return

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

            val artUri = metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

            val artBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            val cachedArtUrl = if (artBitmap != null) {
                ArtworkResolver.saveBitmapToCache(context, artist, title, artBitmap)
            } else artUri

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

            val artUri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

            val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            val cachedArtUrl = if (artBitmap != null) {
                ArtworkResolver.saveBitmapToCache(context, artist, title, artBitmap)
            } else artUri

            onTrackDiscovered(title, artist, rawAlbum, pkg, isYt, directArtUrl = cachedArtUrl)
        }
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
        val cleanArtist = cleanArtistName(artist).ifBlank { artist.trim() }
        val cleanAlbum = album.trim()

        val currentIsPlaceholder = isPlaceholderTitle(currentTitle)
        val newIsPlaceholder = isPlaceholderTitle(cleanTitle)

        if (newIsPlaceholder) {
            // Do not replace real track with placeholder
            return
        }

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
                        artworkUrl = directArtUrl ?: it.artworkUrl
                    )
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
        lastObservedPositionMs = 0L
        lastLoopDetectionTimestamp = 0L

        val todayStr = getTodayDateString()
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

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
                currentSessionSeconds = 0L
            )
        }

        startTicker()

        scope.launch {
            val sid = repository.startSession(
                date = todayStr,
                year = year,
                month = month,
                title = title,
                artist = artist,
                album = album,
                genre = initialGenre,
                sourcePackage = pkg,
                artworkUrl = directArtUrl
            )
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
     * Checks if the currently playing music has completed a loop and restarted from the beginning.
     * When YouTube Music or any music player repeats a track:
     * 1. The playback position rewinds from near the end (or after playing for >= 15s) back to 0..6 seconds.
     * 2. Or the elapsed session seconds reach the track duration and playback restarts.
     * 3. Or a repeated play event is received for the same track after substantial playback progress.
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
        val durationMs = activeController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L } ?: -1L
        val effectiveDurationSec = if (durationMs > 0L) durationMs / 1000L else 0L

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
        val isDurationWrap = durationMs in 15_000L..1_800_000L &&
                sessionSec >= (effectiveDurationSec - 3L) &&
                (controllerPosMs in 0L..8_000L || controllerPosMs < maxObservedPositionMs - 15_000L)

        // 3. Significant rewind when position was near end (> 80% of song) and dropped below 10s
        val isNearEndRewind = durationMs > 15_000L &&
                maxObservedPositionMs >= (durationMs * 0.80f).toLong() &&
                controllerPosMs <= 10_000L &&
                sessionSec >= 15L

        if (isPositionRewoundToStart || isDurationWrap || isNearEndRewind) {
            Log.d("MusicTrackerEngine", "Track loop detected via $triggerSource (pos=$controllerPosMs, maxPos=$maxObservedPositionMs, sessionSec=$sessionSec, durationMs=$durationMs)")
            val title = _uiState.value.trackTitle
            val artist = _uiState.value.artist
            val album = _uiState.value.album
            val pkg = _uiState.value.sourcePackage
            val isYt = _uiState.value.isYouTubeMusicSource
            val artUrl = _uiState.value.artworkUrl

            onTrackLoopDetected(title, artist, album, pkg, isYt, artUrl)
            return true
        }

        lastObservedPositionMs = controllerPosMs
        return false
    }

    private fun onTrackLoopDetected(
        title: String,
        artist: String,
        album: String,
        pkg: String,
        isYt: Boolean,
        directArtUrl: String?
    ) {
        lastLoopDetectionTimestamp = System.currentTimeMillis()
        maxObservedPositionMs = 0L
        lastObservedPositionMs = 0L

        // 1. Complete and flush the current session in the database
        flushPendingSecondsToDb()

        // 2. Reset session timer for the new loop (today's listening time continues uninterrupted)
        val todayStr = getTodayDateString()
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val genre = _uiState.value.currentGenre.ifBlank { GenreClassifier.classify(artist, title, album) }
        val artUrl = directArtUrl ?: _uiState.value.artworkUrl

        _uiState.update {
            it.copy(
                currentSessionSeconds = 0L,
                isActivelyPlaying = true,
                trackTitle = title,
                artist = artist,
                album = album,
                artworkUrl = artUrl,
                currentGenre = genre
            )
        }

        // 3. Start a new session record in Room database for this repeated play
        scope.launch {
            val newSid = repository.startSession(
                date = todayStr,
                year = year,
                month = month,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                sourcePackage = pkg,
                artworkUrl = artUrl
            )
            currentDbSessionId = newSid
        }
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
     * and ghost sessions do not inflate listening time.
     */
    private fun discardShortSession(sid: Long, seconds: Long) {
        currentDbSessionId = null
        scope.launch {
            repository.deleteSession(sid)
            repository.subtractListeningTime(getTodayDateString(), seconds)
        }
        _uiState.update {
            it.copy(todayTotalSeconds = (it.todayTotalSeconds - seconds).coerceAtLeast(0L))
        }
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
                val newSessionSec = _uiState.value.currentSessionSeconds + 1
                val newTodaySec = _uiState.value.todayTotalSeconds + 1

                _uiState.update {
                    it.copy(
                        currentSessionSeconds = newSessionSec,
                        todayTotalSeconds = newTodaySec
                    )
                }

                pendingSecondsForDb += 1

                // Check for real-time track looping/repeating during active playback
                if (!_uiState.value.isSimulationActive) {
                    val estPos = getEstimatedPlaybackPositionMs()
                    val rawPos = activeController?.playbackState?.position ?: -1L
                    val posToCheck = if (rawPos in 0L..6000L) rawPos else estPos
                    checkAndHandleTrackLoop(posToCheck, activeController?.playbackState, "ticker")
                }

                // Every 5 seconds, flush to DB to prevent SQLite disk thrashing while keeping data fresh
                if (pendingSecondsForDb >= 5) {
                    flushPendingSecondsToDb()
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun flushPendingSecondsToDb() {
        if (pendingSecondsForDb <= 0L) return
        val secToSave = pendingSecondsForDb
        pendingSecondsForDb = 0L

        val todayStr = getTodayDateString()
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val sessionId = currentDbSessionId
        val sessionTotalSec = _uiState.value.currentSessionSeconds

        scope.launch {
            repository.addListeningTime(
                date = todayStr,
                year = year,
                month = month,
                day = day,
                dayOfWeek = dayOfWeek,
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

    // Interactive simulation mode for emulator / test demo
    fun toggleSimulation() {
        val nextSimState = !_uiState.value.isSimulationActive
        if (nextSimState) {
            _uiState.update { it.copy(isSimulationActive = true) }
            onPlaybackStarted(
                title = "Starboy (ft. Daft Punk)",
                artist = "The Weeknd • YouTube Music",
                album = "Starboy",
                pkg = "com.google.android.apps.youtube.music",
                isYt = true
            )
        } else {
            _uiState.update { it.copy(isSimulationActive = false) }
            onPlaybackPausedOrStopped()
            scanActiveMediaSessions()
        }
    }

    /**
     * Simulates repeating the current active track (useful for automated testing and UI verification).
     */
    fun simulateTrackLoop() {
        if (!_uiState.value.isActivelyPlaying) return
        val title = _uiState.value.trackTitle
        val artist = _uiState.value.artist
        val album = _uiState.value.album
        val pkg = _uiState.value.sourcePackage
        val isYt = _uiState.value.isYouTubeMusicSource
        val artUrl = _uiState.value.artworkUrl
        onTrackLoopDetected(title, artist, album, pkg, isYt, artUrl)
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    companion object {
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
