package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.delay
import java.awt.Canvas
import java.awt.Color
import java.awt.KeyEventDispatcher
import java.awt.event.*
import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun ComposeMpvPlayer(
    link: ExtractorLink,
    title: String?,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    startPositionMs: Long,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onFullscreenToggle: (Boolean) -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    var mpvHandle by remember { mutableStateOf<com.sun.jna.Pointer?>(null) }
    var hasEverPlayed by remember { mutableStateOf(false) }
    var lastFullscreenState by remember { mutableStateOf(false) }
    var lastSpeed by remember { mutableStateOf<String?>(null) }
    val isDisposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val isDestroyed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val handleLock = remember { Any() }

    val destroyHandle = remember(handleLock, isDisposed, isDestroyed) {
        {
            isDisposed.set(true)
            val h = mpvHandle
            mpvHandle = null
            if (h != null && !isDestroyed.getAndSet(true)) {
                mpvCommandExecutor.execute {
                    synchronized(handleLock) {
                        try {
                            MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            destroyHandle()
        }
    }

    LaunchedEffect(mpvHandle) {
        val h = mpvHandle ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            while (isActive && !isDisposed.get()) {
                // Persist playback speed whenever the user changes it (e.g. via mpv's [ ] keys)
                val speedStr = synchronized(handleLock) {
                    if (!isDisposed.get()) MpvLibrary.INSTANCE.getProperty(h, "speed") else null
                }
                if (speedStr != null && speedStr != lastSpeed) {
                    lastSpeed = speedStr
                    com.lagradost.common.storage.DesktopDataStore.setKey(PlayerConfig.PREF_SPEED, speedStr)
                }

                if (isDisposed.get()) break

                // Check if playback has started and track position
                val posStr = synchronized(handleLock) {
                    if (!isDisposed.get()) MpvLibrary.INSTANCE.getProperty(h, "time-pos") else null
                }
                val pos = posStr?.toDoubleOrNull()

                val durStr = synchronized(handleLock) {
                    if (!isDisposed.get()) MpvLibrary.INSTANCE.getProperty(h, "duration") else null
                }
                val dur = durStr?.toDoubleOrNull()

                if (pos != null && pos > 0.0) {
                    if (!hasEverPlayed) {
                        hasEverPlayed = true
                        withContext(Dispatchers.Main) {
                            onPlaybackReady()
                        }
                    }
                    if (dur != null && dur > 0.0) {
                        withContext(Dispatchers.Main) {
                            onPositionChange((pos * 1000).toLong(), (dur * 1000).toLong())
                        }
                    }
                }

                if (isDisposed.get()) break

                // Check fullscreen state
                val fsStr = synchronized(handleLock) {
                    if (!isDisposed.get()) MpvLibrary.INSTANCE.getProperty(h, "fullscreen") else null
                }
                val isFs = fsStr == "yes"
                if (isFs != lastFullscreenState) {
                    lastFullscreenState = isFs
                    withContext(Dispatchers.Main) {
                        onFullscreenToggle(isFs)
                    }
                }

                if (isDisposed.get()) break

                // Check for completion
                val eofStr = synchronized(handleLock) {
                    if (!isDisposed.get()) MpvLibrary.INSTANCE.getProperty(h, "eof-reached") else null
                }
                if (eofStr == "yes") {
                    withContext(Dispatchers.Main) {
                        if (hasEverPlayed) {
                            onFinished()
                        } else {
                            onPlaybackError("Stream failed to load or instantly ended.")
                        }
                    }
                    break
                }

                // Check timeout. Default to 45s to allow Playwright enough time to bypass Cloudflare.
                // Enforce minimum 45s even if user set it lower in settings, otherwise Cloudflare bypass will always fail.
                val timeoutStr = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT)
                val userTimeout = timeoutStr?.toLongOrNull() ?: 45000L
                val timeoutMs = maxOf(userTimeout, 45000L)
                if (!hasEverPlayed && System.currentTimeMillis() - startTime > timeoutMs) {
                    com.lagradost.common.logging.AppLogger.e("MPV timeout reached while buffering")
                    withContext(Dispatchers.Main) {
                        onPlaybackError("Connection timed out. The stream might be dead.")
                    }
                    break
                }

                delay(200)
            }
        }
    }

    val videoCanvas = remember {
        object : Canvas() {
            private var activeKeyDispatcher: KeyEventDispatcher? = null
            private var tempSubDir: File? = null

            override fun addNotify() {
                super.addNotify()

                if (mpvHandle != null || isDisposed.get()) return // Prevent multiple initializations (multi-audio bug)

                // Find MPV directory and tell JNA where to find the DLL if bundled
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val mpvExe = resolveMpvExecutable(isWindows)
                val mpvDir = mpvExe?.parentFile
                if (mpvDir != null) {
                    System.setProperty("jna.library.path", mpvDir.absolutePath)
                }

                val lib: MpvLibrary
                try {
                    lib = MpvLibrary.INSTANCE
                } catch (e: Exception) {
                    com.lagradost.common.logging.AppLogger.e("Failed to load MPV library", e)
                    onPlaybackError("MPV library not found. Please install libmpv (e.g. sudo apt install libmpv2 or pacman -S mpv).")
                    return
                }

                val handle = lib.mpv_create() ?: run {
                    onPlaybackError("Failed to initialize MPV Engine.")
                    return
                }
                if (isDisposed.get()) {
                    lib.mpv_terminate_destroy(handle)
                    return
                }
                mpvHandle = handle

                lib.mpv_set_option_string(handle, "osc", "yes")
                lib.mpv_set_option_string(handle, "vo", "gpu,x11")

                val isLinux = !isWindows && System.getProperty("os.name").lowercase().let { it.contains("nix") || it.contains("nux") }
                if (isLinux) {
                    // Use robust OpenGL/EGL backend on Linux to avoid experimental Vulkan swapchain stalls on seek
                    lib.mpv_set_option_string(handle, "gpu-api", "opengl,auto")
                    lib.mpv_set_option_string(handle, "gpu-context", "x11egl,x11,auto")
                }

                // Apply User Settings & Logging
                PlayerConfig.applyMpvSettings(handle, lib)

                val userConfigDir = resolveUserMpvConfigDir(mpvDir)
                if (userConfigDir != null && userConfigDir.isDirectory) {
                    val configDirStr = userConfigDir.absolutePath.replace("\\", "/")
                    lib.mpv_set_option_string(handle, "config-dir", configDirStr)
                    lib.mpv_set_option_string(handle, "config", "yes")
                    lib.mpv_set_option_string(handle, "load-scripts", "yes")
                    val fontsDir = File(userConfigDir, "fonts")
                    if (fontsDir.isDirectory) {
                        val fontsDirStr = fontsDir.absolutePath.replace("\\", "/")
                        lib.mpv_set_option_string(handle, "osd-fonts-dir", fontsDirStr)
                        lib.mpv_set_option_string(handle, "sub-fonts-dir", fontsDirStr)
                    }
                    com.lagradost.common.logging.AppLogger.i("Loaded MPV user config from: $configDirStr")
                } else {
                    lib.mpv_set_option_string(handle, "config", "yes")
                    lib.mpv_set_option_string(handle, "load-scripts", "yes")
                }

                val wid = com.sun.jna.Native.getComponentID(this)
                lib.mpv_set_option_string(handle, "wid", wid.toString())

                lib.mpv_set_option_string(handle, "input-default-bindings", "yes")
                lib.mpv_set_option_string(handle, "input-vo-keyboard", "yes")
                lib.mpv_set_option_string(handle, "save-position-on-quit", "no")
                lib.mpv_set_option_string(handle, "resume-playback", "no")
                lib.mpv_set_option_string(handle, "keep-open", "yes")
                lib.mpv_set_option_string(handle, "tls-verify", "no")
                lib.mpv_set_option_string(handle, "ytdl", "no")
                lib.mpv_set_option_string(handle, "idle", "yes")

                // Network reliability optimizations
                val validated = PlayerLinkHandler.validate(link, title).getOrElse {
                    onPlaybackError(it.message ?: "Validation failed")
                    return
                }

                when (validated.streamKind) {
                    PlayerLinkHandler.StreamKind.HLS -> {
                        lib.mpv_set_option_string(handle, "hls-bitrate", "max")
                    }
                    PlayerLinkHandler.StreamKind.DASH -> {}
                    else -> {}
                }

                val startSec = startPositionMs / 1000L
                if (startSec > 0) {
                    lib.mpv_set_option_string(handle, "start", startSec.toString())
                }

                if (validated.displayTitle.isNotBlank()) {
                    lib.mpv_set_option_string(handle, "force-media-title", validated.displayTitle)
                    lib.mpv_set_option_string(handle, "title", validated.displayTitle)
                }

                // Rewrite subtitles if using proxy
                val sessionId = validated.proxySessionId
                val finalSubtitles = if (sessionId != null) {
                    subtitles.map {
                        it.copy(url = com.lagradost.player.impl.proxy.LocalStreamProxy.buildProxyUrl(sessionId, it.url))
                    }
                } else {
                    subtitles
                }

                // Headers & Config (We explicitly pass emptyList for subtitles to prevent blocking)
                val mpvConfig = PlayerLinkHandler.writeMpvConfig(validated.headers, emptyList(), validated.audioTracks, validated.streamKind)
                lib.mpv_set_option_string(handle, "include", mpvConfig.absolutePath.replace("\\", "/"))

                val headerArgs = PlayerLinkHandler.buildHeadersCliArg(validated.headers)
                headerArgs.forEach { arg ->
                    val split = arg.removePrefix("--").split("=", limit = 2)
                    if (split.size == 2) {
                        MpvLibrary.INSTANCE.mpv_set_option_string(handle, split[0], split[1])
                    }
                }

                lib.mpv_set_option_string(handle, "script-opts", "osc-seekbarkeyframes=yes")
                com.lagradost.common.logging.AppLogger.i("Initializing embedded MPV for URL: ${validated.url}")
                lib.mpv_initialize(handle)

                val urlTarget = if (validated.useUrlFile) {
                    PlayerLinkHandler.writeUrlListFile("cloudstream_mpv_url_", validated.displayTitle, validated.url).absolutePath
                } else {
                    validated.url
                }

                val safeUrl = urlTarget.replace("\\", "/")
                sendMpvCommand(handle, "loadfile \"$safeUrl\"", isDisposed, handleLock)

                // Pre-download subtitles to local disk asynchronously so MPV reads local files
                // instead of opening 15+ competing HTTP subtitle demuxers that stall seeking.
                kotlin.concurrent.thread(isDaemon = true, name = "cs-subtitle-downloader") {
                    val subDir = File(System.getProperty("java.io.tmpdir"), "cs_subs_${System.currentTimeMillis()}").apply { mkdirs() }
                    tempSubDir = subDir
                    subDir.deleteOnExit()

                    for ((index, sub) in finalSubtitles.withIndex()) {
                        if (isDisposed.get()) break
                        try {
                            val localFile = if (sub.url.startsWith("http://") || sub.url.startsWith("https://")) {
                                val ext = when {
                                    sub.url.contains(".vtt", ignoreCase = true) -> ".vtt"
                                    sub.url.contains(".ass", ignoreCase = true) -> ".ass"
                                    else -> ".srt"
                                }
                                val dest = File(subDir, "sub_${index}$ext")
                                val req = okhttp3.Request.Builder().url(sub.url).build()
                                val resp = com.lagradost.cloudstream3.app.baseClient.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    val bytes = resp.body?.bytes()
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        dest.writeBytes(bytes)
                                        dest
                                    } else null
                                } else null
                            } else {
                                File(sub.url).takeIf { it.exists() }
                            }

                            if (isDisposed.get()) break

                            if (localFile != null && localFile.exists()) {
                                val safePath = localFile.absolutePath.replace("\\", "/")
                                val escapedTitle = sub.lang.replace("\\", "\\\\").replace("\"", "\\\"")
                                sendMpvCommand(handle, "sub-add \"$safePath\" auto \"$escapedTitle\"", isDisposed, handleLock)
                            }
                        } catch (_: Throwable) {}
                    }
                }

                // Setup Mouse and Keyboard interactions
                val canvas = this
                canvas.addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseMoved(e: MouseEvent) {
                        sendMpvCommand(mpvHandle, "mouse ${e.x} ${e.y}", isDisposed, handleLock)
                    }
                    override fun mouseDragged(e: MouseEvent) {
                        sendMpvCommand(mpvHandle, "mouse ${e.x} ${e.y}", isDisposed, handleLock)
                    }
                })

                canvas.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        canvas.requestFocusInWindow()
                        val h = mpvHandle ?: return
                        if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
                            val inOscArea = e.y > canvas.height - 130 || e.y < 60
                            if (!inOscArea) {
                                sendMpvCommand(h, "cycle fullscreen", isDisposed, handleLock)
                                return
                            }
                        }
                        sendMpvCommand(h, "mouse ${e.x} ${e.y}", isDisposed, handleLock)
                        val btn = when (e.button) {
                            MouseEvent.BUTTON1 -> "MBTN_LEFT"
                            MouseEvent.BUTTON2 -> "MBTN_MID"
                            MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                            else -> return
                        }
                        sendMpvCommand(h, "keydown $btn", isDisposed, handleLock)
                    }
                    override fun mouseReleased(e: MouseEvent) {
                        val h = mpvHandle ?: return
                        val btn = when (e.button) {
                            MouseEvent.BUTTON1 -> "MBTN_LEFT"
                            MouseEvent.BUTTON2 -> "MBTN_MID"
                            MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                            else -> return
                        }
                        sendMpvCommand(h, "keyup $btn", isDisposed, handleLock)
                    }
                })

                canvas.addMouseWheelListener { e ->
                    val h = mpvHandle ?: return@addMouseWheelListener
                    val key = if (e.wheelRotation < 0) "WHEEL_UP" else "WHEEL_DOWN"
                    sendMpvCommand(h, "keypress $key", isDisposed, handleLock)
                }

                val keyDispatcher = KeyEventDispatcher { e ->
                    val h = mpvHandle
                    if (h != null) {
                        if (e.id == KeyEvent.KEY_PRESSED) {
                            val mpvKey = awtKeyToMpv(e)
                            if (mpvKey?.contains("QUIT_OVERRIDE") == true) {
                                onCloseRequest()
                            } else if (mpvKey == "ENTER") {
                                sendMpvCommand(h, "cycle fullscreen", isDisposed, handleLock)
                            } else if (mpvKey != null) {
                                sendMpvCommand(h, "keydown $mpvKey", isDisposed, handleLock)
                            }
                        } else if (e.id == KeyEvent.KEY_RELEASED) {
                            val mpvKey = awtKeyToMpv(e)
                            if (mpvKey != null && !mpvKey.contains("QUIT_OVERRIDE") && mpvKey != "ENTER") {
                                sendMpvCommand(h, "keyup $mpvKey", isDisposed, handleLock)
                            }
                        }
                    }
                    false
                }
                activeKeyDispatcher = keyDispatcher
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)

                canvas.requestFocusInWindow()
            }

            override fun removeNotify() {
                activeKeyDispatcher?.let {
                    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it)
                }
                activeKeyDispatcher = null
                destroyHandle()
                try {
                    tempSubDir?.deleteRecursively()
                } catch (_: Throwable) {}
                super.removeNotify()
            }
        }.apply {
            background = Color.BLACK
            isFocusable = true
        }
    }

    SwingPanel(
        background = androidx.compose.ui.graphics.Color.Black,
        factory = { videoCanvas },
        modifier = modifier,
    )
}

