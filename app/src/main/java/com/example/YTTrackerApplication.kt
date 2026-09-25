package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.MusicTrackerRepository
import com.example.tracker.MusicTrackerEngine
import com.example.update.AppUpdateManager

class YTTrackerApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: MusicTrackerRepository
        private set

    lateinit var trackerEngine: MusicTrackerEngine
        private set

    lateinit var updateManager: AppUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        repository = MusicTrackerRepository(database.musicTrackerDao(), database)
        trackerEngine = MusicTrackerEngine.getInstance(this, repository)
        updateManager = AppUpdateManager(this)
        updateManager.onAppStart()
    }

    companion object {
        lateinit var instance: YTTrackerApplication
            private set
    }
}
