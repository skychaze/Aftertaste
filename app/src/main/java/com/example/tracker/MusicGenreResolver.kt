package com.example.tracker

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.ResolvedGenreEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class GenreResolution(val genre: String, val confidence: Double, val source: String)

internal fun firstResolved(vararg sources: () -> GenreResolution?): GenreResolution? =
    sources.firstNotNullOfOrNull { it() }

object MusicGenreResolver {
    private const val TIMEOUT_MS = 3500
    private val lookupMutex = Mutex()
    private val musicBrainzLock = Any()
    private var lastMusicBrainzCall = 0L
    private val itunesLock = Any()
    private var lastItunesCall = 0L
    private val lastFmLock = Any()
    private var lastLastFmCall = 0L
    @Volatile private var lastFmCooldownUntil = 0L

    suspend fun resolveGenre(artist: String?, title: String?, album: String?, context: Context? = null): String =
        withContext(Dispatchers.IO) {
            val key = GenreTags.trackKey(artist, title) ?: return@withContext "Other"
            lookupMutex.withLock {
                val dao = context?.let { AppDatabase.getInstance(it).musicTrackerDao() }
                dao?.getResolvedGenre(key)?.let {
                    val ttl = if (it.source == "manual") Long.MAX_VALUE else if (it.source == "unknown") 86_400_000L else 2_592_000_000L
                    if (System.currentTimeMillis() - it.resolvedAt < ttl) return@withLock it.genre
                }
                val result = resolveSources(artist.orEmpty(), title.orEmpty(), album.orEmpty())
                val current = dao?.getResolvedGenre(key)
                if (current?.source == "manual") return@withLock current.genre
                dao?.putResolvedGenre(ResolvedGenreEntity(key, result.genre, result.confidence, result.source, System.currentTimeMillis()))
                result.genre
            }
        }

    private fun resolveSources(artist: String, title: String, album: String): GenreResolution {
        return firstResolved(
            {
                if (BuildConfig.LASTFM_API_KEY.isBlank()) null else
                    GenreTags.score(lastFmTags("track", artist, title), 0.95)?.let { GenreResolution(it.genre, it.confidence, "lastfm_track") }
            },
            {
                if (BuildConfig.LASTFM_API_KEY.isBlank()) null else
                    GenreTags.score(lastFmTags("artist", artist, title), 0.75)?.let { GenreResolution(it.genre, it.confidence, "lastfm_artist") }
            },
            {
                musicBrainzTags(artist, title)?.let { (tags, scope) ->
                    GenreTags.score(tags, if (scope == "recording") 0.82 else 0.65)?.let {
                        GenreResolution(it.genre, it.confidence, "musicbrainz_$scope")
                    }
                }
            },
            { itunesGenre(artist, title)?.let { GenreResolution(it, 0.65, "itunes") } },
            {
                val local = GenreClassifier.classify(artist, title, album)
                GenreResolution(local, if (local == "Other") 0.0 else 0.4, if (local == "Other") "unknown" else "local")
            }
        ) ?: GenreResolution("Other", 0.0, "unknown")
    }

    private fun lastFmTags(scope: String, artist: String, title: String): List<GenreTag> {
        if (System.currentTimeMillis() < lastFmCooldownUntil) return emptyList()
        synchronized(lastFmLock) {
            val wait = 1100L - (System.currentTimeMillis() - lastLastFmCall)
            if (wait > 0) Thread.sleep(wait)
            lastLastFmCall = System.currentTimeMillis()
        }
        val method = if (scope == "track") "track.getTopTags" else "artist.getTopTags"
        val url = "https://ws.audioscrobbler.com/2.0/?method=$method&artist=${encode(artist)}" +
            (if (scope == "track") "&track=${encode(title)}" else "") +
            "&api_key=${encode(BuildConfig.LASTFM_API_KEY)}&format=json&autocorrect=1"
        val json = getJson(url, lastFm = true) ?: return emptyList()
        if (json.optInt("error") == 29) {
            lastFmCooldownUntil = System.currentTimeMillis() + 60_000L
            return emptyList()
        }
        return parseLastFmTags(json)
    }

