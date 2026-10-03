package com.example.tracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object ArtworkResolver {

    private const val TAG = "ArtworkResolver"
    private val memoryCache = ConcurrentHashMap<String, String>()

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

    fun getCacheKey(artist: String?, title: String?): String {
        val cleanA = artist?.trim()?.lowercase(Locale.ROOT) ?: ""
        val cleanT = title?.trim()?.lowercase(Locale.ROOT) ?: ""
        return md5("$cleanA|$cleanT")
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun saveBitmapToCache(
        context: Context,
        artist: String?,
        title: String?,
        bitmap: Bitmap,
    ): String? {
        return try {
            val key = getCacheKey(artist, title)
            val artworkDir = File(context.filesDir, "artworks").apply { if (!exists()) mkdirs() }
            val file = File(artworkDir, "$key.jpg")
            val output = AtomicFile(file)
            val stream = output.startWrite()
            try {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream))
                output.finishWrite(stream)
            } catch (error: Exception) {
                output.failWrite(stream)
                throw error
            }
            if (file.length() == 0L) return null
            val path = file.absolutePath
            memoryCache[key] = path
            path
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving bitmap to cache: ${e.message}")
            null
        }
    }

    @Synchronized
    fun getCachedArtwork(context: Context, artist: String?, title: String?): String? {
        val key = getCacheKey(artist, title)
        memoryCache[key]?.let { path ->
            if (File(path).isFile && File(path).length() > 0L) return path
            memoryCache.remove(key, path)
        }
        val file = File(File(context.filesDir, "artworks"), "$key.jpg")
        if (file.isFile && file.length() > 0L) {
            memoryCache[key] = file.absolutePath
            return file.absolutePath
        }
        val legacy = File(File(context.cacheDir, "artworks"), "$key.jpg")
        if (legacy.isFile && legacy.length() > 0L) {
            return runCatching {
                file.parentFile?.mkdirs()
                val output = AtomicFile(file)
                val stream = output.startWrite()
                try {
                    legacy.inputStream().use { it.copyTo(stream) }
                    output.finishWrite(stream)
                } catch (error: Exception) {
                    output.failWrite(stream)
                    throw error
                }
                memoryCache[key] = file.absolutePath
                file.absolutePath
            }
                .getOrNull()
        }
        return null
    }

    suspend fun resolveArtwork(
        context: Context,
        artist: String?,
        title: String?,
        directUri: String? = null,
    ): String? =
        withContext(Dispatchers.IO) {
            val cached = getCachedArtwork(context, artist, title)
            if (cached != null) return@withContext cached

            if (!directUri.isNullOrBlank()) {
                persistUri(context, artist, title, directUri)?.let {
                    return@withContext it
                }
            }

            if (
                title.isNullOrBlank() ||
                    artist.isNullOrBlank() ||
                    title.equals("Unknown Track", ignoreCase = true) ||
                    title.equals("YouTube Music", ignoreCase = true)
            ) {
                return@withContext null
            }

            try {
                val url = ITunesSearchApi.buildSearchUrl(artist, title)

                val request =
                    Request.Builder()
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
                                    artwork = artwork.replace("100x100bb.jpg", "300x300bb.jpg")
                                    return@withContext persistUri(context, artist, title, artwork)
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

    private fun persistUri(
        context: Context,
        artist: String?,
        title: String?,
        uri: String,
    ): String? = runCatching {
        val parsed = Uri.parse(uri)
        val bitmap =
            when (parsed.scheme) {
                "http",
                "https" ->
                    httpClient.newCall(Request.Builder().url(uri).build()).execute().use { response
                        ->
                        if (!response.isSuccessful) null
                        else response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                    }
                "content",
                "android.resource" ->
                    context.contentResolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                "file" -> BitmapFactory.decodeFile(parsed.path)
                null -> BitmapFactory.decodeFile(uri)
                else -> null
            } ?: return null
        try {
            saveBitmapToCache(context, artist, title, bitmap)
        } finally {
            bitmap.recycle()
        }
    }
        .getOrNull()
}
