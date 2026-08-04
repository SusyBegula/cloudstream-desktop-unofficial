package com.lagradost.webclient.server.routes

import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.PreferredSource
import com.lagradost.webclient.api.PreferredSourceDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

/** Remembers/forgets which stream source to prefer for a given show (see PreferredSourceDto). */
fun Route.preferredSourceRoutes() {
    get("/api/preferred-source") {
        val provider = call.request.queryParameters["provider"]
        val seriesUrl = call.request.queryParameters["seriesUrl"]
        if (provider == null || seriesUrl == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val pref = DesktopDataStore.getPreferredSource(provider, seriesUrl)
        if (pref == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(pref.toDto())
        }
    }

    put("/api/preferred-source") {
        val dto = call.receive<PreferredSourceDto>()
        DesktopDataStore.setPreferredSource(
            PreferredSource(provider = dto.provider, showUrl = dto.seriesUrl, sourceName = dto.sourceName),
        )
        call.respond(HttpStatusCode.OK)
    }

    delete("/api/preferred-source") {
        val provider = call.request.queryParameters["provider"]
        val seriesUrl = call.request.queryParameters["seriesUrl"]
        if (provider == null || seriesUrl == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@delete
        }
        DesktopDataStore.removePreferredSource(provider, seriesUrl)
        call.respond(HttpStatusCode.OK)
    }
}
