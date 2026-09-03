package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val year: Int,
    val month: Int, // 1 - 12
    val day: Int, // 1 - 31
    val dayOfWeek: Int, // 1 = Sunday, 2 = Monday, etc.
    val totalPlayTimeSeconds: Long = 0L,
    val sessionCount: Int = 0,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
