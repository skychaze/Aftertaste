package com.example.update

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

enum class AppUpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

enum class AppUpdateError { CHECK, DOWNLOAD, VERIFY, INSTALL }

data class AppUpdateState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val installed: InstalledVersion,
    val release: AvailableRelease? = null,
    val progress: Float = 0f,
    val waitingForNetwork: Boolean = false,
    val error: AppUpdateError? = null,
)

private data class PendingDownload(
    val id: Long,
    val release: AvailableRelease,
    val fileName: String,
)

private const val DEFAULT_LATEST_RELEASE_URL = "https://api.github.com/repos/skychaze/Aftertaste/releases/latest"
private const val PREFERENCES_NAME = "app_update_download"
private const val UPDATE_DIRECTORY = "updates"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

private const val KEY_DOWNLOAD_ID = "download_id"
private const val KEY_VERSION_NAME = "version_name"
private const val KEY_VERSION_CODE = "version_code"
private const val KEY_ASSET_NAME = "asset_name"
private const val KEY_DOWNLOAD_URL = "download_url"
private const val KEY_SIZE_BYTES = "size_bytes"
private const val KEY_SHA256 = "sha256"
private const val KEY_FILE_NAME = "file_name"
private const val KEY_RELEASE_NOTES = "release_notes"

/**
 * Owns the in-app update flow: checks the newest GitHub release, downloads the
 * APK through DownloadManager, verifies it, and hands it to the package
 * installer. One instance lives in [com.example.YTTrackerApplication] so a
 * download survives Activity restarts.
 */
