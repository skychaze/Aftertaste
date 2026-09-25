package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.MusicTrackerRepository
import com.example.data.ResolvedGenreEntity
import com.example.tracker.GenreTag
import com.example.tracker.GenreTags
import com.example.tracker.GenreResolution
import com.example.tracker.MusicGenreResolver
import com.example.tracker.firstResolved
import com.example.tracker.resolveByPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GenreResolutionTest {
    @Test fun tagsUseWeightsAndIgnoreLabels() {
        assertEquals("Rock", GenreTags.score(listOf(GenreTag("seen live", 500), GenreTag("rock", 40), GenreTag("pop", 10)), 0.9)?.genre)
        assertNull(GenreTags.mapTag("1990s"))
        assertNull(GenreTags.mapTag("favorites"))
        assertEquals("Electronic", GenreTags.mapTag("drum-and-bass"))
        assertEquals("artist\u001fsong", GenreTags.trackKey(" ARTIST ", " Song "))
    }

    @Test fun fallbackStopsAtFirstUsableSource() {
        val calls = mutableListOf<String>()
        val result = firstResolved(
            { calls += "track"; null },
            { calls += "artist"; GenreResolution("Rock", 0.7, "lastfm_artist") },
            { calls += "musicbrainz"; GenreResolution("Pop", 0.8, "musicbrainz") }
        )
        assertEquals(listOf("track", "artist"), calls)
        assertEquals("Rock", result?.genre)
    }

    @Test fun lastFmHasPriorityOverCatalogFallback() {
        val calls = mutableListOf<String>()
        val resolved = resolveByPriority(
            lastFmTrack = { calls += "lastfm_track"; GenreResolution("Bengali-Romantic", 0.9, "lastfm_track") },
            lastFmArtist = { calls += "lastfm_artist"; null },
            itunes = { calls += "itunes"; GenreResolution("Pop", 0.9, "itunes") },
            musicBrainz = { calls += "musicbrainz"; null },
            local = { calls += "local"; null }
        )
        assertEquals("Bengali-Romantic", resolved?.genre)
        assertEquals(listOf("lastfm_track"), calls)

        calls.clear()
        val fallback = resolveByPriority(
            lastFmTrack = { calls += "lastfm_track"; null },
            lastFmArtist = { calls += "lastfm_artist"; null },
            itunes = { calls += "itunes"; GenreResolution("C-Pop", 0.9, "itunes") },
            musicBrainz = { calls += "musicbrainz"; null },
            local = { calls += "local"; null }
        )
        assertEquals("C-Pop", fallback?.genre)
        assertEquals(listOf("lastfm_track", "lastfm_artist", "itunes"), calls)
    }

    @Test fun lastFmTagResponseHandlesEmptyAndMalformedTags() {
        val tags = MusicGenreResolver.parseLastFmTags(JSONObject("""{"toptags":{"tag":[{"name":"1990s","count":80},{"name":"alternative rock","count":45},{"name":"pop","count":10}]}}"""))
        assertEquals("Rock", GenreTags.score(tags, 0.95)?.genre)
        assertEquals(emptyList<GenreTag>(), MusicGenreResolver.parseLastFmTags(JSONObject("""{"error":6,"message":"Track not found"}""")))
        assertEquals(emptyList<GenreTag>(), MusicGenreResolver.parseLastFmTags(JSONObject("""{"toptags":{"tag":[]}}""")))
    }

    @Test fun itunesBollywoodTrackWithMultipleCreditedArtistsResolves() {
        val response = JSONObject("""{"results":[{"trackName":"Tum Hi Ho","artistName":"Mithoon & Arijit Singh","primaryGenreName":"Bollywood"}]}""")
        assertEquals("Bollywood", MusicGenreResolver.parseItunesGenre(response, "Arijit Singh", "Tum Hi Ho"))
        assertEquals("Bollywood", GenreTags.mapTag("Bollywood"))
    }

    @Test fun languageAndStyleTagsKeepRegionalGenres() {
        assertEquals("K-Pop", GenreTags.score(listOf(GenreTag("k-pop", 20), GenreTag("pop", 30)), 0.95)?.genre)
        assertEquals("C-Pop", GenreTags.score(listOf(GenreTag("mandopop", 20), GenreTag("pop", 30)), 0.95)?.genre)
        assertEquals("Bengali-Romantic", GenreTags.score(listOf(GenreTag("bengali", 10), GenreTag("romantic", 20)), 0.95)?.genre)
        assertEquals("Bollywood", GenreTags.score(listOf(GenreTag("hindi", 10), GenreTag("bollywood", 20)), 0.95)?.genre)
        assertEquals("Rock", GenreTags.score(listOf(GenreTag("english", 10), GenreTag("rock", 20)), 0.95)?.genre)
        assertEquals("Bengali", GenreTags.score(listOf(GenreTag("bengali", 10)), 0.95)?.genre)
        assertEquals("Portuguese-Rock", GenreTags.score(listOf(GenreTag("portuguese", 10), GenreTag("rock", 20)), 0.95)?.genre)
        assertEquals("Heavy Metal", GenreTags.mapTag("heavy metal"))
    }

    @Test fun catalogGenresKeepLanguage() {
        fun genre(raw: String) = MusicGenreResolver.parseItunesGenre(
            JSONObject("""{"results":[{"trackName":"Song","artistName":"Artist","primaryGenreName":"$raw"}]}"""),
            "Artist", "Song"
        )
        assertEquals("K-Pop", genre("K-Pop"))
        assertEquals("C-Pop", genre("Mandopop"))
        assertEquals("Bollywood", genre("Bollywood"))
        assertEquals("Bengali-Pop", MusicGenreResolver.parseItunesGenre(
            JSONObject("""{"results":[{"trackName":"ভালোবাসা","artistName":"Artist","primaryGenreName":"Pop"}]}"""),
            "Artist", "ভালোবাসা"
        ))
        assertEquals("K-Pop", GenreTags.score(listOf(GenreTag("pop", 20)), 0.95, "사랑")?.genre)
    }

    @Test fun manualOverrideUpdatesPastSessionsAndPersists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.musicTrackerDao()
            val repository = MusicTrackerRepository(dao, db)
            repository.startSession("2026-09-25", 2026, 9, "Song", "Artist", "Album", "Pop", sourcePackage = "com.google.android.apps.youtube.music")
            repository.startSession("2026-09-25", 2026, 9, " Song ", "ARTIST", "Album", "Rock", sourcePackage = "com.google.android.apps.youtube.music")
            repository.startSession("2026-09-25", 2026, 9, "Another", "Artist", "Album", "Pop", sourcePackage = "com.google.android.apps.youtube.music")
            repository.setManualGenre("Artist", "Song", "Bhajan")
            assertEquals(listOf("Bhajan", "Bhajan", "Pop"), dao.getAllSessionsSync().sortedBy { it.id }.map { it.genre })
            assertEquals("manual", dao.getResolvedGenre("artist\u001fsong")?.source)
            assertEquals("Bhajan", dao.getResolvedGenre("artist\u001fsong")?.genre)
            val future = repository.startSession("2026-09-26", 2026, 9, "Song", "Artist", "Album", "Pop", sourcePackage = "com.google.android.apps.youtube.music")
            assertEquals("Bhajan", dao.getSessionById(future)?.genre)
            assertEquals("Bhajan", repository.updateSessionGenre(future, "Rock"))
            assertEquals("Bhajan", dao.getSessionById(future)?.genre)
            assertEquals("Bhajan", repository.updateSessionDetails(future, "Song", "Artist", "Album", "Electronic"))
            assertEquals("Bhajan", dao.getSessionById(future)?.genre)
        } finally { db.close() }
    }

    @Test fun cachedResultAndUnknownMetadataSkipNetwork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getInstance(context).musicTrackerDao()
        val key = "cache test artist\u001fcache test song"
        dao.putResolvedGenre(ResolvedGenreEntity(key, "Bhajan", 1.0, "manual", System.currentTimeMillis()))
        assertEquals("Bhajan", MusicGenreResolver.resolveGenre("Cache Test Artist", "Cache Test Song", null, context))
        assertEquals("manual", dao.getResolvedGenre(key)?.source)
        assertEquals("Other", MusicGenreResolver.resolveGenre(null, "Unknown Track", null, context))
    }
}
