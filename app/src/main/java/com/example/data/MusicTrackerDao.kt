package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicTrackerDao {

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun getDailyStat(date: String): Flow<DailyStatEntity?>

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getDailyStatSync(date: String): DailyStatEntity?

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :limit")
    fun getRecentDailyStats(limit: Int): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stats WHERE year = :year ORDER BY date ASC")
    fun getDailyStatsForYear(year: Int): Flow<List<DailyStatEntity>>

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

    @Query("SELECT * FROM playback_sessions WHERE year = :year AND month = :month ORDER BY startTime DESC")
    fun getSessionsForMonth(year: Int, month: Int): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions WHERE date = :date ORDER BY startTime DESC")
    fun getSessionsForDate(date: String): Flow<List<PlaybackSessionEntity>>

    @Query("SELECT * FROM playback_sessions WHERE date = :date ORDER BY startTime DESC")
    suspend fun getSessionsForDateSync(date: String): List<PlaybackSessionEntity>

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

    @Query("UPDATE playback_sessions SET endTime = :endTime, durationSeconds = :durationSeconds WHERE id = :sessionId")
    suspend fun updateSession(sessionId: Long, endTime: Long, durationSeconds: Long)

    @Query("UPDATE playback_sessions SET genre = :genre WHERE id = :sessionId")
    suspend fun updateSessionGenre(sessionId: Long, genre: String)

    @Query("UPDATE playback_sessions SET artworkUrl = :artworkUrl WHERE id = :sessionId")
    suspend fun updateSessionArtwork(sessionId: Long, artworkUrl: String)

    @Query("UPDATE playback_sessions SET title = :title, artist = :artist, album = :album, genre = :genre, artworkUrl = COALESCE(:artworkUrl, artworkUrl) WHERE id = :sessionId")
    suspend fun updateSessionDetails(sessionId: Long, title: String, artist: String, album: String, genre: String, artworkUrl: String? = null)

    @Query("DELETE FROM playback_sessions WHERE title = 'Background Audio Active' OR title = 'No music playing' OR title IS NULL OR title = ''")
    suspend fun cleanCorruptSessions()

    @Query("UPDATE playback_sessions SET artist = '' WHERE artist = 'YouTube Music'")
    suspend fun cleanPlaceholderArtists()

    @Query("DELETE FROM playback_sessions WHERE sourcePackage = 'com.google.android.youtube' OR sourcePackage = 'com.google.android.apps.youtube.kids' OR sourcePackage = 'com.google.android.apps.youtube.unplugged' OR (sourcePackage LIKE '%youtube%' AND sourcePackage NOT LIKE '%music%' AND sourcePackage NOT LIKE '%ytmusic%')")
    suspend fun deleteYouTubeVideoSessions()

    @Query("""
        UPDATE daily_stats 
        SET totalPlayTimeSeconds = COALESCE((SELECT SUM(durationSeconds) FROM playback_sessions WHERE playback_sessions.date = daily_stats.date), 0),
            sessionCount = COALESCE((SELECT COUNT(*) FROM playback_sessions WHERE playback_sessions.date = daily_stats.date), 0)
    """)
    suspend fun resyncAllDailyStatsFromSessions()

    @Query("DELETE FROM playback_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM playback_sessions WHERE durationSeconds < 5 AND id != :activeSessionId")
    suspend fun deleteShortSessions(activeSessionId: Long = -1L)

    @Query("DELETE FROM daily_stats")
    suspend fun clearDailyStats()

    @Query("DELETE FROM playback_sessions")
    suspend fun clearSessions()
}
