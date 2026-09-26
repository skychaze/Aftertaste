package com.example.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.net.URI

/** Version identity of the installed app. */
data class InstalledVersion(val versionName: String, val versionCode: Long)

/** An installable APK published in the newest GitHub release. */
data class AvailableRelease(
    val versionName: String,
    val versionCode: Long?,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

@JsonClass(generateAdapter = true)
internal data class GitHubReleasePayload(
    @Json(name = "tag_name") val tagName: String? = null,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null,
    val assets: List<GitHubAssetPayload>? = null,
)

@JsonClass(generateAdapter = true)
internal data class GitHubAssetPayload(
    val name: String? = null,
    val size: Long? = null,
    val digest: String? = null,
    @Json(name = "browser_download_url") val downloadUrl: String? = null,
)

internal data class AssetVersion(val versionName: String, val versionCode: Long?)

/**
 * Reads the GitHub `releases/latest` payload and decides whether it is newer
 * than the installed build. Releases before the versioned asset rename carry
 * no version code, so those fall back to comparing version names numerically.
 */
object ReleaseParser {
    private val moshi = Moshi.Builder().build()
    private val releaseAdapter = moshi.adapter(GitHubReleasePayload::class.java)

    private val tagPattern = Regex("^[vV](.+)$")
    private val numericVersionPattern = Regex("^\\d+(\\.\\d+)*$")
    private val versionedAssetPattern = Regex("^aftertaste-v(.+)-(\\d+)\\.apk$", RegexOption.IGNORE_CASE)
    private val legacyAssetPattern = Regex("^aftertaste-[vV](.+)\\.apk$", RegexOption.IGNORE_CASE)
    private val sha256DigestPattern = Regex("^sha256:[0-9a-fA-F]{64}$")
    private val trustedDownloadHosts = setOf("github.com", "objects.githubusercontent.com")

    /** Returns the newest installable APK, or null when the payload is not a usable release. */
    fun parseLatestRelease(payload: String): AvailableRelease? {
        val release = runCatching { releaseAdapter.fromJson(payload) }.getOrNull() ?: return null
        if (release.draft == true || release.prerelease == true) return null
        val tagName = release.tagName ?: return null
        val versionName = tagPattern.matchEntire(tagName)?.groupValues?.get(1) ?: return null
        if (!numericVersionPattern.matches(versionName)) return null

        val candidates = release.assets.orEmpty().mapNotNull { asset -> parseAsset(asset, versionName) }
        if (candidates.isEmpty()) return null
        val best = candidates.maxByOrNull { it.versionCode ?: Long.MIN_VALUE } ?: return null
        if (candidates.size > 1 && (best.versionCode == null || candidates.count { it.versionCode == best.versionCode } > 1)) {
            return null
        }
        return best
    }

    /** True when the release should replace the installed build. */
    fun isUpdateAvailable(installed: InstalledVersion, release: AvailableRelease): Boolean =
        release.versionCode?.let { it > installed.versionCode }
            ?: (compareVersionNames(release.versionName, installed.versionName) > 0)

    /** Compares the leading numeric dotted run of two version names; missing components count as zero. */
    fun compareVersionNames(a: String, b: String): Int {
        val left = numericComponents(a)
        val right = numericComponents(b)
        for (index in 0 until maxOf(left.size, right.size)) {
            val leftPart = left.getOrNull(index) ?: 0L
            val rightPart = right.getOrNull(index) ?: 0L
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return 0
    }

    private fun numericComponents(version: String): List<Long> =
        version.takeWhile { it.isDigit() || it == '.' }
            .split('.')
            .mapNotNull { it.toLongOrNull() }

    /** The local file name for a release, derived from validated version fields. */
    fun downloadFileName(release: AvailableRelease): String =
        release.versionCode?.let { "aftertaste-v${release.versionName}-$it.apk" }
            ?: "aftertaste-v${release.versionName}.apk"

    internal fun parseAssetVersion(assetName: String): AssetVersion? {
        versionedAssetPattern.matchEntire(assetName)?.let { match ->
            val versionName = match.groupValues[1]
            val versionCode = match.groupValues[2].toLongOrNull()
            return if (numericVersionPattern.matches(versionName) && versionCode != null && versionCode > 0) {
                AssetVersion(versionName, versionCode)
            } else {
                null
            }
        }
        legacyAssetPattern.matchEntire(assetName)?.let { match ->
            val versionName = match.groupValues[1]
            return if (numericVersionPattern.matches(versionName)) AssetVersion(versionName, null) else null
        }
        return null
    }

    private fun parseAsset(asset: GitHubAssetPayload, tagVersionName: String): AvailableRelease? {
        val assetName = asset.name ?: return null
        val downloadUrl = asset.downloadUrl ?: return null
        val sizeBytes = asset.size?.takeIf { it > 0 } ?: return null
        if (!isTrustedDownloadUrl(downloadUrl)) return null
        val identity = parseAssetVersion(assetName) ?: return null
        if (!identity.versionName.equals(tagVersionName, ignoreCase = true)) return null
        return AvailableRelease(
            versionName = tagVersionName,
            versionCode = identity.versionCode,
            assetName = assetName,
            downloadUrl = downloadUrl,
            sizeBytes = sizeBytes,
            sha256 = asset.digest?.takeIf { sha256DigestPattern.matches(it) }?.substringAfter(':')?.lowercase(),
        )
    }

    private fun isTrustedDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host?.lowercase() in trustedDownloadHosts
    }
}
