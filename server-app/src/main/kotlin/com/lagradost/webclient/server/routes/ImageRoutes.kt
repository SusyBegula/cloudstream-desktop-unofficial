package com.lagradost.webclient.server.routes

import com.lagradost.cloudstream3.app
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import okhttp3.Request

/**
 * Same-origin image passthrough. Browsers can `<img src>` any cross-origin URL fine, but
 * WebAsyncImage decodes raw bytes into a Skia bitmap (needed since Compose-for-Web draws to a
 * canvas, not the DOM), and a cross-origin `fetch()` for that would be CORS-blocked on most
 * image CDNs. Fetching the bytes server-side and re-serving them same-origin sidesteps that.
 */
fun Route.imageRoutes() {
    get("/api/image") {
        val url = call.request.queryParameters["url"]
        if (url.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        try {
            val response = app.baseClient.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    call.respond(HttpStatusCode.fromValue(it.code))
                    return@get
                }
                val bytes = it.body?.bytes() ?: ByteArray(0)
                val contentType = it.header("Content-Type")?.let { ct ->
                    runCatching { ContentType.parse(ct) }.getOrNull()
                } ?: ContentType.Image.Any
                call.respondBytes(bytes, contentType)
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway)
        }
    }
}
