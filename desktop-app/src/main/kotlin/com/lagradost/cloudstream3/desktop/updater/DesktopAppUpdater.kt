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

    const val CURRENT_VERSION = "0.1.3"
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
                if (isAppImage) {
                    installAndRestartAppImage(installerFile)
                    return@withContext
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

    private fun installAndRestartAppImage(installerFile: File) {
        val userHome = File(System.getProperty("user.home"))
        val runningAppImage = System.getenv("APPIMAGE")

        // 1. Determine target installation path
        val defaultInstallDir = File(userHome, ".local/share/cloudstream-desktop").apply { mkdirs() }
        val targetAppImage: File = if (!runningAppImage.isNullOrBlank()) {
            val f = File(runningAppImage)
            val parent = f.parentFile
            if (parent != null && parent.canWrite()) f else File(defaultInstallDir, "CloudStream-Desktop.AppImage")
        } else {
            File(defaultInstallDir, "CloudStream-Desktop.AppImage")
        }

        // 2. Setup Linux desktop integration (Hyprland / rofi / wofi / desktop menu)
        setupLinuxDesktopIntegration(userHome, targetAppImage)

        // 3. Prepare background update and restart script
        val pid = ProcessHandle.current().pid()
        val scriptFile = File.createTempFile("cs_updater_", ".sh")
        scriptFile.writeText(
            """
            |#!/bin/sh
            |# Wait for running CloudStream process ($pid) to exit
            |while kill -0 $pid 2>/dev/null; do
            |    sleep 0.2
            |done
            |sleep 0.3
            |
            |# Atomically replace target AppImage
            |mkdir -p "${targetAppImage.parentFile?.absolutePath ?: userHome.absolutePath}"
            |cp -f "${installerFile.absolutePath}" "${targetAppImage.absolutePath}"
            |chmod +x "${targetAppImage.absolutePath}"
            |rm -f "${installerFile.absolutePath}"
            |
            |# Launch updated AppImage with Hyprland/Wayland tiling compatibility
            |export _JAVA_AWT_WM_NONREPARENTING=1
            |nohup "${targetAppImage.absolutePath}" >/dev/null 2>&1 &
            |rm -f "${'$'}0"
            """.trimMargin().trim() + "\n"
        )
        scriptFile.setExecutable(true, false)

        AppLogger.i("DesktopAppUpdater: Launching update script ${scriptFile.absolutePath} to install $targetAppImage and restart")
        ProcessBuilder("sh", scriptFile.absolutePath).start()
        exitProcess(0)
    }

    private fun setupLinuxDesktopIntegration(userHome: File, targetAppImage: File) {
        try {
            val binDir = File(userHome, ".local/bin").apply { mkdirs() }
            val appDir = File(userHome, ".local/share/applications").apply { mkdirs() }
            val iconDir = File(userHome, ".local/share/icons/hicolor/256x256/apps").apply { mkdirs() }
            val pixmapsDir = File(userHome, ".local/share/pixmaps").apply { mkdirs() }

            // 1. Launcher command in ~/.local/bin/cloudstream-desktop
            val launcher = File(binDir, "cloudstream-desktop")
            launcher.writeText(
                """
                |#!/usr/bin/env sh
                |export _JAVA_AWT_WM_NONREPARENTING=1
                |exec "${targetAppImage.absolutePath}" "$@"
                """.trimMargin().trim() + "\n"
            )
            launcher.setExecutable(true, false)

            // 2. Extract application icon from resources
            val iconFile = File(iconDir, "cloudstream-desktop.png")
            val pixmapFile = File(pixmapsDir, "cloudstream-desktop.png")
            DesktopAppUpdater::class.java.getResourceAsStream("/logo_ui.png")?.use { input ->
                val bytes = input.readBytes()
                iconFile.writeBytes(bytes)
                pixmapFile.writeBytes(bytes)
            }

            // 3. Desktop entry for Hyprland / rofi / wofi
            val desktopFile = File(appDir, "cloudstream-desktop.desktop")
            desktopFile.writeText(
                """
                |[Desktop Entry]
                |Name=CloudStream Desktop
                |GenericName=Media Streaming Player
                |Comment=Unofficial CloudStream Desktop Client
                |Exec=${launcher.absolutePath} %U
                |Icon=cloudstream-desktop
                |Terminal=false
                |Type=Application
                |Categories=AudioVideo;Video;Player;Network;
                |StartupWMClass=com.lagradost.cloudstream3.desktop.MainKt
                |Keywords=stream;streaming;cloudstream;movie;tv;anime;video;
                """.trimMargin().trim() + "\n"
            )

            // 4. Update desktop database so Hyprland app launchers immediately refresh
            try {
                ProcessBuilder("update-desktop-database", appDir.absolutePath).start()
            } catch (_: Throwable) {}
        } catch (e: Exception) {
            AppLogger.w("DesktopAppUpdater: Failed to complete desktop integration", e)
        }
    }
}
