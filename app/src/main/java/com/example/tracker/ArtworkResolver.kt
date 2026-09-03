package com.example.tracker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ArtworkResolver {

    private const val TAG = "ArtworkResolver"
    private val memoryCache = ConcurrentHashMap<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun getCacheKey(artist: String?, title: String?): String {
        val cleanA = artist?.trim()?.lowercase() ?: ""
        val cleanT = title?.trim()?.lowercase() ?: ""
        return md5("$cleanA|$cleanT")
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun saveBitmapToCache(context: Context, artist: String?, title: String?, bitmap: Bitmap): String? {
        return try {
            val key = getCacheKey(artist, title)
            val artworkDir = File(context.cacheDir, "artworks").apply { if (!exists()) mkdirs() }
            val file = File(artworkDir, "$key.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            val path = file.absolutePath
            memoryCache[key] = path
            path
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving bitmap to cache: ${e.message}")
            null
        }
    }

    fun getCachedArtwork(context: Context, artist: String?, title: String?): String? {
        val key = getCacheKey(artist, title)
        memoryCache[key]?.let { return it }

        val artworkDir = File(context.cacheDir, "artworks")
        val file = File(artworkDir, "$key.jpg")
        if (file.exists() && file.length() > 0) {
            val path = file.absolutePath
            memoryCache[key] = path
            return path
        }
        return null
    }

    suspend fun resolveArtwork(
        context: Context,
        artist: String?,
        title: String?,
        directUri: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!directUri.isNullOrBlank()) {
            val key = getCacheKey(artist, title)
            memoryCache[key] = directUri
            return@withContext directUri
        }

        val cached = getCachedArtwork(context, artist, title)
        if (cached != null) return@withContext cached

        if (title.isNullOrBlank() || artist.isNullOrBlank() ||
            title.equals("Unknown Track", ignoreCase = true) ||
            title.equals("YouTube Music", ignoreCase = true)
        ) {
            return@withContext null
        }

        try {
            val url = ITunesSearchApi.buildSearchUrl(artist, title)

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AfterTaste-MusicTracker/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val item = results.getJSONObject(0)
                            var artwork = item.optString("artworkUrl100", "")
                            if (artwork.isNotBlank()) {
                                // Upgrade to 300x300 or 600x600 resolution
                                artwork = artwork.replace("100x100bb.jpg", "300x300bb.jpg")
                                val key = getCacheKey(artist, title)
                                memoryCache[key] = artwork
                                return@withContext artwork
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Online artwork lookup failed: ${e.message}")
        }
        null
    }
}
