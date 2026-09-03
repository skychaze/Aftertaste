package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.MusicTrackerRepository
import com.example.tracker.MusicTrackerEngine

class YTTrackerApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: MusicTrackerRepository
        private set

    lateinit var trackerEngine: MusicTrackerEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        repository = MusicTrackerRepository(database.musicTrackerDao())
        trackerEngine = MusicTrackerEngine.getInstance(this, repository)
    }

    companion object {
        lateinit var instance: YTTrackerApplication
            private set
    }
}
