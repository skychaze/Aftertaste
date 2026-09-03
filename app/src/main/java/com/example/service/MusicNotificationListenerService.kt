package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.tracker.MusicTrackerEngine
import com.example.tracker.YouTubeHelper
import java.util.Locale

class MusicNotificationListenerService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            notifyEngine(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Notification listener connected!")
        try {
            sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, MusicNotificationListenerService::class.java)
            sessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)

            // Initial query
            val activeSessions = sessionManager?.getActiveSessions(component)
            notifyEngine(activeSessions)

            // Check active notifications as well
            checkActiveNotifications()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sessionManager = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val pkg = sbn.packageName ?: ""
        // Completely ignore all YouTube Video notifications
        if (YouTubeHelper.isYouTubeVideoPackage(pkg)) return

        if (YouTubeHelper.isYouTubeMusic(pkg) || pkg.contains("music", ignoreCase = true) || pkg.contains("spotify", ignoreCase = true) || pkg.contains("audio", ignoreCase = true)) {
            extractAndNotifyMedia(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val pkg = sbn.packageName ?: ""
        if (YouTubeHelper.isYouTubeVideoPackage(pkg)) return

        if (YouTubeHelper.isYouTubeMusic(pkg) || pkg.contains("music", ignoreCase = true) || pkg.contains("spotify", ignoreCase = true) || pkg.contains("audio", ignoreCase = true)) {
            refreshSessions()
        }
    }

    fun extractAndNotifyMedia(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: ""
            // Guard against YouTube video apps (main YouTube, kids, tv)
            if (YouTubeHelper.isYouTubeVideoPackage(pkg)) return

            val notif = sbn.notification ?: return
            val extras = notif.extras ?: return

            // Check if there is a MediaSession token attached to the notification
            val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
            }

            if (token != null) {
                val controller = MediaController(this, token)
                // Ensure the controller does not belong to a YouTube Video player
                if (!YouTubeHelper.isYouTubeVideoPackage(controller.packageName)) {
                    notifyEngine(listOf(controller))
                }
            } else {
                // Non-token notification: MUST be a verified ongoing transport notification with active music audio
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                val isMusicPlaying = audioManager?.isMusicActive == true
                val isTransport = notif.category == Notification.CATEGORY_TRANSPORT || notif.category == Notification.CATEGORY_SERVICE
                val hasActions = notif.actions?.isNotEmpty() == true

                if (!sbn.isOngoing || !isMusicPlaying || (!isTransport && !hasActions)) {
                    // Not an active media playback notification, ignore to prevent false tracking
                    return
                }

                // Extract metadata directly from notification extras as fallback
                val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                val rawSubText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

                // Filter out YouTube video metadata or non-music notifications
                if (YouTubeHelper.isYouTubeVideoNotification(pkg, rawTitle, rawText, rawSubText)) {
                    return
                }

                if (!rawTitle.isNullOrBlank() && 
                    !rawTitle.equals("YouTube Music", ignoreCase = true) && 
                    !rawTitle.equals("YouTube", ignoreCase = true) &&
                    !rawTitle.equals("Music", ignoreCase = true) &&
                    !rawTitle.contains("download", ignoreCase = true) &&
                    !rawTitle.contains("uploaded", ignoreCase = true)
                ) {
                    val artist = if (!rawText.isNullOrBlank()) rawText else (rawSubText ?: "")
                    val album = rawSubText ?: ""
                    val isYt = YouTubeHelper.isYouTubeMusic(pkg)

                    @Suppress("DEPRECATION")
                    val notifBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        extras.getParcelable(Notification.EXTRA_PICTURE, android.graphics.Bitmap::class.java)
                            ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON, android.graphics.Bitmap::class.java)
                    } else {
                        extras.getParcelable(Notification.EXTRA_PICTURE) as? android.graphics.Bitmap
                            ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? android.graphics.Bitmap
                    }

                    MusicTrackerEngine.getExisting()?.onTrackDetectedFromNotification(
                        title = rawTitle,
                        artist = artist,
                        album = album,
                        pkg = pkg,
                        isYt = isYt,
                        bitmap = notifBitmap
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed extracting notification media: ${e.message}")
        }
    }

    private fun checkActiveNotifications() {
        try {
            val activeNotifs = activeNotifications ?: return
            for (sbn in activeNotifs) {
                val pkg = sbn.packageName ?: ""
                if (YouTubeHelper.isYouTubeVideoPackage(pkg)) continue
                if (YouTubeHelper.isYouTubeMusic(pkg) || pkg.contains("music", ignoreCase = true) || pkg.contains("spotify", ignoreCase = true)) {
                    extractAndNotifyMedia(sbn)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshSessions() {
        try {
            val component = ComponentName(this, MusicNotificationListenerService::class.java)
            val activeSessions = sessionManager?.getActiveSessions(component)?.filter { ctrl ->
                !YouTubeHelper.isYouTubeVideoPackage(ctrl.packageName)
            }
            notifyEngine(activeSessions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notifyEngine(controllers: List<MediaController>?) {
        MusicTrackerEngine.getExisting()?.onSessionsChangedFromService(controllers)
    }

    companion object {
        private const val TAG = "MusicNotifService"
        @Volatile
        var instance: MusicNotificationListenerService? = null
    }
}
