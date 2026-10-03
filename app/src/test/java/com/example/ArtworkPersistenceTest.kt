package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.tracker.ArtworkResolver
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArtworkPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `historical artwork survives Android cache eviction`() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val saved =
            ArtworkResolver.saveBitmapToCache(
                context,
                "Persistence artist",
                "Persistence song",
                bitmap,
            )
        assertNotNull(saved)
        File(context.cacheDir, "artworks").deleteRecursively()

        val artwork =
            ArtworkResolver.getCachedArtwork(context, "Persistence artist", "Persistence song")
        assertNotNull(artwork)
        assertTrue(
            "Historical artwork must remain readable after cache eviction",
            File(requireNotNull(artwork)).isFile,
        )
    }

    @Test
    fun `a deleted file is never returned as usable artwork`() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val saved =
            ArtworkResolver.saveBitmapToCache(context, "Deleted artist", "Deleted song", bitmap)
        File(requireNotNull(saved)).delete()

        assertNull(ArtworkResolver.getCachedArtwork(context, "Deleted artist", "Deleted song"))
    }
}
