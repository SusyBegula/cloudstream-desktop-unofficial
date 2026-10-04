package com.lagradost.cloudstream3.desktop.updater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

object DesktopAppUpdater {

    const val CURRENT_VERSION = "0.1.0"
    private const val GITHUB_REPO = "SusyBegula/cloudstream-desktop-unofficial"
    const val PREF_AUTO_CHECK_UPDATES = "pref_auto_check_updates"
    const val PREF_IGNORED_UPDATE_TAG = "pref_ignored_update_tag"

    private val jsonMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog = _showDialog.asStateFlow()

    private val _showBanner = MutableStateFlow(false)
    val showBanner = _showBanner.asStateFlow()

    val isAutoCheckEnabled: Boolean
        get() = DesktopDataStore.getKey<Boolean>(PREF_AUTO_CHECK_UPDATES) ?: true

    fun setAutoCheckEnabled(enabled: Boolean) {
        DesktopDataStore.setKey(PREF_AUTO_CHECK_UPDATES, enabled)
    }

    fun dismissDialog() {
        _showDialog.value = false
    }

    fun openDialog() {
        _showDialog.value = true
        _showBanner.value = false
    }

    fun dismissBanner() {
        _showBanner.value = false
    }

    fun ignoreCurrentUpdate(tagName: String) {
        DesktopDataStore.setKey(PREF_IGNORED_UPDATE_TAG, tagName)
        dismissDialog()
        dismissBanner()
    }

    /**
     * Checks if remote version is newer than current version using semantic versioning.
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String = CURRENT_VERSION): Boolean {
        val remoteParts = parseVersionNumbers(remoteTag)
        val currentParts = parseVersionNumbers(currentVersion)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun parseVersionNumbers(tag: String): List<Int> {
        val clean = tag.trim().removePrefix("v").substringBefore("-")
        return clean.split(".").mapNotNull { it.toIntOrNull() }
    }

    /**
     * Finds the best installer asset for the user's operating system.
     */
    fun findMatchingAsset(release: GitHubRelease): GitHubReleaseAsset? {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        return if (os.contains("win")) {
            release.assets.find { it.name.endsWith(".msi", ignoreCase = true) }
                ?: release.assets.find { it.name.endsWith(".exe", ignoreCase = true) }
        } else if (os.contains("linux")) {
            release.assets.find { it.name.endsWith(".AppImage", ignoreCase = true) }
                ?: release.assets.find { it.name.endsWith(".deb", ignoreCase = true) }
        } else {
            release.assets.firstOrNull()
        }
    }

    fun checkForUpdate(isAutomatic: Boolean = false, scope: CoroutineScope) {
        if (isAutomatic && !isAutoCheckEnabled) return

        scope.launch(Dispatchers.IO) {
            try {
                if (!isAutomatic) {
                    _uiState.value = UpdateUiState.Checking
                    _showDialog.value = true
                }

                val request = Request.Builder()
                    .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "CloudStream-Desktop/$CURRENT_VERSION")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val msg = "GitHub API error: ${response.code} ${response.message}"
                        AppLogger.w("DesktopAppUpdater: $msg")
                        if (!isAutomatic) {
                            _uiState.value = UpdateUiState.Error(msg)
                        }
                        return@use
                    }

                    val body = response.body.string()
                    if (body.isNullOrBlank()) {
                        if (!isAutomatic) {
                            _uiState.value = UpdateUiState.Error("Empty response from GitHub")
                        }
                        return@use
                    }

                    val release = jsonMapper.readValue(body, GitHubRelease::class.java)
                    val isNewer = isNewerVersion(release.tagName, CURRENT_VERSION)

                    if (isNewer) {
                        val ignoredTag = DesktopDataStore.getKey<String>(PREF_IGNORED_UPDATE_TAG)
                        if (isAutomatic && ignoredTag == release.tagName) {
                            // User previously chose to ignore this version automatically
                            return@use
                        }

                        val asset = findMatchingAsset(release)
                        _uiState.value = UpdateUiState.UpdateAvailable(release, asset, isAutomatic)
                        if (isAutomatic) {
                            _showBanner.value = true
                        } else {
                            _showDialog.value = true
                        }
                    } else {
                        if (!isAutomatic) {
                            _uiState.value = UpdateUiState.UpToDate(CURRENT_VERSION)
                            _showDialog.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("DesktopAppUpdater: Check failed", e)
                if (!isAutomatic) {
                    _uiState.value = UpdateUiState.Error(e.message ?: "Failed to check for updates")
                    _showDialog.value = true
                }
            }
        }
    }

