package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class GenreAggregateRow(
    val genreName: String,
    val totalSeconds: Long,
    val trackCount: Int
)

data class TrackAggregateRow(
    val title: String,
    val artist: String,
    val album: String?,
    val genre: String,
    val artworkUrl: String?,
    val totalSeconds: Long,
    val playCount: Int,
    val lastPlayedTimestamp: Long
)

@Dao
interface MusicTrackerDao {

    @Query("SELECT * FROM resolved_genres WHERE trackKey = :trackKey")
    suspend fun getResolvedGenre(trackKey: String): ResolvedGenreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putResolvedGenre(result: ResolvedGenreEntity)


    @Query("DELETE FROM resolved_genres")
    suspend fun clearResolvedGenres()

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun getDailyStat(date: String): Flow<DailyStatEntity?>

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getDailyStatSync(date: String): DailyStatEntity?

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :limit")
    fun getRecentDailyStats(limit: Int): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stats WHERE year = :year ORDER BY date ASC")
    fun getDailyStatsForYear(year: Int): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stats WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getDailyStatsBetween(startDate: String, endDate: String): Flow<List<DailyStatEntity>>

    @Query("SELECT DISTINCT year FROM daily_stats ORDER BY year DESC")
    fun getAvailableYears(): Flow<List<Int>>

    @Query(
        """
        WITH RECURSIVE streak(day, count) AS (
            SELECT CASE
                WHEN EXISTS(SELECT 1 FROM daily_stats WHERE date = :today AND totalPlayTimeSeconds > 0)
                THEN :today ELSE :yesterday END,
                0
            UNION ALL
            SELECT DATE(day, '-1 day'), count + 1
            FROM streak
            WHERE EXISTS(
                SELECT 1 FROM daily_stats
                WHERE date = streak.day AND totalPlayTimeSeconds > 0
            )
        )
        SELECT COALESCE(MAX(count), 0) FROM streak
        """
    )
    fun getCurrentStreak(today: String, yesterday: String): Flow<Int>

    @Query("SELECT * FROM daily_stats ORDER BY date ASC")
    fun getAllDailyStats(): Flow<List<DailyStatEntity>>

    @Query("SELECT COALESCE(SUM(totalPlayTimeSeconds), 0) FROM daily_stats WHERE year = :year")
    fun getTotalSecondsForYear(year: Int): Flow<Long>

    @Query("SELECT COALESCE(SUM(totalPlayTimeSeconds), 0) FROM daily_stats")
    fun getTotalLifetimeSeconds(): Flow<Long>

    @Query("SELECT COUNT(*) FROM daily_stats WHERE year = :year AND totalPlayTimeSeconds > 0")
    fun getActiveDaysCountForYear(year: Int): Flow<Int>

