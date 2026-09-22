package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyStatEntity::class, PlaybackSessionEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicTrackerDao(): MusicTrackerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_sessions ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yt_music_tracker.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigrationFrom(true, 1, 2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
