package com.lagradost.webclient.server.network

import com.lagradost.common.platform.PlatformPaths
import com.microsoft.playwright.Playwright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Server-side counterpart of desktop-app's PlaywrightManager. Kept as a separate copy
 * (rather than an extracted shared module) per the "leave desktop-app untouched" decision.
 */
object PlaywrightManager {

    private val _isInstalled = MutableStateFlow(false)
    val isInstalled = _isInstalled.asStateFlow()

    private val playwrightPath = File(PlatformPaths.appDataDir, "playwright_browsers")

    private val _isDownloaded = MutableStateFlow(false)

    private val _systemBrowser = MutableStateFlow<String?>(null)
    val systemBrowser = _systemBrowser.asStateFlow()

    init {
        checkInstalled()
    }

    private fun checkInstalled() {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val isMac = osName.contains("mac")

        val chromePaths = mutableListOf<File>()

        if (isWindows) {
            val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val progFiles86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            val localAppData = System.getenv("LOCALAPPDATA") ?: "C:\\Users\\Default\\AppData\\Local"

            chromePaths.add(File("$progFiles\\Google\\Chrome\\Application\\chrome.exe"))
            chromePaths.add(File("$progFiles86\\Google\\Chrome\\Application\\chrome.exe"))
            chromePaths.add(File("$localAppData\\Google\\Chrome\\Application\\chrome.exe"))
        } else if (isMac) {
            chromePaths.add(File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"))
        } else {
            // Linux
            chromePaths.add(File("/usr/bin/google-chrome-stable"))
            chromePaths.add(File("/usr/bin/google-chrome"))
            chromePaths.add(File("/usr/bin/chromium"))
            chromePaths.add(File("/usr/bin/chromium-freeworld"))
            chromePaths.add(File("/usr/bin/brave-browser"))
            chromePaths.add(File("/usr/bin/brave"))
        }

        _systemBrowser.value = chromePaths.firstOrNull { it.exists() }?.let { "chrome" }
        _isDownloaded.value = playwrightPath.exists() && (playwrightPath.listFiles()?.isNotEmpty() == true)
        _isInstalled.value = _systemBrowser.value != null || _isDownloaded.value
    }

    private fun getEnvOptions(): Playwright.CreateOptions {
        val env = mutableMapOf("PLAYWRIGHT_BROWSERS_PATH" to playwrightPath.absolutePath)
        if (_systemBrowser.value != null) {
            env["PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD"] = "1"
        }
        return Playwright.CreateOptions().setEnv(env)
    }

    private var playwright: Playwright? = null
    private var browser: com.microsoft.playwright.Browser? = null

    suspend fun getBrowser(): com.microsoft.playwright.Browser = withContext(Dispatchers.IO) {
        val b = browser
        if (b == null || !b.isConnected) {
            try {
                browser?.close()
            } catch (_: Exception) {
            }
            try {
                playwright?.close()
            } catch (_: Exception) {
            }

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
        } catch (_: Exception) {
        }
        try {
            playwright?.close()
        } catch (_: Exception) {
        }
        browser = null
        playwright = null
    }
}
