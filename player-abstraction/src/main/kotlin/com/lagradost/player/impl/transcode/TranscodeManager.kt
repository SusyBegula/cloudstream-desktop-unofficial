package com.lagradost.player.impl.transcode

import com.lagradost.common.logging.AppLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Runs ffmpeg transcodes for streams StreamProbe found to have a browser-incompatible track
 * (HEVC video, AC3/DTS audio, etc.), serving the resulting HLS output from the same embedded
 * Netty server LocalStreamProxy already runs, alongside its passthrough proxy routes.
 */
object TranscodeManager {
    private const val SEGMENT_WAIT_TIMEOUT_MS = 30_000L
    private const val SEGMENT_POLL_INTERVAL_MS = 250L
    private const val SESSION_IDLE_TIMEOUT_MS = 90_000L

    // Long enough for a hardware pipeline that can't actually handle this stream (wrong pixel
    // format, driver quirk, etc.) to crash-loop and exit, short enough not to stall /api/resolve.
    private const val HW_STARTUP_GRACE_MS = 1_500L

    // Best-effort head start before handing the playlist URL back, so hls.js doesn't immediately
    // request a segment ffmpeg hasn't produced yet and stall on the very first chunk.
    private const val INITIAL_BUFFER_SEGMENTS = 2
    private const val INITIAL_BUFFER_TIMEOUT_MS = 8_000L

    // Safety valve against runaway ffmpeg processes if a client re-resolves repeatedly — this
    // app is LAN/localhost-only single-user by design, not a multi-tenant transcode farm.
    private const val MAX_CONCURRENT_SESSIONS = 2

    private val baseDir = File(System.getProperty("java.io.tmpdir"), "cloudstream_transcode").apply {
        deleteRecursively()
        mkdirs()
    }

    private val sessions = ConcurrentHashMap<String, TranscodeSession>()

    init {
        thread(isDaemon = true, name = "transcode-reaper") {
            while (true) {
                try {
                    Thread.sleep(15_000)
                    reapIdleSessions()
                } catch (e: InterruptedException) {
                    return@thread
                } catch (e: Exception) {
                    AppLogger.w("Transcode reaper error", e)
                }
            }
        }
    }

