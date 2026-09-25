package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyStatEntity::class, PlaybackSessionEntity::class, ResolvedGenreEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicTrackerDao(): MusicTrackerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_1_3 = legacyMigration(1)
        internal val MIGRATION_2_3 = legacyMigration(2)

        private fun legacyMigration(version: Int) = object : Migration(version, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = db.query("PRAGMA table_info(playback_sessions)").use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
                }
                val required = mapOf(
                    "genre" to "TEXT", "artworkUrl" to "TEXT", "album" to "TEXT",
                    "sourcePackage" to "TEXT NOT NULL DEFAULT 'com.google.android.apps.youtube.music'",
                    "durationSeconds" to "INTEGER NOT NULL DEFAULT 0", "endTime" to "INTEGER NOT NULL DEFAULT 0"
                )
                required.forEach { (name, definition) ->
                    if (name !in columns) db.execSQL("ALTER TABLE playback_sessions ADD COLUMN $name $definition")
                }
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_sessions ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_sessions ADD COLUMN dailyDurations TEXT")
                db.execSQL("ALTER TABLE playback_sessions ADD COLUMN isOpen INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_stats_year_month_date ON daily_stats(year, month, date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_sessions_date_startTime ON playback_sessions(date, startTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_sessions_year_month_startTime ON playback_sessions(year, month, startTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_sessions_genre_date ON playback_sessions(genre, date)")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_sessions_endTime ON playback_sessions(endTime)")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS resolved_genres (trackKey TEXT NOT NULL PRIMARY KEY, genre TEXT NOT NULL, confidence REAL NOT NULL, source TEXT NOT NULL, resolvedAt INTEGER NOT NULL)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yt_music_tracker.db"
                )
                    .addMigrations(MIGRATION_1_3, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
