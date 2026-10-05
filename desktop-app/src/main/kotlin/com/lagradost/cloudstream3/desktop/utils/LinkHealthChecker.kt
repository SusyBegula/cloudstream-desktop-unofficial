package com.lagradost.cloudstream3.desktop.utils

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.common.logging.AppLogger
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class LinkHealthStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
}

data class LinkHealth(
    val status: LinkHealthStatus,
    val httpCode: Int? = null,
    val reason: String? = null,
    val latencyMs: Long = 0L,
)

object LinkHealthChecker {
    private val healthCache = ConcurrentHashMap<String, LinkHealth>()

    private val checkClient: OkHttpClient by lazy {
        app.baseClient.newBuilder()
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .callTimeout(5000, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getCached(url: String): LinkHealth? = healthCache[url]

    fun clearCache() {
        healthCache.clear()
    }

    suspend fun checkHealth(link: ExtractorLink): LinkHealth = withContext(Dispatchers.IO) {
        val cacheKey = link.url.trim()
        if (cacheKey.isNotBlank()) {
            healthCache[cacheKey]?.let { return@withContext it }
        }

        val result = performCheck(link)
        if (cacheKey.isNotBlank()) {
            healthCache[cacheKey] = result
        }
        result
    }

    private fun performCheck(link: ExtractorLink): LinkHealth {
        val targetUrl = when (link) {
            is ExtractorLinkPlayList -> link.playlist.firstOrNull()?.url ?: ""
            else -> link.url.trim()
        }

        if (targetUrl.isBlank()) {
            return LinkHealth(LinkHealthStatus.OFFLINE, reason = "Empty URL")
        }

        // Handle local files
        if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
            val file = File(targetUrl)
            return if (file.exists() && file.length() > 0) {
                LinkHealth(LinkHealthStatus.ONLINE, reason = "Local File")
            } else {
                LinkHealth(LinkHealthStatus.OFFLINE, reason = "File Not Found")
            }
        }

        val startTime = System.currentTimeMillis()
        try {
            val headers = PlayerLinkHandler.buildHeaderMap(link)
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .header("Range", "bytes=0-2048")

            headers.forEach { (key, value) ->
                if (!key.equals("Range", ignoreCase = true)) {
                    requestBuilder.header(key, value)
                }
            }

            val response = checkClient.newCall(requestBuilder.build()).execute()
            val elapsed = System.currentTimeMillis() - startTime
            response.use { resp ->
                val code = resp.code
                // 200 OK, 206 Partial Content, or 416 Range Not Satisfiable (server reached but doesn't like range)
                if (resp.isSuccessful || code == 416) {
                    val contentType = resp.header("Content-Type")?.lowercase() ?: ""
                    val body = resp.body
                    val bodySource = body.source()
                    val peekString = try {
                        if (bodySource.request(16)) {
                            bodySource.peek().readUtf8(minOf(1024L, bodySource.buffer.size))
                        } else {
                            ""
                        }
                    } catch (_: Exception) {
                        ""
                    }

                    val isM3u8 = peekString.startsWith("#EXTM3U") ||
                            targetUrl.contains(".m3u8", ignoreCase = true) ||
                            contentType.contains("mpegurl")

                    val isHtml = (contentType.contains("text/html") || peekString.trimStart().startsWith("<")) && !isM3u8

                    if (isHtml) {
                        // Check if it's a known error or removal page
                        val lower = peekString.lowercase()
                        val isRemovalPage = lower.contains("not found") ||
                                lower.contains("deleted") ||
                                lower.contains("removed") ||
                                lower.contains("cloudflare") ||
                                lower.contains("turnstile") ||
                                lower.contains("ddos-guard")

                        return LinkHealth(
                            status = LinkHealthStatus.OFFLINE,
                            httpCode = code,
                            reason = if (isRemovalPage) "Removed/Protected HTML" else "Webpage, not video",
                            latencyMs = elapsed,
                        )
                    }

                    return LinkHealth(
                        status = LinkHealthStatus.ONLINE,
                        httpCode = code,
                        reason = "OK",
                        latencyMs = elapsed,
                    )
                }

                // Explicit HTTP error (403, 404, 410, 500, etc.)
                return LinkHealth(
                    status = LinkHealthStatus.OFFLINE,
                    httpCode = code,
                    reason = "HTTP $code",
                    latencyMs = elapsed,
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            val msg = e.message ?: e::class.java.simpleName
            val friendlyReason = when {
                msg.contains("timeout", ignoreCase = true) -> "Timeout"
                msg.contains("failed to connect", ignoreCase = true) -> "Unreachable"
                msg.contains("ssl", ignoreCase = true) -> "SSL Error"
                else -> msg.take(20)
            }
            return LinkHealth(
                status = LinkHealthStatus.OFFLINE,
                reason = friendlyReason,
                latencyMs = elapsed,
            )
        }
    }
}
