package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MonthlyStat(
    val month: Int, // 1 - 12
    val monthName: String,
    val totalSeconds: Long,
    val activeDays: Int
)

data class YearlySummary(
    val year: Int,
    val totalSeconds: Long,
    val activeDaysCount: Int,
    val averageDailySeconds: Long,
    val monthlyStats: List<MonthlyStat>,
    val peakMonthName: String,
    val peakMonthSeconds: Long
)

class MusicTrackerRepository(private val dao: MusicTrackerDao) {

    // Both the engine and the view model run boot cleanup; the read-then-write
    // below must never interleave with itself or one deleted session would be
    // subtracted from daily_stats twice.
    private val cleanupMutex = Mutex()

    fun getTodayStat(date: String): Flow<DailyStatEntity?> = dao.getDailyStat(date)

    fun getRecentDailyStats(limit: Int = 14): Flow<List<DailyStatEntity>> =
        dao.getRecentDailyStats(limit)

    fun getDailyStatsForYear(year: Int): Flow<List<DailyStatEntity>> =
        dao.getDailyStatsForYear(year)

    fun getAllDailyStats(): Flow<List<DailyStatEntity>> = dao.getAllDailyStats()

    fun getTotalSecondsForYear(year: Int): Flow<Long> = dao.getTotalSecondsForYear(year)

    fun getActiveDaysCountForYear(year: Int): Flow<Int> = dao.getActiveDaysCountForYear(year)

    fun getTotalLifetimeSeconds(): Flow<Long> = dao.getTotalLifetimeSeconds()

    fun getRecentSessions(limit: Int = 50): Flow<List<PlaybackSessionEntity>> =
        dao.getRecentSessions(limit)

    fun getAllSessions(): Flow<List<PlaybackSessionEntity>> = dao.getAllSessions()

    fun getSessionsForYear(year: Int): Flow<List<PlaybackSessionEntity>> =
        dao.getSessionsForYear(year)

    fun getSessionsForMonth(year: Int, month: Int): Flow<List<PlaybackSessionEntity>> =
        dao.getSessionsForMonth(year, month)

    suspend fun getDailyStatSync(date: String): DailyStatEntity? =
        dao.getDailyStatSync(date)

