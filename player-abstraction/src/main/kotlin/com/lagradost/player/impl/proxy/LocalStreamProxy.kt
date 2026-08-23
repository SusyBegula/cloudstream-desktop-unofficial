package com.lagradost.player.impl.proxy

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
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

    data class ProxySession(
        val headers: Map<String, String>,
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

    fun start() {
        if (server != null) return
        server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                get("/proxy") {
                    handleRequest(call)
                }
            }
        }.start(wait = false)

        port = kotlinx.coroutines.runBlocking {
            server?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: 0
        }
        AppLogger.i("LocalStreamProxy started on port $port")
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

    fun buildProxyUrl(sessionId: String, url: String): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        return "http://127.0.0.1:$port/proxy?s=$sessionId&u=$encodedUrl"
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

            if (isM3u8) {
                val m3u8Content = withContext(ProxyIoDispatcher) {
                    response.body?.string() ?: ""
                }
                val finalUrl = response.request.url.toString()

                val rewritten = rewriteM3u8(m3u8Content, finalUrl, sessionId)
                val bytes = rewritten.toByteArray(Charsets.UTF_8)

                call.response.header("Content-Type", "application/vnd.apple.mpegurl")
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
                    val buffer = ByteArray(32768)
                    val job = currentCoroutineContext()[Job]
                    job?.invokeOnCompletion {
                        try {
                            response.close()
                        } catch (_: Throwable) {}
                    }
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
                            try {
                                response.close()
                            } catch (_: Throwable) {}
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
