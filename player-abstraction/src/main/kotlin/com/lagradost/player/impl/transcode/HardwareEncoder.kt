package com.lagradost.player.impl.transcode

import com.lagradost.common.logging.AppLogger
import java.io.File
import java.util.concurrent.TimeUnit

enum class HwAccel { VAAPI, NVENC, QSV, NONE }

/**
 * Picks which hardware H.264 encoder (if any) actually works on this machine, by running a
 * throwaway 1-frame test encode for each candidate in priority order. "Compiled into this ffmpeg
 * build" and "the driver/GPU on this box can actually use it" are different things (e.g. VAAPI
 * support can be compiled in with no /dev/dri device, or a broken/missing media driver behind
 * it) — we verify at runtime rather than assume, and fall back to software libx264 if none work.
 */
object HardwareEncoder {
    private const val VAAPI_DEVICE = "/dev/dri/renderD128"
    private const val TEST_TIMEOUT_SECONDS = 10L

    val detected: HwAccel by lazy { detect() }

    private fun detect(): HwAccel {
        val accel = when {
            File(VAAPI_DEVICE).exists() && testEncode(vaapiTestCommand()) -> HwAccel.VAAPI
            testEncode(nvencTestCommand()) -> HwAccel.NVENC
            testEncode(qsvTestCommand()) -> HwAccel.QSV
            else -> HwAccel.NONE
        }
        AppLogger.i("Transcode hardware encoder: $accel" + if (accel == HwAccel.NONE) " (using software libx264)" else "")
        return accel
    }

    private fun testEncode(command: List<String>): Boolean {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val finished = process.waitFor(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun vaapiTestCommand() = listOf(
        "ffmpeg", "-y", "-v", "error",
        "-init_hw_device", "vaapi=va:$VAAPI_DEVICE",
        "-f", "lavfi", "-i", "color=size=64x64:duration=0.1",
        "-vf", "format=nv12,hwupload",
        "-c:v", "h264_vaapi",
        "-f", "null", "-",
    )

    private fun nvencTestCommand() = listOf(
        "ffmpeg", "-y", "-v", "error",
        "-f", "lavfi", "-i", "color=size=64x64:duration=0.1",
        "-c:v", "h264_nvenc",
        "-f", "null", "-",
    )

    private fun qsvTestCommand() = listOf(
        "ffmpeg", "-y", "-v", "error",
        "-init_hw_device", "qsv=hw", "-filter_hw_device", "hw",
        "-f", "lavfi", "-i", "color=size=64x64:duration=0.1",
        "-vf", "format=nv12,hwupload=extra_hw_frames=16",
        "-c:v", "h264_qsv",
        "-f", "null", "-",
    )

    /** Input-side args (placed before -i) to decode on the same accelerator as [accel], avoiding a GPU->CPU->GPU round trip. */
    fun decodeArgs(accel: HwAccel): List<String> = when (accel) {
        HwAccel.VAAPI -> listOf("-hwaccel", "vaapi", "-hwaccel_output_format", "vaapi", "-hwaccel_device", VAAPI_DEVICE)
        HwAccel.NVENC -> listOf("-hwaccel", "cuda", "-hwaccel_output_format", "cuda")
        HwAccel.QSV -> listOf("-hwaccel", "qsv", "-hwaccel_output_format", "qsv")
        HwAccel.NONE -> emptyList()
    }

    /** Video encoder args for [accel]. Quality knobs (qp/cq/global_quality) are chosen to land near libx264's crf 21. */
    fun encodeArgs(accel: HwAccel): List<String> = when (accel) {
        HwAccel.VAAPI -> listOf("-c:v", "h264_vaapi", "-qp", "21")
        HwAccel.NVENC -> listOf("-c:v", "h264_nvenc", "-preset", "p4", "-cq", "21")
        HwAccel.QSV -> listOf("-c:v", "h264_qsv", "-global_quality", "21")
        HwAccel.NONE -> listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "21", "-pix_fmt", "yuv420p")
    }
}
