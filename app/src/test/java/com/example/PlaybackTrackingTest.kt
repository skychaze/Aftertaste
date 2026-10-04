package com.example

import android.app.Application
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.MusicTrackerRepository
import com.example.data.PlaybackSessionDurations
import com.example.tracker.ArtworkResolver
import com.example.tracker.GenreTags
import com.example.tracker.MusicTrackerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaController
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackTrackingTest {
    @Test
    fun `seek buffering and new metadata create independent playback sessions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val engine = newEngine(context, MusicTrackerRepository(database.musicTrackerDao(), database))
        runCurrent()
        val session = MediaSession(context, "Playback tracking")
        val controller = controller(context, session)
        val controllerShadow = shadowOf(controller)
        val transportShadow = shadowOf(controller.transportControls)
        seedResolvers(context, "Song A", "Artist A")
        seedResolvers(context, "Song B", "Artist B")
        engine.setFilterOnlyYouTubeMusic(false)
        publishMetadata(controllerShadow, "Song A", "Artist A", 240_000L)
        publishState(controllerShadow, PlaybackState.STATE_PLAYING, 0L)
        shadowOf(Looper.getMainLooper()).idleFor(1_000L, TimeUnit.MILLISECONDS)
        session.isActive = true

        try {
            invoke(engine, "switchActiveController", controller)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            settle(engine)
            val songASessionId = engine.getCurrentDbSessionId() ?: error("Song A session was not attached")
            advancePlayback(5)
            assertTrue(engine.uiState.value.currentSessionSeconds >= 5L)
            assertTrue(engine.uiState.value.trackPositionMs > 0L)

            engine.seekTo(150_000L)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(150_000L, transportShadow.seekToPositionMs)
            publishState(controllerShadow, PlaybackState.STATE_BUFFERING, 150_000L)

            publishMetadata(controllerShadow, "Song B", "Artist B", 180_000L)
            publishState(controllerShadow, PlaybackState.STATE_PLAYING, 0L)
            runCurrent()
            settle(engine)

            val songBSessionId = engine.getCurrentDbSessionId() ?: error("Song B session was not attached")
            assertNotEquals(songASessionId, songBSessionId)
            assertEquals("Song B", engine.uiState.value.trackTitle)
            assertEquals(180_000L, engine.uiState.value.trackDurationMs)

            advancePlayback(5)
            assertTrue(engine.uiState.value.currentSessionSeconds >= 5L)
            assertTrue(engine.uiState.value.trackPositionMs in 1L..180_000L)
            publishState(controllerShadow, PlaybackState.STATE_STOPPED, engine.uiState.value.trackPositionMs)
            runCurrent()
            settle(engine)

            val rows = database.musicTrackerDao().getAllSessionsSync().sortedBy { it.id }
            assertEquals(listOf("Song A", "Song B"), rows.map { it.title })
            assertTrue(rows.all { it.durationSeconds >= 5L && !it.isOpen })
            assertTrue(rows.all {
                PlaybackSessionDurations.parse(it.dailyDurations).values.sum() == it.durationSeconds
            })
        } finally {
            session.release()
            engine.onPlaybackPausedOrStopped()
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            invoke(engine, "stopTicker")
            settle(engine)
            cancelEngine(engine)
            database.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `buffering continuation keeps the same track session and accumulated seconds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val engine = newEngine(context, MusicTrackerRepository(database.musicTrackerDao(), database))
        runCurrent()
        val session = MediaSession(context, "Buffering continuation")
        val controller = controller(context, session)
        val controllerShadow = shadowOf(controller)
        seedResolvers(context, "Same Song", "Same Artist")
        engine.setFilterOnlyYouTubeMusic(false)
        publishMetadata(controllerShadow, "Same Song", "Same Artist", 240_000L)
        publishState(controllerShadow, PlaybackState.STATE_PLAYING, 0L)
        shadowOf(Looper.getMainLooper()).idleFor(1_000L, TimeUnit.MILLISECONDS)
        session.isActive = true

        try {
            invoke(engine, "switchActiveController", controller)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            settle(engine)
            val originalSessionId = engine.getCurrentDbSessionId() ?: error("Session was not attached")
            advancePlayback(3)
            publishState(controllerShadow, PlaybackState.STATE_BUFFERING, 25_000L)
            val secondsBeforeBuffer = engine.uiState.value.currentSessionSeconds
            advancePlayback(2)
            assertEquals(secondsBeforeBuffer, engine.uiState.value.currentSessionSeconds)
            publishState(controllerShadow, PlaybackState.STATE_PLAYING, 25_000L)
            runCurrent()
            settle(engine)

            advancePlayback(3)
            publishState(controllerShadow, PlaybackState.STATE_STOPPED, 28_000L)
            runCurrent()
            settle(engine)

            assertEquals(originalSessionId, engine.getCurrentDbSessionId())
            val row = database.musicTrackerDao().getAllSessionsSync().single()
            assertEquals("Same Song", row.title)
            assertTrue(row.durationSeconds >= 5L)
            assertTrue(!row.isOpen)
        } finally {
            session.release()
            engine.onPlaybackPausedOrStopped()
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            invoke(engine, "stopTicker")
            settle(engine)
            cancelEngine(engine)
            database.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `queued seek is rejected after the visible track changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val engine = newEngine(context, MusicTrackerRepository(database.musicTrackerDao(), database))
        runCurrent()
        val session = MediaSession(context, "Stale seek")
        val controller = controller(context, session)
        val controllerShadow = shadowOf(controller)
        seedResolvers(context, "First Song", "First Artist")
        seedResolvers(context, "夜に駆ける", "YOASOBI")
        engine.setFilterOnlyYouTubeMusic(false)
        publishMetadata(controllerShadow, "First Song", "First Artist", 240_000L)
        publishState(controllerShadow, PlaybackState.STATE_PLAYING, 0L)
        shadowOf(Looper.getMainLooper()).idleFor(1_000L, TimeUnit.MILLISECONDS)
        session.isActive = true

        try {
            invoke(engine, "switchActiveController", controller)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            settle(engine)
            publishMetadata(controllerShadow, "Second Song", "Second Artist", 180_000L, notify = false)
            engine.seekTo(120_000L)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(0L, shadowOf(controller.transportControls).seekToPositionMs)

            publishMetadata(controllerShadow, "夜に駆ける", "YOASOBI", 180_000L)
            runCurrent()
            settle(engine)
            assertEquals("夜に駆ける", engine.uiState.value.trackTitle)
            engine.seekTo(90_000L)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(90_000L, shadowOf(controller.transportControls).seekToPositionMs)
            publishMetadata(controllerShadow, "夜に駆ける", "YOASOBI", 120_000L, notify = false)
            engine.seekTo(150_000L)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(119_999L, shadowOf(controller.transportControls).seekToPositionMs)
        } finally {
            session.release()
            engine.onPlaybackPausedOrStopped()
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            invoke(engine, "stopTicker")
            settle(engine)
            cancelEngine(engine)
            database.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `pause and resume during attachment keeps one session`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val engine = newEngine(context, MusicTrackerRepository(database.musicTrackerDao(), database))
        runCurrent()
        val session = MediaSession(context, "Pending attachment")
        val controller = controller(context, session)
        val controllerShadow = shadowOf(controller)
        seedResolvers(context, "Pending Song", "Pending Artist")
        engine.setFilterOnlyYouTubeMusic(false)
        publishMetadata(controllerShadow, "Pending Song", "Pending Artist", 240_000L)
        publishState(controllerShadow, PlaybackState.STATE_PLAYING, 0L)
        shadowOf(Looper.getMainLooper()).idleFor(1_000L, TimeUnit.MILLISECONDS)
        session.isActive = true

        try {
            invoke(engine, "switchActiveController", controller)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(engine.uiState.value.isActivelyPlaying)
            assertEquals(null, engine.getCurrentDbSessionId())

            publishState(controllerShadow, PlaybackState.STATE_PAUSED, 0L)
            publishState(controllerShadow, PlaybackState.STATE_PLAYING, 0L)
            assertTrue(engine.uiState.value.isActivelyPlaying)
            assertEquals(null, engine.getCurrentDbSessionId())

            runCurrent()
            settle(engine)
            val sessionId = engine.getCurrentDbSessionId() ?: error("Pending session was not attached")
            val row = database.musicTrackerDao().getAllSessionsSync().single()
            assertEquals(sessionId, row.id)
            assertEquals("Pending Song", row.title)
            assertTrue(row.isOpen)
        } finally {
            session.release()
            engine.onPlaybackPausedOrStopped()
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            invoke(engine, "stopTicker")
            settle(engine)
            cancelEngine(engine)
            database.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `notification track change waits for matching controller timeline`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val engine = newEngine(context, MusicTrackerRepository(database.musicTrackerDao(), database))
        runCurrent()
        val session = MediaSession(context, "Notification timeline")
        val controller = controller(context, session)
        val controllerShadow = shadowOf(controller)
        seedResolvers(context, "Old Song", "Old Artist")
        seedResolvers(context, "New Song", "New Artist")
        publishMetadata(controllerShadow, "Old Song", "Old Artist", 240_000L)
        publishState(controllerShadow, PlaybackState.STATE_PLAYING, 100_000L)
        try {
            invoke(engine, "switchActiveController", controller)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            settle(engine)
            engine.onTrackDiscovered("New Song", "New Artist", "", controller.packageName, true)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            settle(engine)
            advancePlayback(1)
            assertEquals("New Song", engine.uiState.value.trackTitle)
            assertEquals(0L, engine.uiState.value.trackDurationMs)
            assertEquals(0L, engine.uiState.value.trackPositionMs)

            publishState(controllerShadow, PlaybackState.STATE_PLAYING, 0L)
            publishMetadata(controllerShadow, "New Song", "New Artist", 180_000L)
            runCurrent()
            settle(engine)
            assertEquals(180_000L, engine.uiState.value.trackDurationMs)
        } finally {
            session.release()
            engine.onPlaybackPausedOrStopped()
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            invoke(engine, "stopTicker")
            settle(engine)
            cancelEngine(engine)
            database.close()
            Dispatchers.resetMain()
        }
    }

    private fun newEngine(context: Context, repository: MusicTrackerRepository): MusicTrackerEngine =
        MusicTrackerEngine::class.java.getDeclaredConstructor(Context::class.java, MusicTrackerRepository::class.java)
            .apply { isAccessible = true }
            .newInstance(context, repository)

    private fun controller(context: Context, session: MediaSession): MediaController =
        MediaController(context, session.sessionToken).also {
            shadowOf(it).setPackageName("com.google.android.apps.youtube.music")
        }

    private fun metadata(title: String, artist: String, durationMs: Long) =
        MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build()

    private fun publishMetadata(
        controller: ShadowMediaController,
        title: String,
        artist: String,
        durationMs: Long,
        notify: Boolean = true
    ) {
        val metadata = metadata(title, artist, durationMs)
        controller.setMetadata(metadata)
        if (notify) controller.executeOnMetadataChanged(metadata)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun publishState(controller: ShadowMediaController, state: Int, positionMs: Long) {
        val playbackState = PlaybackState.Builder()
                .setState(state, positionMs, 1f, SystemClock.elapsedRealtime())
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SEEK_TO
                )
                .build()
        controller.setPlaybackState(playbackState)
        controller.executeOnPlaybackStateChanged(playbackState)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun invoke(target: MusicTrackerEngine, methodName: String, vararg args: Any?) {
        val method = MusicTrackerEngine::class.java.declaredMethods.single {
            it.name == methodName && it.parameterCount == args.size
        }.apply { isAccessible = true }
        method.invoke(target, *args)
    }

    private fun seedResolvers(context: Context, title: String, artist: String) {
        val key = GenreTags.trackKey(artist, title) ?: error("Track key was blank")
        runBlocking {
            AppDatabase.getInstance(context).musicTrackerDao().putResolvedGenre(
                com.example.data.ResolvedGenreEntity(key, "Pop", 1.0, "manual", System.currentTimeMillis())
            )
        }
        val artwork = File(context.filesDir, "artworks/${ArtworkResolver.getCacheKey(artist, title)}.jpg")
        artwork.parentFile?.mkdirs()
        artwork.writeBytes(byteArrayOf(1))
    }

    private suspend fun TestScope.advancePlayback(seconds: Int) {
        repeat(seconds) {
            advanceTimeBy(1_000L)
            runCurrent()
            shadowOf(Looper.getMainLooper()).idleFor(1_000L, TimeUnit.MILLISECONDS)
            runCurrent()
        }
    }

    private fun TestScope.settle(engine: MusicTrackerEngine) {
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            val tail = MusicTrackerEngine::class.java.getDeclaredField("dbWriteTail")
                .apply { isAccessible = true }
                .get(engine) as? Job
            if (tail != null) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (!tail.isCompleted && System.nanoTime() < deadline) {
                    shadowOf(Looper.getMainLooper()).idle()
                    runCurrent()
                    Thread.sleep(1L)
                }
                assertTrue("Database write queue did not settle", tail.isCompleted)
            }
            runCurrent()
        }
    }

    private fun cancelEngine(engine: MusicTrackerEngine) {
        listOf("scope", "dbWriteScope").forEach { name ->
            val scope = MusicTrackerEngine::class.java.getDeclaredField(name)
                .apply { isAccessible = true }
                .get(engine) as CoroutineScope
            scope.cancel()
        }
    }
}
