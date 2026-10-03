package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33, 36])
class LauncherIconTest {
    @Test
    fun `both launcher icons theme only the logo and leave its background transparent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (resource in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = context.getDrawable(resource) as AdaptiveIconDrawable
            val monochrome = icon.monochrome
            assertNotNull("The launcher must receive a monochrome layer", monochrome)
            val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
            requireNotNull(monochrome).setBounds(0, 0, 108, 108)
            monochrome.draw(Canvas(bitmap))
            val pixels = IntArray(108 * 108)
            bitmap.getPixels(pixels, 0, 108, 0, 0, 108, 108)
            val visiblePixels = pixels.count { Color.alpha(it) > 0 }
            assertTrue("The logo must remain visible", visiblePixels > 0)
            assertTrue(
                "A themed logo must have transparent negative space, not an opaque image tile",
                visiblePixels < pixels.size / 4,
            )
        }
    }
}
