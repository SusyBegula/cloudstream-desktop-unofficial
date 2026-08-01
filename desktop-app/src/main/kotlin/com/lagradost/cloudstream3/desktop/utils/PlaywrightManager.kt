package com.lagradost.cloudstream3.desktop.utils

import com.lagradost.common.platform.PlatformPaths
import com.microsoft.playwright.Playwright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

object PlaywrightManager {

    private val _isInstalled = MutableStateFlow(false)
    val isInstalled = _isInstalled.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _showPrompt = MutableStateFlow(false)
    val showPrompt = _showPrompt.asStateFlow()

    fun triggerPrompt() {
        _showPrompt.value = true
    }

    fun dismissPrompt() {
        _showPrompt.value = false
    }

    private val playwrightPath = File(PlatformPaths.appDataDir, "playwright_browsers")

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded = _isDownloaded.asStateFlow()

    private val _systemBrowser = MutableStateFlow<String?>(null)
    val systemBrowser = _systemBrowser.asStateFlow()

    init {
        checkInstalled()
    }

    private fun checkInstalled() {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val isMac = osName.contains("mac")

        val edgePaths = mutableListOf<File>()
        val chromePaths = mutableListOf<File>()

        if (isWindows) {
            val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val progFiles86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            val localAppData = System.getenv("LOCALAPPDATA") ?: "C:\\Users\\Default\\AppData\\Local"

            edgePaths.add(File("$progFiles86\\Microsoft\\Edge\\Application\\msedge.exe"))
            edgePaths.add(File("$progFiles\\Microsoft\\Edge\\Application\\msedge.exe"))

            chromePaths.add(File("$progFiles\\Google\\Chrome\\Application\\chrome.exe"))
            chromePaths.add(File("$progFiles86\\Google\\Chrome\\Application\\chrome.exe"))
            chromePaths.add(File("$localAppData\\Google\\Chrome\\Application\\chrome.exe"))
        } else if (isMac) {
            edgePaths.add(File("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"))
            chromePaths.add(File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"))
        } else {
            // Linux
            edgePaths.add(File("/usr/bin/microsoft-edge-stable"))
            edgePaths.add(File("/usr/bin/microsoft-edge"))
            chromePaths.add(File("/usr/bin/google-chrome-stable"))
            chromePaths.add(File("/usr/bin/google-chrome"))
            chromePaths.add(File("/usr/bin/chromium"))
            chromePaths.add(File("/usr/bin/chromium-freeworld"))
            chromePaths.add(File("/usr/bin/brave-browser"))
            chromePaths.add(File("/usr/bin/brave"))
            chromePaths.add(File("/usr/bin/vivaldi"))
        }

        when {
            edgePaths.any { it.exists() } -> _systemBrowser.value = "msedge"
            chromePaths.any { it.exists() } -> _systemBrowser.value = "chrome"
            else -> _systemBrowser.value = null
        }

        _isDownloaded.value = playwrightPath.exists() && (playwrightPath.listFiles()?.isNotEmpty() == true)
        _isInstalled.value = _systemBrowser.value != null || _isDownloaded.value
    }

    suspend fun downloadBrowser() = withContext(Dispatchers.IO) {
        if (_isInstalled.value) return@withContext

        _isDownloading.value = true
        try {
            // Tell Playwright where to download the browsers
            val env = mapOf("PLAYWRIGHT_BROWSERS_PATH" to playwrightPath.absolutePath)
            val options = Playwright.CreateOptions().setEnv(env)

            // This will block and download Chromium (and other bundled browsers).
            // It downloads them to the specified PLAYWRIGHT_BROWSERS_PATH.
            val playwright = Playwright.create(options)
            playwright.close()

            checkInstalled()
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("Playwright download or initialization failed", e)
        } finally {
            _isDownloading.value = false
        }
    }

    fun removeBrowser() {
        if (playwrightPath.exists()) {
            playwrightPath.deleteRecursively()
            checkInstalled()
        }
    }

    fun getEnvOptions(): Playwright.CreateOptions {
        val env = mutableMapOf("PLAYWRIGHT_BROWSERS_PATH" to playwrightPath.absolutePath)

        // Skip massive Playwright browser downloads if we already have Edge/Chrome installed
        if (_systemBrowser.value != null) {
            env["PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD"] = "1"
        }

        return Playwright.CreateOptions().setEnv(env)
    }

    var playwright: Playwright? = null
    var browser: com.microsoft.playwright.Browser? = null

    suspend fun getBrowser(): com.microsoft.playwright.Browser = withContext(Dispatchers.IO) {
        val b = browser
        if (b == null || !b.isConnected) {
            // Clean up old instances
            try {
                browser?.close()
            } catch (_: Exception) {}
            try {
                playwright?.close()
            } catch (_: Exception) {}

            playwright = Playwright.create(getEnvOptions())
            val launchOptions = com.microsoft.playwright.BrowserType.LaunchOptions()
                .setHeadless(true)
                .setIgnoreDefaultArgs(listOf("--enable-automation"))
                .setArgs(
                    listOf(
                        "--disable-blink-features=AutomationControlled",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                    ),
                )
            systemBrowser.value?.let { launchOptions.setChannel(it) }
            browser = playwright!!.chromium().launch(launchOptions)
        }
        return@withContext browser!!
    }

    fun resetBrowser() {
        try {
            browser?.close()
        } catch (_: Exception) {}
        try {
            playwright?.close()
        } catch (_: Exception) {}
        browser = null
        playwright = null
    }
}
