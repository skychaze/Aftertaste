package com.example.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    @Test
    fun `versioned asset name carries the version code`() {
        assertEquals(AssetVersion("1.1.4", 14), ReleaseParser.parseAssetVersion("aftertaste-v1.1.4-14.apk"))
    }

    @Test
    fun `legacy asset name carries only the version name`() {
        assertEquals(AssetVersion("1.2.1", null), ReleaseParser.parseAssetVersion("AfterTaste-V1.2.1.apk"))
    }

    @Test
    fun `unversioned and malformed asset names are rejected`() {
        assertNull(ReleaseParser.parseAssetVersion("app-release.apk"))
        assertNull(ReleaseParser.parseAssetVersion("aftertaste-v1.1.4-abc.apk"))
        assertNull(ReleaseParser.parseAssetVersion("aftertaste-v1.1.4-0.apk"))
        assertNull(ReleaseParser.parseAssetVersion("aftertaste-v1.1.4-.apk"))
        assertNull(ReleaseParser.parseAssetVersion("notes.txt"))
    }

    @Test
    fun `legacy release parses with null version code and digest`() {
        val release = ReleaseParser.parseLatestRelease(legacyRelease())

        assertEquals("1.2.1", release?.versionName)
        assertNull(release?.versionCode)
        assertEquals("AfterTaste-V1.2.1.apk", release?.assetName)
        assertEquals("https://github.com/skychaze/Aftertaste/releases/download/V1.2.1/AfterTaste-V1.2.1.apk", release?.downloadUrl)
        assertEquals(16_612_045L, release?.sizeBytes)
        assertEquals("5f46e8b97bde239ec558109de4174f782e38a381a4a977266362ecc86b0658dc", release?.sha256)
    }

    @Test
    fun `versioned release parses with its version code`() {
        val release = ReleaseParser.parseLatestRelease(
            releasePayload(
                tag = "v1.3.0",
                assets = listOf(asset("aftertaste-v1.3.0-42.apk", "https://github.com/skychaze/Aftertaste/releases/download/v1.3.0/x.apk"))
            )
        )

        assertEquals("1.3.0", release?.versionName)
        assertEquals(42L, release?.versionCode)
    }

    @Test
    fun `highest version code wins when a release carries several matching assets`() {
        val release = ReleaseParser.parseLatestRelease(
            releasePayload(
                tag = "v1.3.0",
                assets = listOf(
                    asset("aftertaste-v1.3.0-41.apk", "https://github.com/skychaze/Aftertaste/releases/download/v1.3.0/a.apk"),
                    asset("aftertaste-v1.3.0-42.apk", "https://github.com/skychaze/Aftertaste/releases/download/v1.3.0/b.apk")
                )
            )
        )

        assertEquals(42L, release?.versionCode)
        assertEquals("aftertaste-v1.3.0-42.apk", release?.assetName)
    }

    @Test
    fun `ambiguous assets with the same version code are rejected`() {
        val release = ReleaseParser.parseLatestRelease(
            releasePayload(
                tag = "v1.3.0",
                assets = listOf(
                    asset("aftertaste-v1.3.0-42.apk", "https://github.com/skychaze/Aftertaste/releases/download/v1.3.0/a.apk"),
                    asset("aftertaste-v1.3.0-42.apk", "https://github.com/skychaze/Aftertaste/releases/download/v1.3.0/b.apk")
                )
            )
        )

        assertNull(release)
    }

    @Test
    fun `asset whose version does not match the tag is rejected`() {
        val release = ReleaseParser.parseLatestRelease(
            releasePayload(
                tag = "v1.4.0",
                assets = listOf(asset("aftertaste-v1.3.0-42.apk", "https://github.com/skychaze/Aftertaste/releases/download/v1.4.0/x.apk"))
            )
        )

        assertNull(release)
    }

    @Test
    fun `uppercase tags are accepted`() {
        val release = ReleaseParser.parseLatestRelease(legacyRelease())
        assertEquals("1.2.1", release?.versionName)
        assertNull(release?.versionCode)
    }

    @Test
    fun `draft and prerelease payloads are not offered`() {
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "v1.3.0", draft = true)))
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "v1.3.0", prerelease = true)))
    }

    @Test
    fun `malformed payloads are not offered`() {
        assertNull(ReleaseParser.parseLatestRelease("not json"))
        assertNull(ReleaseParser.parseLatestRelease("{}"))
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "nightly")))
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "v1.3.0", assets = emptyList())))
    }

    @Test
    fun `asset without url or with a non-positive size is rejected`() {
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "v1.3.0", assets = listOf(asset("aftertaste-v1.3.0-42.apk", null)))))
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "v1.3.0", assets = listOf(asset("aftertaste-v1.3.0-42.apk", "https://github.com/x.apk", size = 0)))))
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "v1.3.0", assets = listOf(asset("aftertaste-v1.3.0-42.apk", "https://github.com/x.apk", size = -1)))))
    }

    @Test
    fun `download urls must be https on a trusted host`() {
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "v1.3.0", assets = listOf(asset("aftertaste-v1.3.0-42.apk", "http://github.com/x.apk")))))
        assertNull(ReleaseParser.parseLatestRelease(releasePayload(tag = "v1.3.0", assets = listOf(asset("aftertaste-v1.3.0-42.apk", "https://evil.test/x.apk")))))
        assertTrue(
            ReleaseParser.parseLatestRelease(
                releasePayload(tag = "v1.3.0", assets = listOf(asset("aftertaste-v1.3.0-42.apk", "https://objects.githubusercontent.com/x.apk")))
            ) != null
        )
    }

    @Test
    fun `invalid digest is dropped instead of rejecting the release`() {
        val release = ReleaseParser.parseLatestRelease(
            releasePayload(
                tag = "v1.3.0",
                assets = listOf(asset("aftertaste-v1.3.0-42.apk", "https://github.com/x.apk", digest = "sha256:nope"))
            )
        )

        assertNull(release?.sha256)
    }

    @Test
    fun `update availability prefers the version code`() {
        val installed = InstalledVersion("1.2.1", 7)
        assertTrue(ReleaseParser.isUpdateAvailable(installed, release("1.3.0", 8)))
        assertFalse(ReleaseParser.isUpdateAvailable(installed, release("1.3.0", 7)))
        assertFalse(ReleaseParser.isUpdateAvailable(installed, release("9.9.9", 6)))
    }

    @Test
    fun `update availability falls back to version names without a version code`() {
        val installed = InstalledVersion("1.0", 1)
        assertTrue(ReleaseParser.isUpdateAvailable(installed, release("1.2.1", null)))
        assertFalse(ReleaseParser.isUpdateAvailable(InstalledVersion("1.2.1", 7), release("1.2.1", null)))
        assertFalse(ReleaseParser.isUpdateAvailable(InstalledVersion("1.2.1", 7), release("1.2.0", null)))
    }

    @Test
    fun `version names compare numerically`() {
        assertTrue(ReleaseParser.compareVersionNames("1.10", "1.9") > 0)
        assertTrue(ReleaseParser.compareVersionNames("1.2.1", "1.2") > 0)
        assertEquals(0, ReleaseParser.compareVersionNames("1.2", "1.2.0"))
        assertEquals(0, ReleaseParser.compareVersionNames("1.0.0-manual.5", "1.0.0"))
        assertTrue(ReleaseParser.compareVersionNames("2.0", "10.0") < 0)
    }

    @Test
    fun `download file names are derived from validated version fields`() {
        assertEquals("aftertaste-v1.3.0-42.apk", ReleaseParser.downloadFileName(release("1.3.0", 42)))
        assertEquals("aftertaste-v1.2.1.apk", ReleaseParser.downloadFileName(release("1.2.1", null)))
    }

    private fun release(versionName: String, versionCode: Long?) = AvailableRelease(
        versionName = versionName,
        versionCode = versionCode,
        assetName = "ignored.apk",
        downloadUrl = "https://github.com/skychaze/Aftertaste/releases/download/x/x.apk",
        sizeBytes = 1L,
        sha256 = null
    )

    private fun legacyRelease() = releasePayload(
        tag = "V1.2.1",
        assets = listOf(
            asset(
                name = "AfterTaste-V1.2.1.apk",
                url = "https://github.com/skychaze/Aftertaste/releases/download/V1.2.1/AfterTaste-V1.2.1.apk",
                size = 16_612_045L,
                digest = "sha256:5F46E8B97BDE239EC558109DE4174F782E38A381A4A977266362ECC86B0658DC"
            )
        )
    )

    private fun releasePayload(
        tag: String?,
        assets: List<String> = emptyList(),
        draft: Boolean = false,
        prerelease: Boolean = false
    ) = """
        {
          "tag_name": ${tag?.let { "\"$it\"" } ?: "null"},
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": [${assets.joinToString(",")}]
        }
    """.trimIndent()

    private fun asset(
        name: String,
        url: String?,
        size: Long = 1024L,
        digest: String? = null
    ) = """
        {
          "name": "$name",
          "size": $size,
          "digest": ${digest?.let { "\"$it\"" } ?: "null"},
          "browser_download_url": ${url?.let { "\"$it\"" } ?: "null"}
        }
    """.trimIndent()
}
