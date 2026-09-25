package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppUpdateDialogContent
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.update.AppUpdatePhase
import com.example.update.AppUpdateState
import com.example.update.AvailableRelease
import com.example.update.InstalledVersion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AppUpdateDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun availableScreenshot() {
        capture(
            state = state().copy(phase = AppUpdatePhase.AVAILABLE, release = release()),
            filePath = "src/test/screenshots/app-update-available.png"
        )
    }

    @Test
    fun downloadingScreenshot() {
        capture(
            state = state().copy(
                phase = AppUpdatePhase.DOWNLOADING,
                release = release(),
                progress = 0.42f
            ),
            filePath = "src/test/screenshots/app-update-downloading.png"
        )
    }

    @Test
    fun readyScreenshot() {
        capture(
            state = state().copy(phase = AppUpdatePhase.READY, release = release(), progress = 1f),
            filePath = "src/test/screenshots/app-update-ready.png"
        )
    }

    private fun capture(state: AppUpdateState, filePath: String) {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier.fillMaxSize().background(BentoBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                        tonalElevation = 6.dp
                    ) {
                        AppUpdateDialogContent(
                            state = state,
                            onCheck = {},
                            onDownload = {},
                            onInstall = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = filePath)
    }

    private fun state() = AppUpdateState(installed = InstalledVersion("1.0", 1))

    private fun release() = AvailableRelease(
        versionName = "1.3.0",
        versionCode = 42,
        assetName = "aftertaste-v1.3.0-42.apk",
        downloadUrl = "https://github.com/skychaze/Aftertaste/releases/download/v1.3.0/aftertaste-v1.3.0-42.apk",
        sizeBytes = 16_612_045L,
        sha256 = null
    )
}