private val mpvCommandExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "mpv-command-dispatcher").apply { isDaemon = true }
}

private fun sendMpvCommand(
    handle: com.sun.jna.Pointer?,
    cmd: String,
    isDisposed: java.util.concurrent.atomic.AtomicBoolean? = null,
    handleLock: Any? = null,
) {
    if (handle == null || isDisposed?.get() == true) return
    mpvCommandExecutor.execute {
        if (isDisposed?.get() == true) return@execute
        if (handleLock != null) {
            synchronized(handleLock) {
                if (isDisposed?.get() == true) return@synchronized
                try {
                    MpvLibrary.INSTANCE.mpv_command_string(handle, cmd)
                } catch (_: Throwable) {}
            }
        } else {
            try {
                MpvLibrary.INSTANCE.mpv_command_string(handle, cmd)
            } catch (_: Throwable) {}
        }
    }
}

private fun resolveMpvExecutable(isWindows: Boolean): File? {
    val names = if (isWindows) listOf("libmpv-2.dll") else listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so", "libmpv.dylib")

    val resDir = System.getProperty("compose.application.resources.dir")

    val candidates = listOfNotNull(
        resDir?.let { File(it, "mpv") },
        File("mpv"),
        File("2_cloudstream_desktop/mpv"),
        File("desktop-app/mpv"),
        File("desktop-app/appResources/mpv"),
    )
    for (base in candidates) {
        for (name in names) {
            val f = File(base, name)
            if (f.isFile) return f.absoluteFile
        }
    }
    return null
}