    fun startDownload(
        release: GitHubRelease,
        asset: GitHubReleaseAsset,
        scope: CoroutineScope,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                _uiState.value = UpdateUiState.Downloading(
                    release = release,
                    asset = asset,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = asset.size,
                )
                _showDialog.value = true

                val request = Request.Builder()
                    .url(asset.downloadUrl)
                    .header("User-Agent", "CloudStream-Desktop/$CURRENT_VERSION")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _uiState.value = UpdateUiState.Error("Download failed with HTTP ${response.code}")
                        return@use
                    }

                    val body = response.body
                    val totalBytes = if (asset.size > 0) asset.size else body.contentLength()

                    val tempDir = File(System.getProperty("java.io.tmpdir"), "cloudstream_updates")
                    if (!tempDir.exists()) tempDir.mkdirs()

                    val targetFile = File(tempDir, asset.name)
                    if (targetFile.exists()) targetFile.delete()

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            var downloaded = 0L
                            var lastReportTime = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 200 || downloaded == totalBytes) {
                                    lastReportTime = now
                                    val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                                    _uiState.value = UpdateUiState.Downloading(
                                        release = release,
                                        asset = asset,
                                        progress = progress.coerceIn(0f, 1f),
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    val isAppImage = asset.name.endsWith(".AppImage", ignoreCase = true)
                    if (isAppImage) {
                        targetFile.setExecutable(true, false)
                    }

                    _uiState.value = UpdateUiState.ReadyToInstall(
                        installerFile = targetFile,
                        release = release,
                        isAppImage = isAppImage,
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("DesktopAppUpdater: Download failed", e)
                _uiState.value = UpdateUiState.Error(e.message ?: "Download failed")
            }
        }
    }

    suspend fun applyUpdate(installerFile: File, isAppImage: Boolean) = withContext(Dispatchers.IO) {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        try {
            if (os.contains("linux")) {
                val runningAppImage = System.getenv("APPIMAGE")
                if (isAppImage && !runningAppImage.isNullOrBlank()) {
                    val currentAppImageFile = File(runningAppImage)
                    if (currentAppImageFile.exists() && currentAppImageFile.canWrite()) {
                        // Create update swap script
                        val scriptFile = File.createTempFile("cs_updater_", ".sh")
                        scriptFile.writeText(
                            """
                            #!/bin/sh
                            sleep 1
                            mv -f "${installerFile.absolutePath}" "${currentAppImageFile.absolutePath}"
                            chmod +x "${currentAppImageFile.absolutePath}"
                            exec "${currentAppImageFile.absolutePath}" &
                            rm -f "$0"
                            """.trimIndent()
                        )
                        scriptFile.setExecutable(true, false)
                        ProcessBuilder("sh", scriptFile.absolutePath).start()
                        exitProcess(0)
                    }
                }

                // If not running as writable AppImage, launch the newly downloaded AppImage or installer directly
                if (isAppImage) {
                    installerFile.setExecutable(true, false)
                    ProcessBuilder(installerFile.absolutePath).start()
                    exitProcess(0)
                } else if (installerFile.name.endsWith(".deb", ignoreCase = true)) {
                    // Open with package manager
                    ProcessBuilder("xdg-open", installerFile.absolutePath).start()
                }
            } else if (os.contains("win")) {
                if (installerFile.name.endsWith(".msi", ignoreCase = true)) {
                    ProcessBuilder("msiexec.exe", "/i", installerFile.absolutePath).start()
                    exitProcess(0)
                } else if (installerFile.name.endsWith(".exe", ignoreCase = true)) {
                    ProcessBuilder(installerFile.absolutePath).start()
                    exitProcess(0)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("DesktopAppUpdater: Failed to launch installer", e)
            _uiState.value = UpdateUiState.Error("Failed to launch installer: ${e.message}")
        }
    }
}
