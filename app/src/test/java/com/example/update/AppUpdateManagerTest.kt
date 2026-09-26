package com.example.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AppUpdateManagerTest {
    @Test
    fun `malformed release response reports a check failure`() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5_000
            val responder = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    val body = "not a release payload"
                    socket.getOutputStream().write(
                        ("HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n" +
                            "Connection: close\r\n\r\n$body").toByteArray()
                    )
                }
            }
            val manager = AppUpdateManager(
                ApplicationProvider.getApplicationContext(),
                "http://127.0.0.1:${server.localPort}/latest"
            )
            manager.checkForUpdate()
            val result = withTimeout(5_000L) {
                manager.state.first { it.phase != AppUpdatePhase.CHECKING }
            }
            responder.join(1_000L)
            assertEquals(AppUpdatePhase.ERROR, result.phase)
            assertEquals(AppUpdateError.CHECK, result.error)
        }
    }
}