private fun awtKeyToMpv(e: KeyEvent): String? {
    if (e.isShiftDown) {
        when (e.keyCode) {
            KeyEvent.VK_3 -> return "#"
            KeyEvent.VK_1 -> return "!"
            KeyEvent.VK_2 -> return "@"
            KeyEvent.VK_4 -> return "$"
            KeyEvent.VK_5 -> return "%"
            KeyEvent.VK_6 -> return "^"
            KeyEvent.VK_7 -> return "&"
            KeyEvent.VK_8 -> return "*"
            KeyEvent.VK_9 -> return "("
            KeyEvent.VK_0 -> return ")"
            KeyEvent.VK_OPEN_BRACKET -> return "{"
            KeyEvent.VK_CLOSE_BRACKET -> return "}"
            KeyEvent.VK_COMMA -> return "<"
            KeyEvent.VK_PERIOD -> return ">"
            KeyEvent.VK_MINUS -> return "_"
            KeyEvent.VK_EQUALS -> return "+"
            KeyEvent.VK_Q -> return "QUIT_OVERRIDE"
            in KeyEvent.VK_A..KeyEvent.VK_Z -> {
                val letter = KeyEvent.getKeyText(e.keyCode).uppercase()
                val ctrl = if (e.isControlDown) "Ctrl+" else ""
                val alt = if (e.isAltDown) "Alt+" else ""
                return "$ctrl$alt$letter"
            }
        }
    }

    val baseKey = when (e.keyCode) {
        KeyEvent.VK_SPACE -> "SPACE"
        KeyEvent.VK_LEFT -> "LEFT"
        KeyEvent.VK_RIGHT -> "RIGHT"
        KeyEvent.VK_UP -> "UP"
        KeyEvent.VK_DOWN -> "DOWN"
        KeyEvent.VK_ENTER -> "ENTER"
        KeyEvent.VK_ESCAPE -> "ESC"
        KeyEvent.VK_BACK_SPACE -> "BS"
        KeyEvent.VK_DELETE -> "DEL"
        KeyEvent.VK_TAB -> "TAB"
        KeyEvent.VK_PAGE_UP -> "PGUP"
        KeyEvent.VK_PAGE_DOWN -> "PGDWN"
        KeyEvent.VK_HOME -> "HOME"
        KeyEvent.VK_END -> "END"

        KeyEvent.VK_Q -> "QUIT_OVERRIDE"

        in KeyEvent.VK_A..KeyEvent.VK_Z -> KeyEvent.getKeyText(e.keyCode).lowercase()
        in KeyEvent.VK_0..KeyEvent.VK_9 -> KeyEvent.getKeyText(e.keyCode)

        KeyEvent.VK_COMMA -> ","
        KeyEvent.VK_PERIOD -> "."
        KeyEvent.VK_SLASH, KeyEvent.VK_DIVIDE -> "/"
        KeyEvent.VK_MULTIPLY -> "*"
        KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> "-"
        KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> "+"
        KeyEvent.VK_OPEN_BRACKET -> "["
        KeyEvent.VK_CLOSE_BRACKET -> "]"
        KeyEvent.VK_BACK_SLASH -> "\\"
        KeyEvent.VK_SEMICOLON -> ";"
        KeyEvent.VK_QUOTE -> "'"

        else -> return null
    }

    val alt = if (e.isAltDown) "Alt+" else ""
    val ctrl = if (e.isControlDown) "Ctrl+" else ""
    val shift = if (e.isShiftDown && e.keyCode !in KeyEvent.VK_A..KeyEvent.VK_Z && baseKey.length > 1) "Shift+" else ""

    return "$ctrl$alt$shift$baseKey"
}

private fun resolveUserMpvConfigDir(mpvDir: File?): File? {
    if (mpvDir != null) {
        val portableConfig = File(mpvDir, "portable_config")
        if (portableConfig.isDirectory) return portableConfig
    }

    val xdgConfig = System.getenv("XDG_CONFIG_HOME")
    val homeDir = System.getProperty("user.home")

    val candidates = listOfNotNull(
        xdgConfig?.let { File(it, "mpv") },
        homeDir?.let { File(it, ".config/mpv") },
        homeDir?.let { File(it, ".mpv") },
        System.getenv("APPDATA")?.let { File(it, "mpv") },
    )

    return candidates.firstOrNull { it.isDirectory }
}
