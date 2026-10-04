package com.example

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.MusicTrackerRepository
import com.example.data.PlaybackSessionDurations
import com.example.data.ResolvedGenreEntity
import com.example.tracker.ArtworkResolver
import com.example.tracker.GenreTags
import com.example.tracker.MusicTrackerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class PlaybackTrackingInstrumentedTest {
    @Test
    fun seekBufferingAndMetadataChangePersistSeparateSessions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = SystemClock.uptimeMillis().toString()
        val songATitle = "Instrumented A $fixture"
        val songBTitle = "Instrumented B $fixture"
        val songAArtist = "Instrumented Artist A $fixture"
        val songBArtist = "Instrumented Artist B $fixture"
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val engine = newEngine(context, MusicTrackerRepository(database.musicTrackerDao(), database))
        val session = MediaSessionCompat(context, "Playback tracking instrumented")
        val requestedSeek = AtomicLong(-1L)
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onSeekTo(pos: Long) {
                requestedSeek.set(pos)
                publishState(session, PlaybackStateCompat.STATE_BUFFERING, pos)
            }
        }, Handler(Looper.getMainLooper()))
        seedResolvers(context, songATitle, songAArtist)
        seedResolvers(context, songBTitle, songBArtist)
        engine.setFilterOnlyYouTubeMusic(false)
        session.setMetadata(metadata(songATitle, songAArtist, 240_000L))
        publishState(session, PlaybackStateCompat.STATE_PLAYING, 0L)
        val controller = controller(context, session)

        try {
            instrumentation.runOnMainSync {
                invoke(engine, "switchActiveController", controller)
            }
            await(instrumentation) { engine.getCurrentDbSessionId() != null }
            val songASessionId = engine.getCurrentDbSessionId() ?: error("Song A session was not attached")
            await(instrumentation) { engine.uiState.value.currentSessionSeconds >= 5L }

            instrumentation.runOnMainSync { engine.seekTo(150_000L) }
            await(instrumentation) { requestedSeek.get() == 150_000L }

            instrumentation.runOnMainSync {
                session.setMetadata(metadata(songBTitle, songBArtist, 180_000L))
                publishState(session, PlaybackStateCompat.STATE_PLAYING, 0L)
            }
            await(instrumentation) {
                engine.getCurrentDbSessionId() != null && engine.getCurrentDbSessionId() != songASessionId
            }
            val songBSessionId = engine.getCurrentDbSessionId() ?: error("Song B session was not attached")
            assertNotEquals(songASessionId, songBSessionId)
            assertEquals(songBTitle, engine.uiState.value.trackTitle)
            assertEquals(180_000L, engine.uiState.value.trackDurationMs)
            await(instrumentation) { engine.uiState.value.currentSessionSeconds >= 5L }
            assertTrue(engine.uiState.value.trackPositionMs in 1L..180_000L)

            instrumentation.runOnMainSync {
                publishState(session, PlaybackStateCompat.STATE_STOPPED, engine.uiState.value.trackPositionMs)
            }
            await(instrumentation) {
                runBlocking {
                    database.musicTrackerDao().getAllSessionsSync().let { rows ->
                        rows.size == 2 && rows.all { !it.isOpen && it.durationSeconds >= 5L }
                    }
                }
            }
            val rows = runBlocking {
                database.musicTrackerDao().getAllSessionsSync().sortedBy { it.id }
            }
            assertEquals(listOf(songATitle, songBTitle), rows.map { it.title })
            assertTrue(rows.all { it.durationSeconds >= 5L && !it.isOpen })
            assertTrue(rows.all {
                PlaybackSessionDurations.parse(it.dailyDurations).values.sum() == it.durationSeconds
            })
        } finally {
            instrumentation.runOnMainSync {
                session.release()
                engine.onPlaybackPausedOrStopped()
                invoke(engine, "stopTicker")
            }
            settle(engine)
            cancelEngine(engine)
            database.close()
        }
    }

    private fun newEngine(context: Context, repository: MusicTrackerRepository): MusicTrackerEngine =
        MusicTrackerEngine::class.java.getDeclaredConstructor(Context::class.java, MusicTrackerRepository::class.java)
            .apply { isAccessible = true }
            .newInstance(context, repository)

    private fun controller(context: Context, session: MediaSessionCompat): MediaController =
        MediaController(context, session.sessionToken.token as MediaSession.Token)

    private fun metadata(title: String, artist: String, durationMs: Long) =
        MediaMetadataCompat.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build()

    private fun publishState(session: MediaSessionCompat, state: Int, positionMs: Long) {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, positionMs, 1f, SystemClock.elapsedRealtime())
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
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
                ResolvedGenreEntity(key, "Pop", 1.0, "manual", System.currentTimeMillis())
            )
        }
        val artwork = File(context.filesDir, "artworks/${ArtworkResolver.getCacheKey(artist, title)}.jpg")
        artwork.parentFile?.mkdirs()
        artwork.writeBytes(byteArrayOf(1))
    }

    private fun await(instrumentation: android.app.Instrumentation, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 12_000L
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            Thread.sleep(20L)
        }
        assertTrue("Timed out waiting for playback state", condition())
    }

    private fun settle(engine: MusicTrackerEngine) {
        runBlocking {
            repeat(10) {
                val tail = MusicTrackerEngine::class.java.getDeclaredField("dbWriteTail")
                    .apply { isAccessible = true }
                    .get(engine) as? Job
                tail?.let { withTimeout(5_000L) { it.join() } }
            }
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
