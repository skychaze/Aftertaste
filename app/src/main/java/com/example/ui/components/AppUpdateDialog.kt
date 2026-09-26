package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
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
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTileBorder
import com.example.ui.theme.ProposalBadge
import com.example.ui.theme.ProposalDivider
import com.example.ui.theme.ProposalMuted
import com.example.ui.theme.ProposalPanel
import com.example.ui.theme.ProposalSub
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
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 0.dp
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
    val available = state.phase == AppUpdatePhase.AVAILABLE
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ProposalBadge),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(17.dp))
        Text(
            if (available) "Update AfterTaste" else "App updates",
            color = BentoTextPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(10.dp))
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
            Text(
                if (available) "A new version is ready to download." else presentation.status,
                color = ProposalSub,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
        }
        if (!available) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Installed v${state.installed.versionName} (${state.installed.versionCode})",
                color = ProposalMuted,
                fontSize = 11.sp
            )
        }

        state.release?.let { release ->
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Version ${release.versionName}", color = BentoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(formatUpdateSize(release.sizeBytes), color = ProposalMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(13.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ProposalDivider))
        }

        if (available) {
            state.release?.releaseNotes?.let { notes ->
                Spacer(Modifier.height(15.dp))
                Text("What changes", color = BentoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(notes, color = ProposalSub, fontSize = 12.sp, lineHeight = 19.sp, maxLines = 5)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Your listening history stays on this device. After downloading, Android will ask you to confirm installation.",
                color = ProposalSub,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ProposalPanel)
                    .padding(12.dp)
            )
        }

        if (state.phase == AppUpdatePhase.DOWNLOADING) {
            Spacer(Modifier.height(14.dp))
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

        if (presentation.actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    when (presentation.action) {
                        UpdateAction.CHECK -> onCheck()
                        UpdateAction.DOWNLOAD -> onDownload()
                        UpdateAction.INSTALL -> onInstall()
                        null -> Unit
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(presentation.actionLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                if (available) "Not now" else "Close",
                color = BentoTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/** Header entry point: plain 48 dp proposal action, progress ring while downloading. */
@Composable
fun AppUpdateHeaderAction(state: AppUpdateState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val needsAttention = state.phase == AppUpdatePhase.AVAILABLE ||
        state.phase == AppUpdatePhase.READY ||
        state.phase == AppUpdatePhase.ERROR
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
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
                modifier = Modifier.size(21.dp)
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
