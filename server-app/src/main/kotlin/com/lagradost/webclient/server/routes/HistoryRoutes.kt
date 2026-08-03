package com.lagradost.webclient.server.routes

import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.webclient.api.WatchHistoryEntryDto
import com.lagradost.webclient.api.WatchHistoryResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch

fun Route.historyRoutes() {
    get("/api/history") {
        call.respond(WatchHistoryResponse(DesktopDataStore.getAllWatchHistory().map { it.toDto() }))
    }

    patch("/api/history") {
        val entry = call.receive<WatchHistoryEntryDto>()
        val parentId = DesktopDataStore.watchHistoryId(entry.provider, entry.url, entry.season, entry.episode, entry.episodeData)
        DesktopDataStore.setLastWatched(
            WatchHistory(
                parentId = parentId,
                showName = entry.name,
                showUrl = entry.url,
                apiName = entry.provider,
                posterUrl = entry.posterUrl,
                episode = entry.episode,
                season = entry.season,
                episodeId = entry.episodeData,
                position = entry.positionMs / 1000,
                duration = entry.durationMs / 1000,
            ),
        )
        call.respond(HttpStatusCode.OK)
    }

    delete("/api/history/item") {
        val provider = call.request.queryParameters["provider"]
        val url = call.request.queryParameters["url"]
        if (provider == null || url == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@delete
        }
        val season = call.request.queryParameters["season"]?.toIntOrNull()
        val episode = call.request.queryParameters["episode"]?.toIntOrNull()
        val episodeData = call.request.queryParameters["episodeData"]
        DesktopDataStore.removeWatchHistory(DesktopDataStore.watchHistoryId(provider, url, season, episode, episodeData))
        call.respond(HttpStatusCode.OK)
    }

    delete("/api/history") {
        DesktopDataStore.clearAllWatchHistory()
        call.respond(HttpStatusCode.OK)
    }
}
