package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoSurface
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTileBorder
import com.example.update.AppUpdateError
import com.example.update.AppUpdateManager
import com.example.update.AppUpdatePhase
import com.example.update.AppUpdateState
import java.util.Locale
import kotlin.math.roundToInt

internal enum class UpdateAction { CHECK, DOWNLOAD, INSTALL }

internal data class UpdatePresentation(
    val status: String,
    val actionLabel: String?,
    val action: UpdateAction?,
    val busy: Boolean,
)

/** Hosts [AppUpdateDialogContent] and checks for a new release when it opens. */
@Composable
fun AppUpdateDialog(manager: AppUpdateManager, onDismiss: () -> Unit) {
    val state by manager.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        val phase = manager.state.value.phase
        if (phase == AppUpdatePhase.IDLE || phase == AppUpdatePhase.UP_TO_DATE || phase == AppUpdatePhase.ERROR) {
            manager.checkForUpdate()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            AppUpdateDialogContent(
                state = state,
                onCheck = manager::checkForUpdate,
                onDownload = manager::startDownload,
                onInstall = manager::installUpdate,
                onDismiss = onDismiss
            )
        }
    }
}

/** The dialog body, stateless so screenshots and drives can pin every phase. */
@Composable
fun AppUpdateDialogContent(
    state: AppUpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = presentUpdate(state)
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BentoSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("App updates", color = BentoTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Installed v${state.installed.versionName} (${state.installed.versionCode})",
                    color = BentoTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (presentation.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = BentoPrimary,
                    trackColor = BentoTileBorder,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(presentation.status, color = BentoTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }

        if (state.phase == AppUpdatePhase.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BentoPrimary,
                trackColor = BentoTileBorder,
                drawStopIndicator = {}
            )
        }

        if (state.phase == AppUpdatePhase.AVAILABLE) {
            state.release?.releaseNotes?.let { notes ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What's new", color = BentoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(notes, color = BentoTextSecondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 5)
                }
            }
            Text(
                "Android will ask you to confirm installation.",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    if (state.phase == AppUpdatePhase.AVAILABLE) "Not now" else "Close",
                    color = BentoTextSecondary,
                    fontSize = 13.sp
                )
            }
            if (presentation.actionLabel != null) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        when (presentation.action) {
                            UpdateAction.CHECK -> onCheck()
                            UpdateAction.DOWNLOAD -> onDownload()
                            UpdateAction.INSTALL -> onInstall()
                            null -> Unit
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(presentation.actionLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Header entry point: progress ring while downloading, accent tint when action is needed. */
@Composable
fun AppUpdateHeaderAction(state: AppUpdateState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val needsAttention = state.phase == AppUpdatePhase.AVAILABLE ||
        state.phase == AppUpdatePhase.READY ||
        state.phase == AppUpdatePhase.ERROR
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, BentoTileBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "App updates" },
        contentAlignment = Alignment.Center
    ) {
        if (state.phase == AppUpdatePhase.DOWNLOADING) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(22.dp),
                color = BentoPrimary,
                trackColor = BentoTileBorder,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = if (needsAttention) BentoPrimary else BentoTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

internal fun presentUpdate(state: AppUpdateState): UpdatePresentation = when (state.phase) {
    AppUpdatePhase.IDLE -> UpdatePresentation(
        "Check GitHub for the newest release.",
        "Check for updates",
        UpdateAction.CHECK,
        busy = false
    )
    AppUpdatePhase.CHECKING -> UpdatePresentation(
        "Checking for updates…",
        null,
        null,
        busy = true
    )
    AppUpdatePhase.UP_TO_DATE -> UpdatePresentation(
        "You are on the latest version.",
        "Check again",
        UpdateAction.CHECK,
        busy = false
    )
    AppUpdatePhase.AVAILABLE -> UpdatePresentation(
        "Version ${state.release?.versionName.orEmpty()} is available (${formatUpdateSize(state.release?.sizeBytes ?: 0L)}).",
        "Download update",
        UpdateAction.DOWNLOAD,
        busy = false
    )
    AppUpdatePhase.DOWNLOADING -> UpdatePresentation(
        if (state.waitingForNetwork) {
            "Waiting for network…"
        } else {
            "Downloading ${state.release?.versionName.orEmpty()} (${(state.progress * 100).roundToInt()}%)"
        },
        null,
        null,
        busy = true
    )
    AppUpdatePhase.READY -> UpdatePresentation(
        "Update downloaded. Install to finish.",
        "Install",
        UpdateAction.INSTALL,
        busy = false
    )
    AppUpdatePhase.ERROR -> {
        val (status, action) = when (state.error) {
            AppUpdateError.CHECK -> "Could not check for updates." to UpdateAction.CHECK
            AppUpdateError.DOWNLOAD -> "Download failed." to UpdateAction.DOWNLOAD
            AppUpdateError.VERIFY -> "The downloaded update failed verification." to UpdateAction.DOWNLOAD
            AppUpdateError.INSTALL -> "Could not open the installer." to UpdateAction.INSTALL
            null -> "Something went wrong." to UpdateAction.CHECK
        }
        UpdatePresentation(status, "Retry", action, busy = false)
    }
}

private fun formatUpdateSize(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
