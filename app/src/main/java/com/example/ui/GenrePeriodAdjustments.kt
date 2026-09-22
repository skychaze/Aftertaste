package com.example.ui

import com.example.data.GenreAggregateRow
import com.example.data.PlaybackSessionEntity
import com.example.data.TrackAggregateRow

internal data class ScopedGenreSession(
    val session: PlaybackSessionEntity,
    val genre: String,
    val seconds: Long
)

internal data class GenreTrackKey(
    val genre: String,
    val title: String,
    val artist: String
)

internal object GenrePeriodAdjustments {
    const val TRACK_LIMIT = 100

    fun trackKey(genre: String, title: String?, artist: String?): GenreTrackKey = GenreTrackKey(
        genre = genre,
        title = title ?: "Unknown Track",
        artist = artist?.trim()?.takeIf(String::isNotEmpty) ?: "Unknown Artist"
    )

    fun topTracksQueryLimit(
        sessions: List<ScopedGenreSession>,
        genre: String,
        bounds: DateBounds
    ): Int {
        val outgoingTracks = sessions.asSequence()
            .filter { it.genre == genre && it.session.date in bounds }
            .map { trackKey(it.genre, it.session.title, it.session.artist) }
            .distinct()
            .count()
        return TRACK_LIMIT + outgoingTracks
    }

    fun adjustGenreAggregates(
        baseRows: List<GenreAggregateRow>,
        sessions: List<ScopedGenreSession>,
        bounds: DateBounds,
        baseTracks: Map<GenreTrackKey, TrackAggregateRow?>
    ): List<GenreAggregateRow> {
        val rows = baseRows.associateBy { it.genreName }.toMutableMap()
        for ((genre, genreSessions) in sessions.groupBy { it.genre }) {
            val secondsDelta = genreSessions.sumOf { contribution ->
                val previous = if (contribution.session.date in bounds) {
                    contribution.session.durationSeconds
                } else {
                    0L
                }
                contribution.seconds - previous
            }
            val trackCountDelta = genreSessions.groupBy {
                trackKey(genre, it.session.title, it.session.artist)
            }.entries.sumOf { (key, trackSessions) ->
                val base = baseTracks[key]
                val baseBoundarySeconds = trackSessions.asSequence()
                    .filter { it.session.date in bounds }
                    .sumOf { it.session.durationSeconds }
                val hasOtherBaseSession = (base?.totalSeconds ?: 0L) > baseBoundarySeconds
                val hasPeriodContribution = trackSessions.any { it.seconds > 0L }
                val wasCounted = base != null
                (if (hasOtherBaseSession || hasPeriodContribution) 1 else 0) -
                    (if (wasCounted) 1 else 0)
            }
            val base = rows[genre] ?: GenreAggregateRow(genre, 0L, 0)
            rows[genre] = base.copy(
                totalSeconds = (base.totalSeconds + secondsDelta).coerceAtLeast(0L),
                trackCount = (base.trackCount + trackCountDelta).coerceAtLeast(0)
            )
        }
        return rows.values.filter { it.totalSeconds > 0L }.sortedByDescending { it.totalSeconds }
    }

    fun adjustGenreTracks(
        baseRows: List<TrackAggregateRow>,
        sessions: List<ScopedGenreSession>,
        genre: String,
        bounds: DateBounds,
        baseTracks: Map<GenreTrackKey, TrackAggregateRow?>
    ): List<TrackAggregateRow> {
        val tracks = baseRows.associateByTo(linkedMapOf()) {
            trackKey(it.genre, it.title, it.artist)
        }
        val corrections = sessions.filter { it.genre == genre }.groupBy {
            trackKey(it.genre, it.session.title, it.session.artist)
        }

        for ((key, trackSessions) in corrections) {
            val first = trackSessions.first().session
            val base = baseTracks[key]
            val totalDelta = trackSessions.sumOf { contribution ->
                val previous = if (contribution.session.date in bounds) {
                    contribution.session.durationSeconds
                } else {
                    0L
                }
                contribution.seconds - previous
            }
            val playCountDelta = trackSessions.sumOf { contribution ->
                val previous = if (contribution.session.date in bounds) contribution.session.playCount else 0
                val current = contribution.session.playCount.takeIf { contribution.seconds > 0L } ?: 0
                current - previous
            }
            val totalSeconds = ((base?.totalSeconds ?: 0L) + totalDelta).coerceAtLeast(0L)
            if (totalSeconds == 0L) {
                tracks.remove(key)
            } else {
                tracks[key] = TrackAggregateRow(
                    title = base?.title ?: first.title ?: "Unknown Track",
                    artist = base?.artist ?: first.artist?.trim()?.takeIf(String::isNotEmpty) ?: "Unknown Artist",
                    album = base?.album ?: first.album,
                    genre = genre,
                    artworkUrl = base?.artworkUrl ?: first.artworkUrl,
                    totalSeconds = totalSeconds,
                    playCount = ((base?.playCount ?: 0) + playCountDelta).coerceAtLeast(0),
                    lastPlayedTimestamp = maxOf(
                        base?.lastPlayedTimestamp ?: 0L,
                        trackSessions.maxOf { it.session.startTime }
                    )
                )
            }
        }
        return tracks.values.asSequence()
            .filter { it.totalSeconds > 0L }
            .sortedByDescending { it.totalSeconds }
            .take(TRACK_LIMIT)
            .toList()
    }
}
