package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.tracker.YouTubeHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YouTubeMusicNavigationTest {
    @Test
    fun `search prefers music app and falls back to browser with identical encoded query`() {
        val attempts = mutableListOf<Intent>()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) {
                attempts += intent
                if (intent.`package` != null) throw ActivityNotFoundException()
            }
        }
        openTrackInYouTubeMusic(context, "Song & more / 東京?", "Artist + guest")
        assertEquals(2, attempts.size)
        assertEquals(YouTubeHelper.PACKAGE_YOUTUBE_MUSIC, attempts[0].`package`)
        assertNull(attempts[1].`package`)
        attempts.forEach {
            assertEquals(Intent.ACTION_VIEW, it.action)
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, it.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            assertEquals("https", it.data?.scheme)
            assertEquals("music.youtube.com", it.data?.host)
            assertEquals("/search", it.data?.path)
            assertEquals("Song & more / 東京? Artist + guest", it.data?.getQueryParameter("q"))
        }
        assertEquals(attempts[0].data, attempts[1].data)
    }

    @Test
    fun `missing app and browser retain the existing helpful message`() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        openTrackInYouTubeMusic(context, "Song", "Artist")
        assertEquals("Install YouTube Music or a browser to open this song.", ShadowToast.getTextOfLatestToast())
    }
}
