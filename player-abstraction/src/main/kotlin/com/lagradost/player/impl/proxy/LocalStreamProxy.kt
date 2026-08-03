package com.lagradost.player.impl.proxy

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.queryString
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.UUID

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (ex: Throwable) {}
    }
}

object LocalStreamProxy {
    // Use Kotlin's dynamically scaling IO dispatcher instead of hoarding 500 OS threads
    private val ProxyIoDispatcher = kotlinx.coroutines.Dispatchers.IO

    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    var port: Int = 0
        private set

    /** Interface the embedded Netty engine binds to. Defaults to loopback-only (desktop behavior). */
    var bindHost: String = "127.0.0.1"

    /** Host used when generating proxy URLs handed to the player/browser. Defaults to loopback. */
    var publicHost: String = "127.0.0.1"

    data class ProxySession(
        val headers: Map<String, String>,
        val playlist: List<com.lagradost.cloudstream3.utils.PlayListItem>? = null,
        /** Directory URL (trailing "/") that relative DASH BaseURL/segment paths resolve against. */
        val originBase: String? = null,
    )

    // Capped LRU cache to prevent memory leaks from abandoned video sessions
    private val sessions = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, ProxySession>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ProxySession>): Boolean {
                return size > 100
            }
        }
    )

    private val proxyClient by lazy {
        app.baseClient.newBuilder()
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 1000
                    maxRequestsPerHost = 500
                },
            )
            .build()
    }

    /** @param fixedPort When non-zero, binds to this exact port instead of an OS-assigned one (needed so a LAN client can reach a stable, known port). */
    fun start(fixedPort: Int = 0) {
        if (server != null) return
        server = embeddedServer(Netty, port = fixedPort, host = bindHost) {
            routing {
                get("/proxy") {
                    handleRequest(call)
                }
                get("/proxy/playlist/{sessionId}.m3u8") {
                    handlePlaylistRequest(call)
                }
                get("/proxy/base/{sessionId}/{path...}") {
                    handleBaseRequest(call)
                }
            }
        }.start(wait = false)

        port = kotlinx.coroutines.runBlocking {
            server?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: 0
        }
        AppLogger.i("LocalStreamProxy started on $bindHost:$port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        sessions.clear()
    }

    fun registerSession(headers: Map<String, String>): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = ProxySession(headers)
        return sessionId
    }

    /**
     * Registers a session for an [com.lagradost.cloudstream3.utils.ExtractorLinkPlayList]'s
     * chunks. Unlike [registerSession], the resulting URL (see [buildPlaylistUrl]) is a
     * synthetic HLS VOD playlist stitching every chunk — the browser-compatible equivalent
     * of MPV's EDL-file stitching (see [PlayerLinkHandler.writeEdlFile]).
     */
    fun registerPlaylistSession(
        headers: Map<String, String>,
        playlist: List<com.lagradost.cloudstream3.utils.PlayListItem>,
    ): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = ProxySession(headers, playlist)
        return sessionId
    }

    fun buildProxyUrl(sessionId: String, url: String): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        return "http://$publicHost:$port/proxy?s=$sessionId&u=$encodedUrl"
    }

    fun buildPlaylistUrl(sessionId: String): String {
        return "http://$publicHost:$port/proxy/playlist/$sessionId.m3u8"
    }

    private suspend fun handlePlaylistRequest(call: io.ktor.server.application.ApplicationCall) {
        val sessionId = call.parameters["sessionId"]
        val session = sessionId?.let { sessions[it] }
        val playlist = session?.playlist
        if (sessionId == null || session == null || playlist.isNullOrEmpty()) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val body = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
            appendLine("#EXT-X-TARGETDURATION:${playlist.maxOf { (it.durationUs / 1_000_000).coerceAtLeast(1) }}")
            for (item in playlist) {
                if (item.url.isBlank()) continue
                val durationSeconds = item.durationUs / 1_000_000.0
                appendLine("#EXTINF:${"%.3f".format(durationSeconds)},")
                appendLine(buildProxyUrl(sessionId, item.url))
            }
            appendLine("#EXT-X-ENDLIST")
        }

        call.response.header("Content-Type", "application/vnd.apple.mpegurl")
        call.respondBytes(body.toByteArray(Charsets.UTF_8), status = HttpStatusCode.OK)
    }

    /**
     * Generic passthrough for DASH segment/init requests. The manifest's `<BaseURL>` is
     * rewritten (see [rewriteDash]) to point here instead of the origin, so relative
     * SegmentTemplate/segment references the player resolves land on this route — which then
     * re-resolves them against the session's stored [ProxySession.originBase] and forwards
     * the request with the session's headers, exactly like [handleRequest] does for HLS.
     */
    private suspend fun handleBaseRequest(call: io.ktor.server.application.ApplicationCall) {
        try {
            val sessionId = call.parameters["sessionId"]
            val session = sessionId?.let { sessions[it] }
            val originBase = session?.originBase
            if (sessionId == null || session == null || originBase == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val subPath = call.parameters.getAll("path")?.joinToString("/") ?: ""
            val query = call.request.queryString().takeIf { it.isNotBlank() }
            val targetUrl = originBase + subPath + (if (query != null) "?$query" else "")

            val mergedHeaders = session.headers.toMutableMap()
            mergedHeaders.keys.filter { it.equals("Accept-Encoding", ignoreCase = true) }.forEach { mergedHeaders.remove(it) }
            call.request.headers["Range"]?.let { mergedHeaders["Range"] = it }

            val requestBuilder = okhttp3.Request.Builder().url(targetUrl)
            mergedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = try {
                proxyClient.newCall(requestBuilder.build()).await()
            } catch (e: Exception) {
                AppLogger.e("LocalStreamProxy base request failed! URL: $targetUrl Error: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError)
                return
            }

            if (!response.isSuccessful) {
                response.body?.close()
                call.respond(HttpStatusCode.fromValue(response.code))
                return
            }

            response.header("Content-Range")?.let { call.response.header("Content-Range", it) }
            response.header("Accept-Ranges")?.let { call.response.header("Accept-Ranges", it) }

            val contentTypeStr = response.header("Content-Type") ?: "application/octet-stream"
            val parsedContentType = try {
                ContentType.parse(contentTypeStr)
            } catch (e: Exception) {
                ContentType.Application.OctetStream
            }
            val cl = response.body?.contentLength() ?: -1L

            call.respondBytesWriter(
                contentType = parsedContentType,
                status = HttpStatusCode.fromValue(response.code),
                contentLength = if (cl >= 0) cl else null,
            ) {
                val streamSource = response.body?.source() ?: return@respondBytesWriter
                val buffer = ByteArray(16384)
                try {
                    while (!isClosedForWrite) {
                        val bytesRead = withContext(ProxyIoDispatcher) { streamSource.read(buffer) }
                        if (bytesRead == -1) break
                        writeFully(buffer, 0, bytesRead)
                        flush()
                    }
                } catch (e: Exception) {
                    // Ignored (client disconnected)
                } finally {
                    withContext(ProxyIoDispatcher) { response.body?.close() }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("LocalStreamProxy base error: ${e.message}")
            try {
                call.respond(HttpStatusCode.InternalServerError)
            } catch (_: Exception) {}
        }
    }

    private suspend fun handleRequest(call: io.ktor.server.application.ApplicationCall) {
        try {
            val sessionId = call.request.queryParameters["s"]
            val encodedUrl = call.request.queryParameters["u"]

            if (sessionId == null || encodedUrl == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val url = String(Base64.getUrlDecoder().decode(encodedUrl), Charsets.UTF_8)
            val session = sessions[sessionId]

            if (session == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val mergedHeaders = session.headers.toMutableMap()

            val keysToRemove = mergedHeaders.keys.filter { it.equals("Accept-Encoding", ignoreCase = true) }
            keysToRemove.forEach { mergedHeaders.remove(it) }

            call.request.headers["Range"]?.let {
                mergedHeaders["Range"] = it
            }

            val requestBuilder = okhttp3.Request.Builder().url(url)
            mergedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

            // Use completely async OkHttp fetch to prevent ThreadPool exhaustion
            val response = try {
                proxyClient.newCall(requestBuilder.build()).await()
            } catch (e: Exception) {
                AppLogger.e("LocalStreamProxy Request Failed (Async)! URL: $url Error: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError)
                return
            }

            if (!response.isSuccessful) {
                AppLogger.e("LocalStreamProxy Request Failed! Code: ${response.code} URL: $url")
                response.body?.close()
                call.respond(HttpStatusCode.fromValue(response.code))
                return
            }

            val contentTypeStr = response.header("Content-Type") ?: "application/octet-stream"
            val isM3u8 = url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".m3u", ignoreCase = true) ||
                contentTypeStr.contains("mpegurl", ignoreCase = true) ||
                contentTypeStr.contains("x-mpegURL", ignoreCase = true) ||
                withContext(ProxyIoDispatcher) {
                    try {
                        val s = response.body?.source()
                        s != null && s.request(7) && s.peek().readUtf8(7) == "#EXTM3U"
                    } catch (e: Exception) {
                        false
                    }
                }

            val isDash = url.contains(".mpd", ignoreCase = true) ||
                contentTypeStr.contains("dash+xml", ignoreCase = true)

            if (isM3u8) {
                val m3u8Content = withContext(ProxyIoDispatcher) {
                    response.body?.string() ?: ""
                }
                val finalUrl = response.request.url.toString()

                val rewritten = rewriteM3u8(m3u8Content, finalUrl, sessionId)
                val bytes = rewritten.toByteArray(Charsets.UTF_8)

                call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                call.respondBytes(bytes, status = HttpStatusCode.OK)
            } else if (isDash) {
                val mpdContent = withContext(ProxyIoDispatcher) {
                    response.body?.string() ?: ""
                }
                val finalUrl = response.request.url.toString()

                val rewritten = rewriteDash(mpdContent, finalUrl, sessionId)
                val bytes = rewritten.toByteArray(Charsets.UTF_8)

                call.response.header("Content-Type", "application/dash+xml")
                call.respondBytes(bytes, status = HttpStatusCode.OK)
            } else {
                response.header("Content-Range")?.let { call.response.header("Content-Range", it) }
                response.header("Accept-Ranges")?.let { call.response.header("Accept-Ranges", it) }

                val cl = response.body?.contentLength() ?: -1L
                val contentLengthParam = if (cl >= 0) cl else null

                val parsedContentType = try {
                    ContentType.parse(contentTypeStr)
                } catch (e: Exception) {
                    ContentType.Application.OctetStream
                }

                // Stream chunks asynchronously to Ktor
                call.respondBytesWriter(
                    contentType = parsedContentType,
                    status = HttpStatusCode.fromValue(response.code),
                    contentLength = contentLengthParam,
                ) {
                    val streamSource = response.body?.source() ?: return@respondBytesWriter
                    val buffer = ByteArray(16384)
                    try {
                        while (!isClosedForWrite) {
                            val bytesRead = withContext(ProxyIoDispatcher) {
                                streamSource.read(buffer)
                            }
                            if (bytesRead == -1) break
                            writeFully(buffer, 0, bytesRead)
                            flush()
                        }
                    } catch (e: Exception) {
                        // Ignored (Client disconnected, e.g. user seeking)
                    } finally {
                        withContext(ProxyIoDispatcher) {
                            response.body?.close()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("LocalStreamProxy error: ${e.message}")
            try {
                call.respond(HttpStatusCode.InternalServerError)
            } catch (_: Exception) {}
        }
    }

    private fun rewriteM3u8(content: String, baseUrl: String, sessionId: String): String {
        val lines = content.split("\n")
        val rewritten = buildString {
            for (line in lines) {
                val trim = line.trim()
                if (trim.isEmpty()) continue
                if (trim.startsWith("#")) {
                    if (trim.contains("URI=\"")) {
                        val uriRegex = Regex("""URI="([^"]+)"""")
                        val newLine = trim.replace(uriRegex) { result ->
                            val uri = result.groupValues[1]
                            val absolute = resolveUrl(baseUrl, uri)
                            "URI=\"${buildProxyUrl(sessionId, absolute)}\""
                        }
                        appendLine(newLine)
                    } else {
                        appendLine(trim)
                    }
                } else {
                    val absolute = resolveUrl(baseUrl, trim)
                    appendLine(buildProxyUrl(sessionId, absolute))
                }
            }
        }
        return rewritten
    }

    /**
     * Rewrites a DASH MPD manifest's `<BaseURL>` (inserting one if absent) to point at
     * [handleBaseRequest]'s proxy route, and stores the resolved absolute origin directory
     * on the session so relative SegmentTemplate/segment references keep working once the
     * player resolves them against the new BaseURL. SegmentTemplate `$Number$`/`$Time$`
     * attributes are left untouched — they're relative paths, resolved client-side against
     * BaseURL like any other DASH manifest.
     */
    private fun rewriteDash(content: String, baseUrl: String, sessionId: String): String {
        val baseUrlRegex = Regex("""<BaseURL>([^<]*)</BaseURL>""")
        val existingMatch = baseUrlRegex.find(content)

        val originBase = if (existingMatch != null) {
            val absolute = resolveUrl(baseUrl, existingMatch.groupValues[1].trim())
            if (absolute.endsWith("/")) absolute else absolute.substringBeforeLast('/') + "/"
        } else {
            baseUrl.substringBeforeLast('/') + "/"
        }

        sessions[sessionId]?.let { existing ->
            sessions[sessionId] = existing.copy(originBase = originBase)
        }

        val proxyBase = "http://$publicHost:$port/proxy/base/$sessionId/"

        return if (existingMatch != null) {
            content.replace(baseUrlRegex, "<BaseURL>$proxyBase</BaseURL>")
        } else {
            val mpdOpenTag = Regex("""<MPD\b[^>]*>""").find(content)
            if (mpdOpenTag != null) {
                val insertAt = mpdOpenTag.range.last + 1
                content.substring(0, insertAt) + "<BaseURL>$proxyBase</BaseURL>" + content.substring(insertAt)
            } else {
                content
            }
        }
    }

    private fun resolveUrl(base: String, uri: String): String {
        if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
            return uri
        }
        val baseUri = URI(base)
        val resolved = baseUri.resolve(uri).toString()

        // Inherit query parameters from the base URL (for auth tokens like md5/expires)
        if (baseUri.query != null && !resolved.contains("?")) {
            return "$resolved?${baseUri.query}"
        }

        return resolved
    }
}
