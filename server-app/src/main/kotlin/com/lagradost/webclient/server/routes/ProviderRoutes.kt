package com.lagradost.webclient.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.webclient.api.HomePageResponseDto
import com.lagradost.webclient.api.MainPageRowDto
import com.lagradost.webclient.api.ProviderListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun findProvider(name: String): MainAPI? =
    APIHolder.allProviders.firstOrNull { it.name == name && it.isRealProvider() }

fun Route.providerRoutes() {
    get("/api/providers") {
        val providers = APIHolder.allProviders.filter { it.isRealProvider() }.map { it.toDto() }
        call.respond(ProviderListResponse(providers))
    }

    get("/api/providers/{name}/main-page") {
        val name = call.parameters["name"]
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val provider = name?.let { findProvider(it) }
        if (provider == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val rows = mutableListOf<MainPageRowDto>()
        var hasNext = false
        for (entry in provider.mainPage) {
            val response = try {
                provider.getMainPage(page, MainPageRequest(entry.name, entry.data, entry.horizontalImages))
            } catch (e: Exception) {
                null
            } ?: continue
            hasNext = hasNext || response.hasNext
            response.items.forEach { row ->
                rows.add(MainPageRowDto(row.name, row.list.map { it.toDto() }, row.isHorizontalImages))
            }
        }
        call.respond(HomePageResponseDto(rows, hasNext))
    }
}
