package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {
    @Test
    fun `migration from release schema preserves listening history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v5.db"
        createDatabase(context, name, 5)

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(7, migrated.openHelper.readableDatabase.version)
            val daily = migrated.musicTrackerDao().getDailyStatSync("2026-09-17")
            assertEquals(2220L, daily?.totalPlayTimeSeconds)
            assertEquals(13, daily?.sessionCount)

            val indexes = migrated.openHelper.readableDatabase
                .query("PRAGMA index_list(playback_sessions)")
                .use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(1))
                    }
                }
            assertTrue(indexes.contains("index_playback_sessions_endTime"))

            val session = migrated.musicTrackerDao().getAllSessionsSync().single()
            assertEquals(7L, session.id)
            assertEquals("Saved track", session.title)
            assertEquals(2220L, session.durationSeconds)
            assertEquals(2, session.playCount)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `migration from v6 preserves listening history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v6.db"
        createDatabase(context, name, 6)

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(7, migrated.openHelper.readableDatabase.version)
            val daily = migrated.musicTrackerDao().getDailyStatSync("2026-09-17")
            assertEquals(2220L, daily?.totalPlayTimeSeconds)
            val session = migrated.musicTrackerDao().getAllSessionsSync().single()
            assertEquals(7L, session.id)
            assertEquals("Saved track", session.title)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun createDatabase(context: Context, name: String, version: Int) {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.execSQL("CREATE TABLE daily_stats (date TEXT NOT NULL PRIMARY KEY, year INTEGER NOT NULL, month INTEGER NOT NULL, day INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL, totalPlayTimeSeconds INTEGER NOT NULL, sessionCount INTEGER NOT NULL, lastUpdatedTimestamp INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE playback_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, title TEXT, artist TEXT, album TEXT, genre TEXT, artworkUrl TEXT, playCount INTEGER NOT NULL, sourcePackage TEXT NOT NULL, dailyDurations TEXT, isOpen INTEGER NOT NULL)")
            if (version >= 6) {
                database.execSQL("CREATE INDEX index_daily_stats_year_month_date ON daily_stats(year, month, date)")
                database.execSQL("CREATE INDEX index_playback_sessions_date_startTime ON playback_sessions(date, startTime)")
                database.execSQL("CREATE INDEX index_playback_sessions_year_month_startTime ON playback_sessions(year, month, startTime)")
                database.execSQL("CREATE INDEX index_playback_sessions_genre_date ON playback_sessions(genre, date)")
            }
            database.execSQL("INSERT INTO daily_stats VALUES ('2026-09-17', 2026, 9, 17, 5, 2220, 13, 12345)")
            database.execSQL("INSERT INTO playback_sessions VALUES (7, '2026-09-17', 2026, 9, 1000, 3220, 2220, 'Saved track', 'Saved artist', NULL, 'Pop', NULL, 2, 'com.google.android.apps.youtube.music', NULL, 0)")
            database.version = version
        }
    }
}
