package com.lagradost.webclient.server.routes

import com.lagradost.webclient.api.LinkEventDto
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val wsJson = Json { encodeDefaults = true }

/** Streams resolved ExtractorLinks/subtitles to the browser as MainAPI.loadLinks discovers them. */
fun Route.linksRoutes() {
    webSocket("/api/links") {
        val providerName = call.request.queryParameters["provider"]
        val data = call.request.queryParameters["data"]
        val provider = providerName?.let { findProvider(it) }

        if (provider == null || data == null) {
            send(Frame.Text(wsJson.encodeToString(LinkEventDto.serializer(), LinkEventDto(kind = "error", message = "invalid provider/data"))))
            close()
            return@webSocket
        }

        val events = Channel<LinkEventDto>(Channel.UNLIMITED)
        val job = launch {
            try {
                provider.loadLinks(
                    data,
                    false,
                    subtitleCallback = { events.trySend(LinkEventDto(kind = "subtitle", subtitle = it.toDto())) },
                    callback = { events.trySend(LinkEventDto(kind = "link", link = it.toDto())) },
                )
            } catch (e: Exception) {
                events.trySend(LinkEventDto(kind = "error", message = e.message))
            } finally {
                events.trySend(LinkEventDto(kind = "done"))
                events.close()
            }
        }

        for (event in events) {
            send(Frame.Text(wsJson.encodeToString(LinkEventDto.serializer(), event)))
        }
        job.join()
        close()
    }
}
