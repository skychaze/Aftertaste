package com.example.tracker

import java.util.Locale

object YouTubeHelper {
    // Official YouTube Music Android package
    const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    // Standard YouTube Video app packages
    const val PACKAGE_YOUTUBE_MAIN = "com.google.android.youtube"
    const val PACKAGE_YOUTUBE_KIDS = "com.google.android.apps.youtube.kids"
    const val PACKAGE_YOUTUBE_TV = "com.google.android.apps.youtube.unplugged"

    /**
     * Returns true ONLY if the package is YouTube Music.
     * Matches the official YouTube Music app as well as third-party/patched YouTube Music builds.
     */
    fun isYouTubeMusic(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase(Locale.ROOT).trim()
        return lower == PACKAGE_YOUTUBE_MUSIC ||
                lower.contains("youtube.music") ||
                lower.contains("ytmusic")
    }

    /**
     * Returns true if the package is specifically YouTube Video (main app, kids, tv, or video player).
     * These must NEVER be tracked as YouTube Music.
     */
    fun isYouTubeVideoPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase(Locale.ROOT).trim()
        if (isYouTubeMusic(lower)) return false
        return lower == PACKAGE_YOUTUBE_MAIN ||
                lower == PACKAGE_YOUTUBE_KIDS ||
                lower == PACKAGE_YOUTUBE_TV ||
                lower.contains("youtube")
    }

    /**
     * Returns true if notification text or metadata indicates a YouTube video,
     * channel upload, video recommendation, or non-YouTube Music notification.
     */
    fun isYouTubeVideoNotification(
        pkg: String?,
        title: String? = null,
        text: String? = null,
        subText: String? = null
    ): Boolean {
        if (isYouTubeVideoPackage(pkg)) return true

        val combined = "$title $text $subText".lowercase(Locale.ROOT)
        // If not explicit YouTube Music, check if title/text refers to YouTube video/channel
        if (!isYouTubeMusic(pkg)) {
            if (combined.contains("• youtube") ||
                combined.contains("- youtube") ||
                combined.contains("| youtube") ||
                combined.contains("youtube.com") ||
                combined.contains("uploaded:") ||
                combined.contains("uploaded a video") ||
                combined.contains("new video") ||
                combined.contains("premiere") ||
                combined.contains("livestream") ||
                combined.contains("subscribed to")
            ) {
                return true
            }
        }
        return false
    }
}
