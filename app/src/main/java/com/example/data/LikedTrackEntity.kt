package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(
    @PrimaryKey val trackKey: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val likedAt: Long,
)