    private fun reapIdleSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { (id, session) ->
            val shouldReap = now - session.lastAccessMs > SESSION_IDLE_TIMEOUT_MS
            if (shouldReap) {
                AppLogger.i("Reaping idle transcode session $id")
                session.destroy()
            }
            shouldReap
        }
    }

    private fun evictOldestIfAtCapacity() {
        if (sessions.size < MAX_CONCURRENT_SESSIONS) return
        val oldest = sessions.entries.minByOrNull { it.value.lastAccessMs } ?: return
        AppLogger.i("Evicting oldest transcode session ${oldest.key} to stay under capacity")
        sessions.remove(oldest.key)
        oldest.value.destroy()
    }

    /**
     * Starts a new ffmpeg transcode for [sourceUrl] (already routed through LocalStreamProxy,
     * so ffmpeg needs no auth headers of its own) and returns the HLS playlist URL to hand back
     * to the browser, or null if ffmpeg failed to start. Waits (briefly, best-effort) for a
     * small head start of segments before returning, so playback doesn't immediately stall.
     */
    suspend fun startSession(
        sourceUrl: String,
        plan: TranscodePlan,
        publicHost: String,
        port: Int,
        startOffsetSeconds: Double = 0.0,
    ): String? =
        withContext(Dispatchers.IO) {
            evictOldestIfAtCapacity()

            val sessionId = UUID.randomUUID().toString()
            val outputDir = File(baseDir, sessionId).apply { mkdirs() }

            val process = try {
                startFfmpeg(sourceUrl, plan, outputDir, startOffsetSeconds)
            } catch (e: Exception) {
                AppLogger.e("Failed to start ffmpeg transcode for $sourceUrl", e)
                outputDir.deleteRecursively()
                return@withContext null
            }

            val session = TranscodeSession(sessionId, outputDir, process)
            sessions[sessionId] = session

            // Drain ffmpeg's stderr so it never blocks on a full pipe buffer, surfacing real errors.
            thread(isDaemon = true, name = "ffmpeg-log-$sessionId") {
                try {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        if (line.contains("error", ignoreCase = true)) {
                            AppLogger.w("[ffmpeg $sessionId] $line")
                        }
                    }
                } catch (e: Exception) {
                    // Process ended / stream closed — nothing to do.
                }
            }

            AppLogger.i(
                "Started transcode session $sessionId for $sourceUrl " +
                    "(video=${plan.transcodeVideo}, audio=${plan.transcodeAudio}, startOffset=${startOffsetSeconds}s)",
            )

            awaitInitialBuffer(session)
            "http://$publicHost:$port/transcode/$sessionId/${TranscodeSession.PLAYLIST_NAME}"
        }

    /** Best-effort: returns as soon as a few segments exist, ffmpeg dies, or the timeout hits — whichever first. */
    private fun awaitInitialBuffer(session: TranscodeSession) {
        val deadline = System.currentTimeMillis() + INITIAL_BUFFER_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val segmentCount = session.outputDir.listFiles { f -> f.extension == "ts" }?.size ?: 0
            if (segmentCount >= INITIAL_BUFFER_SEGMENTS || !session.isAlive()) return
            Thread.sleep(SEGMENT_POLL_INTERVAL_MS)
        }
    }

    /**
     * Tries hardware encoders in [HardwareEncoder.detected]'s priority order first; if the
     * resulting ffmpeg process dies within [HW_STARTUP_GRACE_MS] (bad pixel format, driver
     * quirk, a specific stream the hw pipeline just can't handle), falls back to software
     * libx264 for this session rather than failing playback outright.
     */
    private fun startFfmpeg(sourceUrl: String, plan: TranscodePlan, outputDir: File, startOffsetSeconds: Double): Process {
        if (plan.transcodeVideo) {
            val accel = HardwareEncoder.detected
            if (accel != HwAccel.NONE) {
                val process = launchQuietly(buildCommand(sourceUrl, plan, outputDir, accel, startOffsetSeconds))
                if (process != null) {
                    Thread.sleep(HW_STARTUP_GRACE_MS)
                    if (process.isAlive) {
                        AppLogger.i("Transcoding with hardware encoder: $accel")
                        return process
                    }
                    AppLogger.w("$accel encode exited immediately (code ${process.exitValue()}), falling back to software")
                }
            }
        }
        return ProcessBuilder(buildCommand(sourceUrl, plan, outputDir, HwAccel.NONE, startOffsetSeconds))
            .redirectErrorStream(false).start()
    }

    private fun launchQuietly(command: List<String>): Process? = try {
        ProcessBuilder(command).redirectErrorStream(false).start()
    } catch (e: Exception) {
        AppLogger.w("Failed to launch ffmpeg: $command", e)
        null
    }

    private fun buildCommand(
        sourceUrl: String,
        plan: TranscodePlan,
        outputDir: File,
        accel: HwAccel,
        startOffsetSeconds: Double,
    ): List<String> {
        val videoCodecArgs = if (plan.transcodeVideo) HardwareEncoder.encodeArgs(accel) else listOf("-c:v", "copy")
        val decodeArgs = if (plan.transcodeVideo) HardwareEncoder.decodeArgs(accel) else emptyList()
        val audioCodecArgs = if (plan.transcodeAudio) {
            listOf("-c:a", "aac", "-b:a", "160k", "-ac", "2")
        } else {
            listOf("-c:a", "copy")
        }
        val mapArgs = buildList {
            add("-map"); add("0:${plan.videoStreamIndex}")
            plan.audioStreamIndex?.let { add("-map"); add("0:$it") }
        }

        return buildList {
            add("ffmpeg"); add("-y"); add("-loglevel"); add("warning")
            addAll(decodeArgs)
            // Input-side (-ss before -i) seeking: fast, keyframe-aligned demuxer seek — used when
            // the player scrubs past what's been transcoded so far, to restart from there instead
            // of forcing the whole file to be re-encoded from the beginning.
            if (startOffsetSeconds > 0) {
                add("-ss"); add(startOffsetSeconds.toString())
            }
            add("-i"); add(sourceUrl)
            addAll(mapArgs)
            addAll(videoCodecArgs)
            addAll(audioCodecArgs)
            add("-f"); add("hls")
            add("-hls_time"); add("6")
            add("-hls_playlist_type"); add("event")
            add("-hls_flags"); add("independent_segments")
            add("-hls_segment_filename"); add(File(outputDir, "seg_%05d.ts").absolutePath)
            add(File(outputDir, TranscodeSession.PLAYLIST_NAME).absolutePath)
        }
    }

    /** Waits for [file] to appear (ffmpeg writes it incrementally) or the session to die. */
    private suspend fun awaitFile(file: File, session: TranscodeSession): Boolean {
        val deadline = System.currentTimeMillis() + SEGMENT_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (file.exists() && file.length() > 0) return true
            if (!session.isAlive()) return file.exists() && file.length() > 0
            delay(SEGMENT_POLL_INTERVAL_MS)
        }
        return false
    }

    fun registerRoutes(route: Route) {
        route.get("/transcode/{sessionId}/{filename}") {
            val sessionId = call.parameters["sessionId"]
            val filename = call.parameters["filename"]
            val session = sessionId?.let { sessions[it] }

            if (sessionId == null || filename == null || session == null || filename.contains("..")) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            session.touch()

            val file = File(session.outputDir, filename)
            if (!awaitFile(file, session)) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val contentType = if (filename == TranscodeSession.PLAYLIST_NAME) {
                ContentType.parse("application/vnd.apple.mpegurl")
            } else {
                ContentType.parse("video/mp2t")
            }
            call.respondBytes(file.readBytes(), contentType)
        }
    }
}
