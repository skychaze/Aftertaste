package com.example.ui.components

import com.example.update.AppUpdateError
import com.example.update.AppUpdatePhase
import com.example.update.AppUpdateState
import com.example.update.AvailableRelease
import com.example.update.InstalledVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePresentationTest {
    private val release = AvailableRelease(
        versionName = "1.3.0",
        versionCode = 42,
        assetName = "aftertaste-v1.3.0-42.apk",
        downloadUrl = "https://github.com/skychaze/Aftertaste/releases/download/v1.3.0/aftertaste-v1.3.0-42.apk",
        sizeBytes = 16_612_045L,
        sha256 = null
    )

    @Test
    fun `action follows the phase`() {
        assertEquals(UpdateAction.CHECK, presentUpdate(state(AppUpdatePhase.IDLE)).action)
        assertNull(presentUpdate(state(AppUpdatePhase.CHECKING)).action)
        assertEquals(UpdateAction.CHECK, presentUpdate(state(AppUpdatePhase.UP_TO_DATE)).action)
        assertEquals(UpdateAction.DOWNLOAD, presentUpdate(state(AppUpdatePhase.AVAILABLE, release)).action)
        assertNull(presentUpdate(state(AppUpdatePhase.DOWNLOADING, release)).action)
        assertEquals(UpdateAction.INSTALL, presentUpdate(state(AppUpdatePhase.READY, release)).action)
    }

    @Test
    fun `retry action follows the error kind`() {
        assertEquals(UpdateAction.CHECK, presentUpdate(state(AppUpdatePhase.ERROR, error = AppUpdateError.CHECK)).action)
        assertEquals(UpdateAction.DOWNLOAD, presentUpdate(state(AppUpdatePhase.ERROR, error = AppUpdateError.DOWNLOAD)).action)
        assertEquals(UpdateAction.DOWNLOAD, presentUpdate(state(AppUpdatePhase.ERROR, error = AppUpdateError.VERIFY)).action)
        assertEquals(UpdateAction.INSTALL, presentUpdate(state(AppUpdatePhase.ERROR, error = AppUpdateError.INSTALL)).action)
    }

    @Test
    fun `status reports version size and progress`() {
        val available = presentUpdate(state(AppUpdatePhase.AVAILABLE, release))
        assertTrue(available.status.contains("1.3.0"))
        assertTrue(available.status.contains("15.8 MB"))

        val downloading = presentUpdate(state(AppUpdatePhase.DOWNLOADING, release, progress = 0.42f))
        assertTrue(downloading.status.contains("42%"))
        assertTrue(downloading.busy)

        val waiting = presentUpdate(state(AppUpdatePhase.DOWNLOADING, release, waitingForNetwork = true))
        assertEquals("Waiting for network…", waiting.status)
    }

    private fun state(
        phase: AppUpdatePhase,
        release: AvailableRelease? = null,
        progress: Float = 0f,
        waitingForNetwork: Boolean = false,
        error: AppUpdateError? = null
    ) = AppUpdateState(
        phase = phase,
        installed = InstalledVersion("1.0", 1),
        release = release,
        progress = progress,
        waitingForNetwork = waitingForNetwork,
        error = error
    )
}
