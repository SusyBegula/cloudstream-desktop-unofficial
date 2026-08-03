package com.lagradost.webclient.server.routes

import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.player.impl.PlayerLinkHandler
import com.lagradost.player.impl.proxy.LocalStreamProxy
import com.lagradost.webclient.api.PlayableStreamDto
import com.lagradost.webclient.api.ResolveRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * Resolves a chosen ExtractorLink into something a browser's <video>/hls.js can actually play.
 * Unlike desktop (MPV/VLC), the browser can never attach custom headers, so every stream kind
 * is routed through LocalStreamProxy — not just HLS (see PlayerLinkHandler.validate's alwaysProxy).
 */
fun Route.resolveRoutes() {
    post("/api/resolve") {
        val request = call.receive<ResolveRequest>()
        val link = request.link.toDomain()

        if (link is ExtractorLinkPlayList) {
            if (link.playlist.isEmpty()) {
                call.respond(HttpStatusCode.UnprocessableEntity, "Empty playlist")
                return@post
            }
            val headers = PlayerLinkHandler.buildHeaderMap(link)
            val sessionId = LocalStreamProxy.registerPlaylistSession(headers, link.playlist)
            call.respond(
                PlayableStreamDto(
                    proxyUrl = LocalStreamProxy.buildPlaylistUrl(sessionId),
                    mimeType = "application/vnd.apple.mpegurl",
                    kind = "HLS",
                    audioTracks = link.audioTracks.map { it.toDto() },
                ),
            )
            return@post
        }

        val validated = PlayerLinkHandler.validate(link, alwaysProxy = true).getOrElse {
            call.respond(HttpStatusCode.UnprocessableEntity, it.message ?: "Failed to resolve link")
            return@post
        }

        val mimeType = when (validated.streamKind) {
            PlayerLinkHandler.StreamKind.HLS -> "application/vnd.apple.mpegurl"
            PlayerLinkHandler.StreamKind.DASH -> "application/dash+xml"
            PlayerLinkHandler.StreamKind.PROGRESSIVE -> "video/mp4"
        }

        call.respond(
            PlayableStreamDto(
                proxyUrl = validated.url,
                mimeType = mimeType,
                kind = validated.streamKind.name,
                audioTracks = validated.audioTracks.map { it.toDto() },
            ),
        )
    }
}