    @Query("SELECT * FROM playback_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions WHERE year = :year ORDER BY startTime DESC")
    fun getSessionsForYear(year: Int): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM daily_stats WHERE year = :year)")
    suspend fun hasDailyStatsForYear(year: Int): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM playback_sessions WHERE year = :year)")
    suspend fun hasSessionsForYear(year: Int): Boolean

    @Query("SELECT * FROM playback_sessions WHERE year = :year AND month = :month ORDER BY startTime DESC")
    fun getSessionsForMonth(year: Int, month: Int): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions WHERE date = :date ORDER BY startTime DESC")
    fun getSessionsForDate(date: String): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions WHERE date = :date ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessionsForDate(date: String, limit: Int): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions WHERE date BETWEEN :startDate AND :endDate OR (date < :startDate AND endTime >= :rangeStartTime) ORDER BY startTime DESC")
    fun getSessionsOverlappingRange(
        startDate: String,
        endDate: String,
        rangeStartTime: Long
    ): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions WHERE (startTime < :rangeStartTime AND endTime >= :rangeStartTime) OR (startTime < :rangeEndTime AND endTime >= :rangeEndTime) ORDER BY startTime DESC")
    fun getSessionsCrossingRange(rangeStartTime: Long, rangeEndTime: Long): Flow<List<PlaybackSessionEntity>>

    @Query(
        """
        SELECT COALESCE(NULLIF(TRIM(genre), ''), 'Other') AS genreName,
               COALESCE(SUM(durationSeconds), 0) AS totalSeconds,
               COUNT(DISTINCT COALESCE(title, 'Unknown Track') || CHAR(31) ||
                   COALESCE(NULLIF(TRIM(artist), ''), 'Unknown Artist')) AS trackCount
        FROM playback_sessions
        WHERE date BETWEEN :startDate AND :endDate
          AND durationSeconds >= 5
          AND title IS NOT NULL AND TRIM(title) != ''
          AND LOWER(TRIM(title)) NOT IN (
              'no music playing', 'youtube music', 'music track', 'waiting for youtube music',
              'background audio active', 'background music playing', 'media player',
              'youtube music track', 'syncing track info...', 'detecting...',
              'detecting track...', 'unknown track'
          )
          AND sourcePackage NOT IN ('com.google.android.youtube', 'com.google.android.apps.youtube.kids')
        GROUP BY COALESCE(NULLIF(TRIM(genre), ''), 'Other')
        ORDER BY totalSeconds DESC
        """
    )
    fun getGenreAggregates(startDate: String, endDate: String): Flow<List<GenreAggregateRow>>

    @Query(
        """
        SELECT COALESCE(title, 'Unknown Track') AS title,
               COALESCE(NULLIF(TRIM(artist), ''), 'Unknown Artist') AS artist,
               MAX(album) AS album,
               COALESCE(NULLIF(TRIM(genre), ''), 'Other') AS genre,
               MAX(artworkUrl) AS artworkUrl,
               COALESCE(SUM(durationSeconds), 0) AS totalSeconds,
               COALESCE(SUM(playCount), 0) AS playCount,
               MAX(startTime) AS lastPlayedTimestamp
        FROM playback_sessions
        WHERE date BETWEEN :startDate AND :endDate
          AND COALESCE(NULLIF(TRIM(genre), ''), 'Other') = :genre
          AND durationSeconds >= 5
          AND title IS NOT NULL AND TRIM(title) != ''
          AND LOWER(TRIM(title)) NOT IN (
              'no music playing', 'youtube music', 'music track', 'waiting for youtube music',
              'background audio active', 'background music playing', 'media player',
              'youtube music track', 'syncing track info...', 'detecting...',
              'detecting track...', 'unknown track'
          )
          AND sourcePackage NOT IN ('com.google.android.youtube', 'com.google.android.apps.youtube.kids')
        GROUP BY COALESCE(title, 'Unknown Track'), COALESCE(NULLIF(TRIM(artist), ''), 'Unknown Artist')
        ORDER BY totalSeconds DESC
        LIMIT :limit
        """
    )
    fun getTopTracksForGenre(
        startDate: String,
        endDate: String,
        genre: String,
        limit: Int
    ): Flow<List<TrackAggregateRow>>

    @Query(
        """
        SELECT COALESCE(title, 'Unknown Track') AS title,
               COALESCE(NULLIF(TRIM(artist), ''), 'Unknown Artist') AS artist,
               MAX(album) AS album,
               COALESCE(NULLIF(TRIM(genre), ''), 'Other') AS genre,
               MAX(artworkUrl) AS artworkUrl,
               COALESCE(SUM(durationSeconds), 0) AS totalSeconds,
               COALESCE(SUM(playCount), 0) AS playCount,
               MAX(startTime) AS lastPlayedTimestamp
        FROM playback_sessions
        WHERE date BETWEEN :startDate AND :endDate
          AND COALESCE(NULLIF(TRIM(genre), ''), 'Other') = :genre
          AND COALESCE(title, 'Unknown Track') = COALESCE(:title, 'Unknown Track')
          AND COALESCE(NULLIF(TRIM(artist), ''), 'Unknown Artist') =
              COALESCE(NULLIF(TRIM(:artist), ''), 'Unknown Artist')
          AND durationSeconds >= 5
          AND title IS NOT NULL AND TRIM(title) != ''
          AND LOWER(TRIM(title)) NOT IN (
              'no music playing', 'youtube music', 'music track', 'waiting for youtube music',
              'background audio active', 'background music playing', 'media player',
              'youtube music track', 'syncing track info...', 'detecting...',
              'detecting track...', 'unknown track'
          )
          AND sourcePackage NOT IN ('com.google.android.youtube', 'com.google.android.apps.youtube.kids')
        GROUP BY COALESCE(title, 'Unknown Track'), COALESCE(NULLIF(TRIM(artist), ''), 'Unknown Artist')
        LIMIT 1
        """
    )
    suspend fun getTrackAggregateForGenre(
        startDate: String,
        endDate: String,
        genre: String,
        title: String?,
        artist: String?
    ): TrackAggregateRow?

    @Query("SELECT * FROM playback_sessions ORDER BY startTime DESC")
    suspend fun getAllSessionsSync(): List<PlaybackSessionEntity>

    @Query(
        """
        SELECT * FROM playback_sessions
        WHERE title IS NULL
           OR TRIM(title) = ''
           OR LOWER(TRIM(title)) IN (
               'background audio active', 'background music playing', 'no music playing',
               'youtube music', 'music track', 'media player', 'youtube music track',
               'syncing track info...', 'detecting...', 'detecting track...', 'unknown track'
           )
           OR sourcePackage IN ('com.google.android.youtube', 'com.google.android.apps.youtube.kids')
        """
    )
    suspend fun getCorruptSessionsSync(): List<PlaybackSessionEntity>

    @Query("SELECT * FROM playback_sessions ORDER BY endTime DESC LIMIT :limit")
    suspend fun getRecentSessionsSync(limit: Int): List<PlaybackSessionEntity>

    @Query("SELECT * FROM playback_sessions WHERE durationSeconds < 5 AND id != :activeSessionId")
    suspend fun getShortSessionsSync(activeSessionId: Long = -1L): List<PlaybackSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDaily(stat: DailyStatEntity)

    @Query("UPDATE daily_stats SET totalPlayTimeSeconds = totalPlayTimeSeconds + :seconds, lastUpdatedTimestamp = :timestamp WHERE date = :date")
    suspend fun addListeningSeconds(date: String, seconds: Long, timestamp: Long): Int

    @Query("UPDATE daily_stats SET totalPlayTimeSeconds = MAX(0, totalPlayTimeSeconds - :seconds) WHERE date = :date")
    suspend fun subtractListeningSeconds(date: String, seconds: Long)

    @Query("UPDATE daily_stats SET sessionCount = sessionCount + 1, lastUpdatedTimestamp = :timestamp WHERE date = :date")
    suspend fun addSessionCount(date: String, timestamp: Long): Int

    @Query("UPDATE daily_stats SET sessionCount = MAX(0, sessionCount - 1) WHERE date = :date")
    suspend fun subtractSessionCount(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PlaybackSessionEntity): Long

    @Query("SELECT * FROM playback_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): PlaybackSessionEntity?

    @Query("UPDATE playback_sessions SET endTime = MAX(endTime, :endTime), durationSeconds = MAX(durationSeconds, :durationSeconds), dailyDurations = :dailyDurations, isOpen = CASE WHEN :closeSession THEN 0 ELSE isOpen END WHERE id = :sessionId")
    suspend fun updateSession(sessionId: Long, endTime: Long, durationSeconds: Long, dailyDurations: String?, closeSession: Boolean)

    @Query("UPDATE playback_sessions SET isOpen = 1 WHERE id = :sessionId")
    suspend fun reopenSession(sessionId: Long)

    @Query("UPDATE playback_sessions SET genre = :genre WHERE id = :sessionId")
    suspend fun updateSessionGenre(sessionId: Long, genre: String)

    @Query("UPDATE playback_sessions SET artworkUrl = :artworkUrl WHERE id = :sessionId")
    suspend fun updateSessionArtwork(sessionId: Long, artworkUrl: String)

    @Query("UPDATE playback_sessions SET artist = :artist WHERE id = :sessionId")
    suspend fun updateSessionArtist(sessionId: Long, artist: String)

    @Query("UPDATE playback_sessions SET playCount = playCount + :plays WHERE id = :sessionId")
    suspend fun incrementSessionPlayCount(sessionId: Long, plays: Int = 1)

    @Query("UPDATE playback_sessions SET title = :title, artist = :artist, album = :album, genre = :genre, artworkUrl = COALESCE(:artworkUrl, artworkUrl) WHERE id = :sessionId")
    suspend fun updateSessionDetails(sessionId: Long, title: String, artist: String, album: String, genre: String, artworkUrl: String? = null)

    @Query("UPDATE playback_sessions SET artist = '' WHERE artist = 'YouTube Music'")
    suspend fun cleanPlaceholderArtists()

    @Query("DELETE FROM daily_stats WHERE totalPlayTimeSeconds <= 0 AND sessionCount <= 0")
    suspend fun deleteEmptyDailyStats()

    @Query("DELETE FROM playback_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM daily_stats")
    suspend fun clearDailyStats()

    @Query("DELETE FROM playback_sessions")
    suspend fun clearSessions()
}
