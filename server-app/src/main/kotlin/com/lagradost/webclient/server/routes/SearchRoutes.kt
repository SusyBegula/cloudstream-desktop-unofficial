package com.lagradost.webclient.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.webclient.api.AggregatedSearchResponse
import com.lagradost.webclient.api.LoadRequest
import com.lagradost.webclient.api.LoadResponseDto
import com.lagradost.webclient.api.SearchResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

fun Route.searchRoutes() {
    get("/api/search") {
        val query = call.request.queryParameters["q"]
        val providerName = call.request.queryParameters["provider"]
        val global = call.request.queryParameters["global"]?.toBoolean() ?: false

        if (query.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing 'q' query parameter")
            return@get
        }

        val providers = when {
            providerName != null -> listOfNotNull(findProvider(providerName))
            global -> APIHolder.allProviders.filter { it.isRealProvider() }
            else -> emptyList()
        }

        val perProvider = coroutineScope {
            providers.map { provider ->
                async {
                    val results = try {
                        provider.search(query, 1)?.items ?: provider.search(query).orEmpty()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    SearchResponseDto(provider.name, results.map { it.toDto() })
                }
            }.map { it.await() }
        }

        call.respond(AggregatedSearchResponse(query, perProvider))
    }

    post("/api/load") {
        val request = call.receive<LoadRequest>()
        val provider = findProvider(request.provider)
        if (provider == null) {
            call.respond(HttpStatusCode.NotFound, "Unknown provider ${request.provider}")
            return@post
        }

        val loadResponse = try {
            provider.load(request.url)
        } catch (e: Exception) {
            null
        }

        if (loadResponse == null) {
            call.respond(HttpStatusCode.NotFound, "Could not load ${request.url}")
            return@post
        }

        call.respond<LoadResponseDto>(loadResponse.toDto())
    }
}
