package com.example

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.AppDatabase
import com.example.data.MusicTrackerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenreMigrationInstrumentedTest {
    @Test fun upgradeKeepsHistoryAndManualLabelUpdatesExactSong() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "genre-upgrade-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE daily_stats (date TEXT NOT NULL PRIMARY KEY, year INTEGER NOT NULL, month INTEGER NOT NULL, day INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL, totalPlayTimeSeconds INTEGER NOT NULL, sessionCount INTEGER NOT NULL, lastUpdatedTimestamp INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playback_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, title TEXT, artist TEXT, album TEXT, genre TEXT, artworkUrl TEXT, playCount INTEGER NOT NULL, sourcePackage TEXT NOT NULL, dailyDurations TEXT, isOpen INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX index_daily_stats_year_month_date ON daily_stats(year, month, date)")
            db.execSQL("CREATE INDEX index_playback_sessions_date_startTime ON playback_sessions(date, startTime)")
            db.execSQL("CREATE INDEX index_playback_sessions_year_month_startTime ON playback_sessions(year, month, startTime)")
            db.execSQL("CREATE INDEX index_playback_sessions_genre_date ON playback_sessions(genre, date)")
            db.execSQL("CREATE INDEX index_playback_sessions_endTime ON playback_sessions(endTime)")
            db.execSQL("INSERT INTO daily_stats VALUES ('2026-09-25',2026,9,25,6,120,2,1000)")
            db.execSQL("INSERT INTO playback_sessions VALUES (1,'2026-09-25',2026,9,1000,1060,60,'Song','Artist',NULL,'Pop',NULL,1,'com.google.android.apps.youtube.music',NULL,0)")
            db.execSQL("INSERT INTO playback_sessions VALUES (2,'2026-09-25',2026,9,1060,1120,60,'Other song','Artist',NULL,'Rock',NULL,1,'com.google.android.apps.youtube.music',NULL,0)")
            db.version = 7
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_7_8).build()
        try {
            val dao = database.musicTrackerDao()
            assertEquals(120L, dao.getDailyStatSync("2026-09-25")?.totalPlayTimeSeconds)
            MusicTrackerRepository(dao, database).setManualGenre("Artist", "Song", "Bhajan")
            assertEquals(listOf("Bhajan", "Rock"), dao.getAllSessionsSync().sortedBy { it.id }.map { it.genre })
            assertEquals("manual", dao.getResolvedGenre("artist\u001fsong")?.source)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
