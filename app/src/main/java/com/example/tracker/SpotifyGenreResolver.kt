package com.example.tracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Handles Spotify Developer API integration for strict, verified genre detection.
 * Uses the official Spotify Client Credentials Flow (RFC 6749) to query Spotify catalog genres.
 */
object SpotifyGenreResolver {

    private const val TAG = "SpotifyGenreResolver"
    private const val PREFS_NAME = "spotify_developer_prefs"
    private const val KEY_CLIENT_ID = "spotify_client_id"
    private const val KEY_CLIENT_SECRET = "spotify_client_secret"

    private const val TIMEOUT_MS = 4000

    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiresAt: Long = 0L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveCredentials(context: Context, clientId: String, clientSecret: String) {
        getPrefs(context).edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .apply()
        // Invalidate token cache when keys change
        cachedAccessToken = null
        tokenExpiresAt = 0L
    }

    fun getCredentials(context: Context): Pair<String, String> {
        val prefs = getPrefs(context)
        val id = prefs.getString(KEY_CLIENT_ID, "") ?: ""
        val secret = prefs.getString(KEY_CLIENT_SECRET, "") ?: ""
        return Pair(id, secret)
    }

    fun isConfigured(context: Context): Boolean {
        val (id, secret) = getCredentials(context)
        return id.isNotBlank() && secret.isNotBlank()
    }

    /**
     * Tests Spotify Developer credentials by requesting an access token.
     * Returns Result.success(message) or Result.failure(exception).
     */
    suspend fun testConnection(clientId: String, clientSecret: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (clientId.isBlank() || clientSecret.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Client ID and Client Secret cannot be empty."))
            }
            val token = requestAccessToken(clientId.trim(), clientSecret.trim())
            if (token.isNotBlank()) {
                Result.success("Successfully connected to Spotify Developer API!")
            } else {
                Result.failure(Exception("Failed to retrieve access token from Spotify."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the genre for a track using Spotify's official Web API.
     */
    suspend fun fetchGenre(context: Context, artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val (clientId, clientSecret) = getCredentials(context)
        if (clientId.isBlank() || clientSecret.isBlank()) return@withContext null

        try {
            val token = getValidAccessToken(clientId, clientSecret) ?: return@withContext null

            // 1. Search Spotify for track & artist
            val query = "artist:$artist track:$title"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://api.spotify.com/v1/search?q=$encodedQuery&type=track,artist&limit=1"

            val searchResponse = executeGetRequest(searchUrl, token) ?: return@withContext null
            val searchJson = JSONObject(searchResponse)

            // Check if artist was matched directly
            val artistsObj = searchJson.optJSONObject("artists")
            val artistItems = artistsObj?.optJSONArray("items")
            if (artistItems != null && artistItems.length() > 0) {
                val artistEntry = artistItems.getJSONObject(0)
                val genres = artistEntry.optJSONArray("genres")
                if (genres != null && genres.length() > 0) {
                    val genre = genres.getString(0)
                    if (genre.isNotBlank()) {
                        Log.d(TAG, "Spotify Artist Genre resolved: '$artist - $title' -> $genre")
                        return@withContext genre
                    }
                }
            }

            // If track matched, fetch artist profile by ID to get genres
            val tracksObj = searchJson.optJSONObject("tracks")
            val trackItems = tracksObj?.optJSONArray("items")
            if (trackItems != null && trackItems.length() > 0) {
                val trackEntry = trackItems.getJSONObject(0)
                val trackArtists = trackEntry.optJSONArray("artists")
                if (trackArtists != null && trackArtists.length() > 0) {
                    val artistId = trackArtists.getJSONObject(0).optString("id")
                    if (artistId.isNotBlank()) {
                        val artistDetailsUrl = "https://api.spotify.com/v1/artists/$artistId"
                        val artistResponse = executeGetRequest(artistDetailsUrl, token)
                        if (artistResponse != null) {
                            val artistJson = JSONObject(artistResponse)
                            val genres = artistJson.optJSONArray("genres")
                            if (genres != null && genres.length() > 0) {
                                val genre = genres.getString(0)
                                if (genre.isNotBlank()) {
                                    Log.d(TAG, "Spotify Track Artist ID Genre resolved: $genre")
                                    return@withContext genre
                                }
                            }
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Spotify genre lookup failed for '$artist - $title': ${e.message}")
            null
        }
    }

    private suspend fun getValidAccessToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        if (cachedAccessToken != null && now < (tokenExpiresAt - 60_000L)) {
            return cachedAccessToken
        }
        return try {
            val token = requestAccessToken(clientId, clientSecret)
            token
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Spotify access token: ${e.message}")
            null
        }
    }

    private fun requestAccessToken(clientId: String, clientSecret: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://accounts.spotify.com/api/token")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                val authString = "$clientId:$clientSecret"
                val encodedAuth = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $encodedAuth")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            val body = "grant_type=client_credentials"
            connection.outputStream.use { os ->
                os.write(body.toByteArray())
                os.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val token = json.getString("access_token")
                val expiresInSec = json.optLong("expires_in", 3600L)
                cachedAccessToken = token
                tokenExpiresAt = System.currentTimeMillis() + (expiresInSec * 1000L)
                return token
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${connection.responseCode}"
                throw Exception("Spotify auth failed: $errorMsg")
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun executeGetRequest(urlString: String, token: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
