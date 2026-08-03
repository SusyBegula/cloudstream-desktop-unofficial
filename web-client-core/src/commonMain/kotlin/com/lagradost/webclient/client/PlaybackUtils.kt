package com.lagradost.webclient.client

private const val RESUME_RESET_PERCENT = 95L
private const val RESUME_MIN_PERCENT = 1L
private const val MIN_DURATION_TO_SAVE_MS = 30_000L

/** Web-client reimplementation of PlayerLinkHandler.isCompleted (JVM-only, from :player-abstraction). */
fun isHistoryCompleted(positionMs: Long, durationMs: Long): Boolean {
    if (positionMs <= 0 || durationMs <= 0) return false
    val percent = positionMs * 100 / durationMs
    return percent >= RESUME_RESET_PERCENT
}

/** Web-client reimplementation of PlayerLinkHandler.resumeStartSeconds, in milliseconds. */
fun resumeStartMs(positionMs: Long, durationMs: Long): Long {
    if (positionMs <= 0) return 0
    if (durationMs < MIN_DURATION_TO_SAVE_MS) return 0
    val percent = positionMs * 100 / durationMs
    if (percent >= RESUME_RESET_PERCENT) return 0
    if (percent <= RESUME_MIN_PERCENT) return 0
    return positionMs.coerceAtLeast(0)
}