    private fun musicBrainzTags(artist: String, title: String): Pair<List<GenreTag>, String>? {
        val query = "recording:\"$title\" AND artist:\"$artist\""
        val search = getJson("https://musicbrainz.org/ws/2/recording/?query=${encode(query)}&fmt=json&limit=3", true)
        val recordings = search?.optJSONArray("recordings") ?: JSONArray()
        for (i in 0 until recordings.length()) {
            val candidate = recordings.optJSONObject(i) ?: continue
            if (GenreTags.normalize(candidate.optString("title")) != GenreTags.normalize(title)) continue
            val credit = candidate.optJSONArray("artist-credit")?.optJSONObject(0)
            val candidateArtist = credit?.optJSONObject("artist")?.optString("name") ?: continue
            if (GenreTags.normalize(candidateArtist) != GenreTags.normalize(artist)) continue
            val id = candidate.optString("id")
            if (id.isBlank()) continue
            val recording = getJson("https://musicbrainz.org/ws/2/recording/$id?inc=genres+tags&fmt=json", true)
            val recordingTags = readTags(recording?.optJSONArray("genres")) + readTags(recording?.optJSONArray("tags"))
            if (GenreTags.score(recordingTags, 1.0) != null) return recordingTags to "recording"
            val artistId = credit.optJSONObject("artist")?.optString("id").orEmpty()
            if (artistId.isNotBlank()) {
                val artistJson = getJson("https://musicbrainz.org/ws/2/artist/$artistId?inc=genres+tags&fmt=json", true)
                val artistTags = readTags(artistJson?.optJSONArray("genres")) + readTags(artistJson?.optJSONArray("tags"))
                if (artistTags.isNotEmpty()) return artistTags to "artist"
            }
        }
        val artistSearch = getJson("https://musicbrainz.org/ws/2/artist/?query=${encode("artist:\"$artist\"")}&fmt=json&limit=3", true)
        val artists = artistSearch?.optJSONArray("artists") ?: return null
        for (i in 0 until artists.length()) {
            val candidate = artists.optJSONObject(i) ?: continue
            if (GenreTags.normalize(candidate.optString("name")) != GenreTags.normalize(artist)) continue
            val id = candidate.optString("id")
            if (id.isBlank()) continue
            val details = getJson("https://musicbrainz.org/ws/2/artist/$id?inc=genres+tags&fmt=json", true)
            val tags = readTags(details?.optJSONArray("genres")) + readTags(details?.optJSONArray("tags"))
            if (tags.isNotEmpty()) return tags to "artist"
        }
        return null
    }

    private fun itunesGenre(artist: String, title: String): String? {
        synchronized(itunesLock) {
            val wait = 3100L - (System.currentTimeMillis() - lastItunesCall)
            if (wait > 0) Thread.sleep(wait)
            lastItunesCall = System.currentTimeMillis()
        }
        val json = getJson(ITunesSearchApi.buildSearchUrl(artist, title)) ?: return null
        val results = json.optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            if (GenreTags.normalize(item.optString("trackName")) != GenreTags.normalize(title) ||
                GenreTags.normalize(item.optString("artistName")) != GenreTags.normalize(artist)) continue
            GenreTags.mapTag(item.optString("primaryGenreName"))?.let { return it }
        }
        return null
    }

    private fun readTags(array: JSONArray?): List<GenreTag> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val tag = array.optJSONObject(i) ?: continue
            val count = tag.optInt("count", 1)
            add(GenreTag(tag.optString("name"), count))
        }
    }

    internal fun parseLastFmTags(json: JSONObject): List<GenreTag> =
        readTags(json.optJSONObject("toptags")?.optJSONArray("tag"))

    private fun getJson(url: String, musicBrainz: Boolean = false, lastFm: Boolean = false): JSONObject? {
        if (musicBrainz) synchronized(musicBrainzLock) {
            val wait = 1100L - (System.currentTimeMillis() - lastMusicBrainzCall)
            if (wait > 0) Thread.sleep(wait)
            lastMusicBrainzCall = System.currentTimeMillis()
        }
        val connection = try { URL(url).openConnection() as HttpURLConnection } catch (_: Exception) { return null }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AfterTaste/${BuildConfig.VERSION_NAME} (https://github.com/skychaze/Aftertaste)")
            if (connection.responseCode == 429 && lastFm) {
                lastFmCooldownUntil = System.currentTimeMillis() + 60_000L
                return null
            }
            if (connection.responseCode != 200) return null
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.w("MusicGenreResolver", "Genre lookup failed: ${e.javaClass.simpleName}")
            null
        } finally { connection.disconnect() }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
