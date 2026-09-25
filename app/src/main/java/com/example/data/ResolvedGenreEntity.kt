package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resolved_genres")
data class ResolvedGenreEntity(
    @PrimaryKey val trackKey: String,
    val genre: String,
    val confidence: Double,
    val source: String,
    val resolvedAt: Long
)
