package com.example.tracker

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Public Online Music Genre Resolver.
 * Queries public music databases (such as Apple's iTunes Search API and MusicBrainz)
 * to retrieve real, verified industry genre tags for played songs without requiring
 * user accounts, API keys, or registration.
 *
 * Includes an in-memory LRU cache and automatic fallback to the local GenreClassifier
 * dictionary when offline or when listening to unreleased/custom mixes.
 */
object MusicGenreResolver {

    private const val TAG = "MusicGenreResolver"
    private const val TIMEOUT_MS = 3500

    // In-memory cache: "artist|title" -> resolvedGenre
    private val genreCache = ConcurrentHashMap<String, String>()

    /**
     * Resolves the genre for a track asynchronously.
     * Checks Spotify Developer API (if configured), then iTunes API, MusicBrainz,
     * with local fallback and caching.
     */
    suspend fun resolveGenre(
        artist: String?,
        title: String?,
        album: String?,
        context: android.content.Context? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanArtist = artist?.trim() ?: ""
        val cleanTitle = title?.trim() ?: ""
        val cleanAlbum = album?.trim() ?: ""

        if (cleanArtist.isBlank() && cleanTitle.isBlank()) {
            return@withContext "Pop"
        }

        val cacheKey = "${cleanArtist.lowercase()}|${cleanTitle.lowercase()}"
        genreCache[cacheKey]?.let { return@withContext it }

        // 1. Try Spotify Developer Web API if configured
        if (context != null && SpotifyGenreResolver.isConfigured(context)) {
            val spotifyGenre = SpotifyGenreResolver.fetchGenre(context, cleanArtist, cleanTitle)
            if (!spotifyGenre.isNullOrBlank()) {
                val normalized = GenreClassifier.normalizeApiGenre(spotifyGenre)
                genreCache[cacheKey] = normalized
                return@withContext normalized
            }
        }

        // 2. Try public iTunes Search API (fast, comprehensive global catalog, no key needed)
        val itunesGenre = queryItunesSearchApi(cleanArtist, cleanTitle)
        if (!itunesGenre.isNullOrBlank()) {
            val normalized = GenreClassifier.normalizeApiGenre(itunesGenre)
            genreCache[cacheKey] = normalized
            return@withContext normalized
        }

        // 3. Try MusicBrainz Open API (open-source community music database)
        val mbGenre = queryMusicBrainzApi(cleanArtist, cleanTitle)
        if (!mbGenre.isNullOrBlank()) {
            val normalized = GenreClassifier.normalizeApiGenre(mbGenre)
            genreCache[cacheKey] = normalized
            return@withContext normalized
        }

        // 4. Fallback to local heuristic dictionary (offline / instantaneous)
        val fallback = GenreClassifier.classify(cleanArtist, cleanTitle, cleanAlbum)
        genreCache[cacheKey] = fallback
        return@withContext fallback
    }

    /**
     * Queries Apple's public iTunes Search API.
     * Returns the "primaryGenreName" (e.g. "Rock", "Pop", "Hip-Hop/Rap", "Electronic", "R&B/Soul").
     */
    private fun queryItunesSearchApi(artist: String, title: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val query = "$artist $title".trim()
            val urlString = ITunesSearchApi.buildSearchUrl(artist, title)
            val url = URL(urlString)

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; YTMusicTracker)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val firstItem = results.getJSONObject(0)
                    val genre = firstItem.optString("primaryGenreName")
                    if (genre.isNotBlank()) {
                        Log.d(TAG, "Resolved via iTunes API: '$query' -> $genre")
                        return genre
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "iTunes API lookup failed or offline for '$artist - $title': ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Queries the MusicBrainz public REST API as a secondary fallback.
     */
    private fun queryMusicBrainzApi(artist: String, title: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val query = "recording:${URLEncoder.encode(title, "UTF-8")}%20AND%20artist:${URLEncoder.encode(artist, "UTF-8")}"
            val urlString = "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=1"
            val url = URL(urlString)

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "YTMusicTracker/1.0 (contact: info@example.com)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val recordings = json.optJSONArray("recordings")
                if (recordings != null && recordings.length() > 0) {
                    val rec = recordings.getJSONObject(0)
                    val tags = rec.optJSONArray("tags")
                    if (tags != null && tags.length() > 0) {
                        return tags.getJSONObject(0).optString("name")
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "MusicBrainz API lookup failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
