package com.lagradost.cloudstream3.desktop.utils

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.lagradost.common.logging.AppLogger
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.concurrent.TimeUnit

object DesktopClipboard {

    private val isLinux = System.getProperty("os.name")?.lowercase()?.contains("linux") == true

    fun copyText(text: String, composeClipboard: ClipboardManager? = null): Boolean {
        if (text.isEmpty()) return false

        // 1. On Linux, native CLI tools (wl-copy on Wayland, xclip/xsel on X11)
        // are immune to the Java AWT X11 "BadWindow" crash in XWayland.
        if (isLinux) {
            if (copyViaLinuxCli(text)) {
                runCatching { composeClipboard?.setText(AnnotatedString(text)) }
                return true
            }
        }

        // 2. Try Compose Platform Clipboard Manager
        val composeSuccess = runCatching {
            composeClipboard?.setText(AnnotatedString(text))
            true
        }.getOrDefault(false)

        if (composeSuccess) return true

        // 3. Fallback to Java AWT Toolkit with null owner
        return runCatching {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            true
        }.onFailure { e ->
            AppLogger.e("Failed to copy text to system clipboard", e)
        }.getOrDefault(false)
    }

    private fun copyViaLinuxCli(text: String): Boolean {
        // Priority 1: wl-copy (Wayland native)
        if (System.getenv("WAYLAND_DISPLAY")?.isNotBlank() == true || File("/usr/bin/wl-copy").canExecute()) {
            if (executeCopyProcess(listOf("wl-copy"), text)) return true
        }

        // Priority 2: xclip (X11)
        if (File("/usr/bin/xclip").canExecute()) {
            if (executeCopyProcess(listOf("xclip", "-selection", "clipboard"), text)) return true
        }

        // Priority 3: xsel (X11)
        if (File("/usr/bin/xsel").canExecute()) {
            if (executeCopyProcess(listOf("xsel", "--clipboard", "--input"), text)) return true
        }

        return false
    }

    private fun executeCopyProcess(command: List<String>, text: String): Boolean {
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            process.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(text)
                it.flush()
            }

            val completed = process.waitFor(1, TimeUnit.SECONDS)
            completed && process.exitValue() == 0
        }.getOrDefault(false)
    }
}
