package com.lagradost.player.impl.transcode

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

data class ProbedStream(
    val index: Int,
    val codecType: String,
    val codecName: String?,
    val width: Int?,
    val height: Int?,
)

data class ProbeResult(
    val streams: List<ProbedStream>,
    val durationSeconds: Double,
) {
    val videoStreams get() = streams.filter { it.codecType == "video" }
    val audioStreams get() = streams.filter { it.codecType == "audio" }
}

/** What ffmpeg needs to do (if anything) to make a probed stream browser-playable. */
data class TranscodePlan(
    val videoStreamIndex: Int,
    val audioStreamIndex: Int?,
    val transcodeVideo: Boolean,
    val transcodeAudio: Boolean,
)

/**
 * Detects codecs a browser's <video>/MSE can't decode (HEVC, AC3/DTS, etc.) by shelling out to
 * ffprobe/ffmpeg, since Chromium ships no HEVC decoder at all on Linux (licensing) — the browser
 * plays the audio track fine but never decodes a video frame, giving sound with a black screen.
 */
object StreamProbe {
    private val BROWSER_SAFE_VIDEO_CODECS = setOf("h264", "vp8", "vp9", "av1")
    private val BROWSER_SAFE_AUDIO_CODECS = setOf("aac", "mp3", "opus", "vorbis", "flac")

    private const val PROBE_TIMEOUT_SECONDS = 20L

    val ffmpegAvailable: Boolean by lazy { isToolAvailable("ffmpeg") }
    val ffprobeAvailable: Boolean by lazy { isToolAvailable("ffprobe") }

    private fun isToolAvailable(name: String): Boolean {
        return try {
            val process = ProcessBuilder(name, "-version").redirectErrorStream(true).start()
            process.inputStream.readBytes()
            process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /** [url] should already be routed through LocalStreamProxy so ffprobe needs no auth headers. */
    suspend fun probe(url: String): ProbeResult? = withContext(Dispatchers.IO) {
        if (!ffprobeAvailable) return@withContext null
        try {
            val process = ProcessBuilder(
                "ffprobe", "-v", "error", "-print_format", "json",
                "-show_streams", "-show_format", url,
            ).redirectErrorStream(false).start()

            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext null
            }
            if (process.exitValue() != 0) return@withContext null

            parseProbeJson(output)
        } catch (e: Exception) {
            AppLogger.w("StreamProbe failed for $url", e)
            null
        }
    }

    private fun parseProbeJson(raw: String): ProbeResult? {
        val root = Json.parseToJsonElement(raw).jsonObject
        val streamsJson = root["streams"]?.jsonArray ?: return null
        val streams = streamsJson.mapNotNull { el ->
            val obj = el.jsonObject
            val type = obj["codec_type"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val index = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            ProbedStream(
                index = index,
                codecType = type,
                codecName = obj["codec_name"]?.jsonPrimitive?.content,
                width = obj["width"]?.jsonPrimitive?.content?.toIntOrNull(),
                height = obj["height"]?.jsonPrimitive?.content?.toIntOrNull(),
            )
        }
        val duration = root["format"]?.jsonObject?.get("duration")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        return ProbeResult(streams, duration)
    }

    private fun isVideoCodecSupported(codecName: String?) =
        codecName != null && codecName.lowercase() in BROWSER_SAFE_VIDEO_CODECS

    private fun isAudioCodecSupported(codecName: String?) =
        codecName != null && codecName.lowercase() in BROWSER_SAFE_AUDIO_CODECS

    private fun area(stream: ProbedStream) = (stream.width ?: 0) * (stream.height ?: 0)

    /** Null means every relevant track is already browser-compatible — no transcode needed. */
    fun planTranscode(probe: ProbeResult): TranscodePlan? {
        val compatibleVideo = probe.videoStreams.filter { isVideoCodecSupported(it.codecName) }.maxByOrNull(::area)
        val video = compatibleVideo ?: probe.videoStreams.maxByOrNull(::area) ?: return null

        val compatibleAudio = probe.audioStreams.firstOrNull { isAudioCodecSupported(it.codecName) }
        val audio = compatibleAudio ?: probe.audioStreams.firstOrNull()

        val needsVideo = compatibleVideo == null
        val needsAudio = audio != null && compatibleAudio == null
        if (!needsVideo && !needsAudio) return null

        return TranscodePlan(
            videoStreamIndex = video.index,
            audioStreamIndex = audio?.index,
            transcodeVideo = needsVideo,
            transcodeAudio = needsAudio,
        )
    }
}
