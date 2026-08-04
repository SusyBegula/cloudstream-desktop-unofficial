package com.lagradost.player.impl.transcode

import com.lagradost.common.logging.AppLogger
import java.io.File
import java.util.concurrent.TimeUnit

/** One running (or finished) ffmpeg HLS transcode, writing segments into [outputDir]. */
class TranscodeSession(
    val id: String,
    val outputDir: File,
    private val process: Process,
) {
    @Volatile
    var lastAccessMs: Long = System.currentTimeMillis()
        private set

    val playlistFile: File get() = File(outputDir, PLAYLIST_NAME)

    fun touch() {
        lastAccessMs = System.currentTimeMillis()
    }

    fun isAlive(): Boolean = process.isAlive

    fun destroy() {
        try {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Error stopping transcode session $id", e)
        }
        outputDir.deleteRecursively()
    }

    companion object {
        const val PLAYLIST_NAME = "playlist.m3u8"
    }
}
