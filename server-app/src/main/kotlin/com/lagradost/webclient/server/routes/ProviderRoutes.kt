package com.lagradost.webclient.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.webclient.api.HomePageResponseDto
import com.lagradost.webclient.api.MainPageCategoriesResponse
import com.lagradost.webclient.api.MainPageCategoryDto
import com.lagradost.webclient.api.MainPageRowDto
import com.lagradost.webclient.api.ProviderListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

        // Each mainPage entry is an independent network call to the provider site — running them
        // sequentially made the homepage wait on the sum of every category's latency instead of
        // just the slowest one.
        val responses = coroutineScope {
            provider.mainPage.map { entry ->
                async {
                    try {
                        provider.getMainPage(page, MainPageRequest(entry.name, entry.data, entry.horizontalImages))
                    } catch (e: Exception) {
                        null
                    }
                }
            }.map { it.await() }
        }

        val rows = mutableListOf<MainPageRowDto>()
        var hasNext = false
        for (response in responses) {
            if (response == null) continue
            hasNext = hasNext || response.hasNext
            response.items.forEach { row ->
                rows.add(MainPageRowDto(row.name, row.list.map { it.toDto() }, row.isHorizontalImages))
            }
        }
        call.respond(HomePageResponseDto(rows, hasNext))
    }

    // provider.mainPage is a static local list, so this is instant — lets the frontend render row
    // placeholders immediately and lazy-fetch each category's items only as it scrolls into view.
    get("/api/providers/{name}/categories") {
        val name = call.parameters["name"]
        val provider = name?.let { findProvider(it) }
        if (provider == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val categories = provider.mainPage.mapIndexed { index, entry ->
            MainPageCategoryDto(index, entry.name, entry.horizontalImages)
        }
        call.respond(MainPageCategoriesResponse(categories))
    }

    get("/api/providers/{name}/main-page/{index}") {
        val name = call.parameters["name"]
        val index = call.parameters["index"]?.toIntOrNull()
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val provider = name?.let { findProvider(it) }
        val entry = index?.let { provider?.mainPage?.getOrNull(it) }
        if (provider == null || entry == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val response = try {
            provider.getMainPage(page, MainPageRequest(entry.name, entry.data, entry.horizontalImages))
        } catch (e: Exception) {
            null
        }

        if (response == null) {
            call.respond(HomePageResponseDto(emptyList(), false))
            return@get
        }

        val rows = response.items.map { row ->
            MainPageRowDto(row.name, row.list.map { it.toDto() }, row.isHorizontalImages)
        }
        call.respond(HomePageResponseDto(rows, response.hasNext))
    }
}
