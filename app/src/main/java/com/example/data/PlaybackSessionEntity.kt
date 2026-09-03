package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_sessions")
data class PlaybackSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val date: String, // Format: YYYY-MM-DD
    val year: Int,
    val month: Int,
    val startTime: Long,
    val endTime: Long = startTime,
    val durationSeconds: Long = 0L,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val artworkUrl: String? = null,
    val sourcePackage: String = "com.google.android.apps.youtube.music"
)