    suspend fun addListeningTime(
        date: String,
        year: Int,
        month: Int,
        day: Int,
        dayOfWeek: Int,
        additionalSeconds: Long
    ) {
        // Atomic increment so concurrent flushes (ticker + pause) cannot lose seconds
        val updated = dao.addListeningSeconds(date, additionalSeconds, System.currentTimeMillis())
        if (updated == 0) {
            // sessionCount stays 0 here: incrementSessionCount owns row creation for counts
            dao.insertOrUpdateDaily(
                DailyStatEntity(
                    date = date,
                    year = year,
                    month = month,
                    day = day,
                    dayOfWeek = dayOfWeek,
                    totalPlayTimeSeconds = additionalSeconds,
                    sessionCount = 0,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun subtractListeningTime(date: String, secondsToRemove: Long) {
        if (secondsToRemove > 0L) {
            dao.subtractListeningSeconds(date, secondsToRemove)
        }
    }

    suspend fun incrementSessionCount(date: String, year: Int, month: Int, day: Int, dayOfWeek: Int) {
        // Atomic increment so a concurrent flush creating the row cannot double-count
        val updated = dao.addSessionCount(date, System.currentTimeMillis())
        if (updated == 0) {
            dao.insertOrUpdateDaily(
                DailyStatEntity(
                    date = date,
                    year = year,
                    month = month,
                    day = day,
                    dayOfWeek = dayOfWeek,
                    totalPlayTimeSeconds = 0L,
                    sessionCount = 1,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun decrementSessionCount(date: String) {
        dao.subtractSessionCount(date)
    }

    suspend fun startSession(
        date: String,
        year: Int,
        month: Int,
        title: String?,
        artist: String?,
        album: String?,
        genre: String? = null,
        artworkUrl: String? = null,
        sourcePackage: String
    ): Long {
        val now = System.currentTimeMillis()
        val detectedGenre = genre ?: com.example.tracker.GenreClassifier.classify(artist, title, album)
        val session = PlaybackSessionEntity(
            date = date,
            year = year,
            month = month,
            startTime = now,
            endTime = now,
            durationSeconds = 0L,
            title = title,
            artist = artist,
            album = album,
            genre = detectedGenre,
            artworkUrl = artworkUrl,
            sourcePackage = sourcePackage
        )
        return dao.insertSession(session)
    }

    suspend fun updateSession(sessionId: Long, endTime: Long, durationSeconds: Long) {
        dao.updateSession(sessionId, endTime, durationSeconds)
    }

    suspend fun updateSessionGenre(sessionId: Long, genre: String) {
        dao.updateSessionGenre(sessionId, genre)
    }

    suspend fun updateSessionArtwork(sessionId: Long, artworkUrl: String) {
        dao.updateSessionArtwork(sessionId, artworkUrl)
    }

    /** Backfills a missing artist on an existing session row. */
    suspend fun updateSessionArtist(sessionId: Long, artist: String) {
        dao.updateSessionArtist(sessionId, artist)
    }

    /** Records absorbed track loops as extra plays on the session row. */
    suspend fun incrementSessionPlayCount(sessionId: Long, plays: Int = 1) {
        if (plays > 0) {
            dao.incrementSessionPlayCount(sessionId, plays)
        }
    }

    suspend fun updateSessionDetails(
        sessionId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        artworkUrl: String? = null
    ) {
        dao.updateSessionDetails(sessionId, title, artist, album, genre, artworkUrl)
    }

    suspend fun cleanCorruptSessions() = cleanupMutex.withLock {
        // Subtractive cleanup: daily_stats already holds the exact per-day splits
        // flushed live by the engine (including post-midnight portions of spanning
        // sessions). Recomputing totals from sessions by start date would collapse
        // those splits back onto the start day, so deletions adjust daily_stats
        // incrementally instead and no full resync runs here.
        val corruptTitles = setOf("Background Audio Active", "No music playing")
        val all = dao.getAllSessionsSync()
        val toDelete = all.filter { s ->
            s.title == null || s.title!!.isBlank() ||
                    s.title in corruptTitles ||
                    com.example.tracker.YouTubeHelper.isYouTubeVideoPackage(s.sourcePackage)
        }
        for (s in toDelete) {
            subtractSessionContribution(s)
            dao.deleteSession(s.id)
        }
        dao.cleanPlaceholderArtists()
        dao.deleteEmptyDailyStats()
    }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun dateOf(millis: Long): String = dateFmt.format(Date(millis))

    /** Rolls back one deleted session's daily_stats contribution, spanning-aware. */
    private suspend fun subtractSessionContribution(s: PlaybackSessionEntity) {
        if (s.durationSeconds > 0L) {
            subtractListeningTime(s.date, s.durationSeconds)
        }
        decrementSessionCount(s.date)
        // Sessions that played across midnight were also counted on their end day
        // by the rollover increment; roll that back too so counts stay exact.
        val endDate = dateOf(s.endTime)
        if (endDate != s.date) {
            decrementSessionCount(endDate)
        }
    }

    suspend fun getRecentSessionsSync(limit: Int = 20): List<PlaybackSessionEntity> =
        dao.getRecentSessionsSync(limit)

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSession(sessionId)
    }

    suspend fun deleteShortSessions(activeSessionId: Long = -1L) = cleanupMutex.withLock {
        val shorts = dao.getShortSessionsSync(activeSessionId)
        for (s in shorts) {
            subtractSessionContribution(s)
            dao.deleteSession(s.id)
        }
        dao.deleteEmptyDailyStats()
    }

    suspend fun clearAll() {
        dao.clearDailyStats()
        dao.clearSessions()
    }

    suspend fun seedSampleAnalyticsForYear(targetYear: Int) {
        // Generates realistic playback history for the current year
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Seed rich catalog of sample artists with genre classifications
        data class SeedTrack(val artist: String, val title: String, val genre: String)
        val sampleCatalog = listOf(
            SeedTrack("Taylor Swift", "Anti-Hero", "Pop"),
            SeedTrack("Daft Punk", "Get Lucky", "Electronic"),
            SeedTrack("The Weeknd", "Blinding Lights", "R&B / Soul"),
            SeedTrack("Kendrick Lamar", "HUMBLE.", "Hip-Hop / Rap"),
            SeedTrack("Billie Eilish", "Birds of a Feather", "Pop"),
            SeedTrack("Coldplay", "Viva La Vida", "Rock"),
            SeedTrack("Dua Lipa", "Levitating", "Pop"),
            SeedTrack("Travis Scott", "SICKO MODE", "Hip-Hop / Rap"),
            SeedTrack("Calvin Harris", "Summer", "Electronic"),
            SeedTrack("Lofi Girl", "Cozy Coffee Morning", "Lo-Fi / Chill"),
            SeedTrack("SZA", "Kill Bill", "R&B / Soul"),
            SeedTrack("Arctic Monkeys", "Do I Wanna Know?", "Rock"),
            SeedTrack("Hans Zimmer", "Time", "Classical / Instrumental"),
            SeedTrack("Hozier", "Too Sweet", "Indie / Folk"),
            SeedTrack("Drake", "God's Plan", "Hip-Hop / Rap"),
            SeedTrack("Avicii", "Levels", "Electronic"),
            SeedTrack("Post Malone", "Circles", "Hip-Hop / Rap"),
            SeedTrack("Ludovico Einaudi", "Nuvole Bianche", "Classical / Instrumental")
        )

        val currentMonth = cal.get(Calendar.MONTH) + 1
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)

        for (m in 1..currentMonth) {
            val daysInMonth = if (m == currentMonth) currentDay else 28
            val activeDaysCount = when {
                m == currentMonth -> (daysInMonth * 0.75).toInt().coerceAtLeast(1)
                else -> 18 + (m % 5)
            }

            val step = (daysInMonth / activeDaysCount).coerceAtLeast(1)
            for (d in 1..daysInMonth step step) {
                cal.set(targetYear, m - 1, d)
                val dateStr = sdf.format(cal.time)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val minutes = 35 + ((d * 17 + m * 23) % 125)
                val seconds = minutes * 60L
                val sessionCount = 2 + ((d + m) % 3)

                dao.insertOrUpdateDaily(
                    DailyStatEntity(
                        date = dateStr,
                        year = targetYear,
                        month = m,
                        day = d,
                        dayOfWeek = dayOfWeek,
                        totalPlayTimeSeconds = seconds,
                        sessionCount = sessionCount,
                        lastUpdatedTimestamp = cal.timeInMillis
                    )
                )

                // Add 1-2 realistic playback sessions per active day for genre & session logs
                val trackIndex1 = (d * 3 + m * 7) % sampleCatalog.size
                val track1 = sampleCatalog[trackIndex1]
                val dur1 = (seconds * 0.6).toLong()

                dao.insertSession(
                    PlaybackSessionEntity(
                        date = dateStr,
                        year = targetYear,
                        month = m,
                        startTime = cal.timeInMillis - (minutes * 60 * 1000L),
                        endTime = cal.timeInMillis - (minutes * 30 * 1000L),
                        durationSeconds = dur1,
                        title = track1.title,
                        artist = track1.artist,
                        album = "YouTube Music",
                        genre = track1.genre,
                        sourcePackage = "com.google.android.apps.youtube.music"
                    )
                )

                if (seconds > 1800) {
                    val trackIndex2 = (trackIndex1 + 5) % sampleCatalog.size
                    val track2 = sampleCatalog[trackIndex2]
                    val dur2 = seconds - dur1
                    dao.insertSession(
                        PlaybackSessionEntity(
                            date = dateStr,
                            year = targetYear,
                            month = m,
                            startTime = cal.timeInMillis - (dur2 * 1000L),
                            endTime = cal.timeInMillis,
                            durationSeconds = dur2,
                            title = track2.title,
                            artist = track2.artist,
                            album = "Top Charts",
                            genre = track2.genre,
                            sourcePackage = "com.google.android.apps.youtube.music"
                        )
                    )
                }
            }
        }
    }
}