class AppUpdateManager(
    context: Context,
    private val latestReleaseUrl: String = DEFAULT_LATEST_RELEASE_URL,
) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val packageManager = appContext.packageManager
    private val installedVersion = InstalledVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
    private val client = defaultClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AppUpdateState(installed = installedVersion))
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var awaitingInstallPermission = false

    /** Restores a download that outlived the previous process without touching the network. */
    fun onAppStart() {
        scope.launch {
            if (readPendingDownload() == null) {
                pruneUpdateDirectory(keep = null)
            } else {
                refreshDownloadStatus()
            }
        }
    }

    /** Checks the newest release. Ignored while a download is pending or ready. */
    fun checkForUpdate() {
        val phase = _state.value.phase
        if (phase == AppUpdatePhase.CHECKING || phase == AppUpdatePhase.AVAILABLE ||
            phase == AppUpdatePhase.DOWNLOADING || phase == AppUpdatePhase.READY
        ) {
            return
        }
        _state.update { it.copy(phase = AppUpdatePhase.CHECKING, error = null) }
        scope.launch {
            fetchLatestRelease()
                .onSuccess { release ->
                    if (!ReleaseParser.isUpdateAvailable(installedVersion, release)) {
                        forgetDownload(readPendingDownload())
                        markUpToDate()
                    } else {
                        pruneUpdateDirectory(keep = ReleaseParser.downloadFileName(release))
                        val pending = readPendingDownload()
                        if (pending != null && pending.release.versionName == release.versionName &&
                            pending.release.versionCode == release.versionCode
                        ) {
                            refreshDownloadStatus()
                        } else {
                            forgetDownload(pending)
                            _state.update {
                                it.copy(
                                    phase = AppUpdatePhase.AVAILABLE,
                                    release = release,
                                    progress = 0f,
                                    waitingForNetwork = false,
                                    error = null
                                )
                            }
                        }
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(phase = AppUpdatePhase.ERROR, error = AppUpdateError.CHECK, waitingForNetwork = false)
                    }
                }
        }
    }

    /** Enqueues the release APK in DownloadManager and tracks its progress. */
    fun startDownload() {
        val current = _state.value
        val release = current.release ?: return
        val canStart = current.phase == AppUpdatePhase.AVAILABLE ||
            (current.phase == AppUpdatePhase.ERROR &&
                (current.error == AppUpdateError.DOWNLOAD || current.error == AppUpdateError.VERIFY))
        if (!canStart) return

        _state.update {
            it.copy(phase = AppUpdatePhase.DOWNLOADING, progress = 0f, waitingForNetwork = false, error = null)
        }
        scope.launch { enqueueDownload(release) }
    }

    /** Opens the installer, or the unknown-sources setting when Android has not allowed it yet. */
    fun installUpdate() {
        val phase = _state.value.phase
        val canInstall = phase == AppUpdatePhase.READY ||
            (phase == AppUpdatePhase.ERROR && _state.value.error == AppUpdateError.INSTALL)
        if (!canInstall) return
        val pending = readPendingDownload() ?: return
        val file = releaseFile(pending)
        if (!file.isFile || file.length() != pending.release.sizeBytes) {
            forgetDownload(pending)
            _state.update { it.copy(phase = AppUpdatePhase.ERROR, error = AppUpdateError.VERIFY, progress = 0f) }
            return
        }
        if (!canInstallPackages()) {
            awaitingInstallPermission = true
            runCatching {
                appContext.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${appContext.packageName}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                awaitingInstallPermission = false
                _state.update { it.copy(phase = AppUpdatePhase.ERROR, error = AppUpdateError.INSTALL) }
            }
            return
        }
        runCatching { launchInstaller(file) }.onFailure {
            _state.update { it.copy(phase = AppUpdatePhase.ERROR, error = AppUpdateError.INSTALL) }
        }
    }

    /** Resumes an install interrupted by the unknown-sources setting and refreshes download progress. */
    fun onActivityResumed() {
        if (awaitingInstallPermission) {
            awaitingInstallPermission = false
            if (canInstallPackages()) installUpdate()
            return
        }
        if (_state.value.phase == AppUpdatePhase.DOWNLOADING) {
            scope.launch { refreshDownloadStatus() }
        }
    }

    private suspend fun enqueueDownload(release: AvailableRelease) {
        val fileName = ReleaseParser.downloadFileName(release)
        runCatching {
            val request = DownloadManager.Request(release.downloadUrl.toUri())
                .setTitle("AfterTaste update")
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(appContext, null, "$UPDATE_DIRECTORY/$fileName")
            val id = downloadManager.enqueue(request)
            val saved = preferences.edit()
                .putLong(KEY_DOWNLOAD_ID, id)
                .putString(KEY_VERSION_NAME, release.versionName)
                .putLong(KEY_VERSION_CODE, release.versionCode ?: -1L)
                .putString(KEY_ASSET_NAME, release.assetName)
                .putString(KEY_DOWNLOAD_URL, release.downloadUrl)
                .putLong(KEY_SIZE_BYTES, release.sizeBytes)
                .putString(KEY_SHA256, release.sha256)
                .putString(KEY_RELEASE_NOTES, release.releaseNotes)
                .putString(KEY_FILE_NAME, fileName)
                .commit()
            if (!saved) {
                downloadManager.remove(id)
                error("Could not persist the download id")
            }
        }.onFailure {
            _state.update {
                it.copy(phase = AppUpdatePhase.ERROR, release = release, error = AppUpdateError.DOWNLOAD)
            }
            return
        }
        startPolling()
    }

    private suspend fun refreshDownloadStatus() {
        val pending = readPendingDownload()
        if (pending == null) {
            if (_state.value.phase == AppUpdatePhase.DOWNLOADING) {
                _state.update { it.copy(phase = AppUpdatePhase.AVAILABLE, progress = 0f, waitingForNetwork = false) }
            }
            return
        }
        if (!ReleaseParser.isUpdateAvailable(installedVersion, pending.release)) {
            forgetDownload(pending)
            markUpToDate()
            return
        }
        queryDownloadManager(pending)
    }

    private fun markUpToDate() {
        _state.update {
            it.copy(phase = AppUpdatePhase.UP_TO_DATE, release = null, progress = 0f, waitingForNetwork = false, error = null)
        }
    }

    private fun queryDownloadManager(pending: PendingDownload) {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(pending.id))
        cursor.use {
            if (!it.moveToFirst()) {
                forgetDownload(pending)
                setAvailable(pending.release)
                return
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val written = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val progress = (written.toFloat() / pending.release.sizeBytes).coerceIn(0f, 1f)
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> verifyDownload(pending)
                DownloadManager.STATUS_FAILED -> {
                    forgetDownload(pending)
                    _state.update {
                        it.copy(
                            phase = AppUpdatePhase.ERROR,
                            release = pending.release,
                            progress = 0f,
                            waitingForNetwork = false,
                            error = AppUpdateError.DOWNLOAD
                        )
                    }
                }
                DownloadManager.STATUS_PAUSED -> setDownloading(pending.release, progress, waitingForNetwork = true)
                else -> setDownloading(pending.release, progress, waitingForNetwork = false)
            }
        }
    }

    private fun verifyDownload(pending: PendingDownload) {
        val file = releaseFile(pending)
        val valid = file.isFile &&
            file.length() == pending.release.sizeBytes &&
            (pending.release.sha256 == null || sha256(file) == pending.release.sha256) &&
            apkMatchesInstalledApp(file)
        if (valid) {
            stopPolling()
            _state.update {
                it.copy(phase = AppUpdatePhase.READY, release = pending.release, progress = 1f, waitingForNetwork = false, error = null)
            }
        } else {
            forgetDownload(pending)
            _state.update {
                it.copy(
                    phase = AppUpdatePhase.ERROR,
                    release = pending.release,
                    progress = 0f,
                    waitingForNetwork = false,
                    error = AppUpdateError.VERIFY
                )
            }
        }
    }

    private fun setAvailable(release: AvailableRelease) {
        _state.update {
            it.copy(phase = AppUpdatePhase.AVAILABLE, release = release, progress = 0f, waitingForNetwork = false, error = null)
        }
    }

    private fun setDownloading(release: AvailableRelease, progress: Float, waitingForNetwork: Boolean) {
        _state.update {
            it.copy(
                phase = AppUpdatePhase.DOWNLOADING,
                release = release,
                progress = progress,
                waitingForNetwork = waitingForNetwork,
                error = null
            )
        }
        startPolling()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_state.value.phase != AppUpdatePhase.DOWNLOADING) break
                refreshDownloadStatus()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun fetchLatestRelease(): Result<AvailableRelease> = runCatching {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AfterTaste")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("release check failed with status ${response.code}")
            val body = response.body?.string() ?: error("empty release response")
            ReleaseParser.parseLatestRelease(body) ?: error("release response has no usable APK")
        }
    }

    private fun readPendingDownload(): PendingDownload? {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id < 0) return null
        val fileName = preferences.getString(KEY_FILE_NAME, null) ?: return null
        val versionName = preferences.getString(KEY_VERSION_NAME, null) ?: return null
        val downloadUrl = preferences.getString(KEY_DOWNLOAD_URL, null) ?: return null
        val sizeBytes = preferences.getLong(KEY_SIZE_BYTES, 0L).takeIf { it > 0 } ?: return null
        val release = AvailableRelease(
            versionName = versionName,
            versionCode = preferences.getLong(KEY_VERSION_CODE, -1L).takeIf { it > 0 },
            assetName = preferences.getString(KEY_ASSET_NAME, null) ?: fileName,
            downloadUrl = downloadUrl,
            sizeBytes = sizeBytes,
            sha256 = preferences.getString(KEY_SHA256, null),
            releaseNotes = preferences.getString(KEY_RELEASE_NOTES, null),
        )
        return PendingDownload(id, release, fileName)
    }

    private fun forgetDownload(pending: PendingDownload?) {
        val id = pending?.id ?: preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0) downloadManager.remove(id)
        preferences.edit { clear() }
        stopPolling()
    }

    private fun pruneUpdateDirectory(keep: String?) {
        val directory = updateDirectory()
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".apk") && it.name != keep }
            .forEach { it.delete() }
    }

    private fun updateDirectory(): File = File(appContext.getExternalFilesDir(null), UPDATE_DIRECTORY)

    private fun releaseFile(pending: PendingDownload): File = File(updateDirectory(), pending.fileName)

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.update.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.clipData = ClipData.newUri(appContext.contentResolver, file.name, uri)
        appContext.startActivity(intent)
    }

    /** The APK must carry the same package name and signer as the installed app. */
    private fun apkMatchesInstalledApp(file: File): Boolean {
        val archive = archivePackageInfo(file) ?: return false
        if (archive.packageName != appContext.packageName) return false
        val archiveSigners = signaturesOf(archive).map { sha256(it.toByteArray()) }.toSet()
        val installedSigners = installedSignerDigests()
        return archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() && archiveSigners == installedSigners
    }

    private fun installedSignerDigests(): Set<String> =
        signaturesOf(installedPackageInfo()).map { sha256(it.toByteArray()) }.toSet()

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        } else {
            packageManager.getPackageInfo(appContext.packageName, signingFlags())
        }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        } else {
            packageManager.getPackageArchiveInfo(file.path, signingFlags())
        }

    @Suppress("DEPRECATION")
    private fun signaturesOf(info: PackageInfo): List<Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty().toList()
        } else {
            info.signatures.orEmpty().toList()
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
