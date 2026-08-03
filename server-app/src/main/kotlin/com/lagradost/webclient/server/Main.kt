package com.lagradost.webclient.server

import com.lagradost.common.logging.AppLogger
import com.lagradost.player.impl.proxy.LocalStreamProxy
import com.lagradost.webclient.api.ActionResult
import com.lagradost.webclient.server.init.bootstrapServer
import com.lagradost.webclient.server.routes.bookmarksRoutes
import com.lagradost.webclient.server.routes.historyRoutes
import com.lagradost.webclient.server.routes.imageRoutes
import com.lagradost.webclient.server.routes.linksRoutes
import com.lagradost.webclient.server.routes.pluginRoutes
import com.lagradost.webclient.server.routes.providerRoutes
import com.lagradost.webclient.server.routes.resolveRoutes
import com.lagradost.webclient.server.routes.searchRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import java.io.File
import java.net.NetworkInterface

/**
 * LAN/localhost-only by design (per project decision) — no auth/session security is applied.
 * Override with env vars if you need to bind elsewhere on your own network.
 */
private fun detectLanHost(): String {
    System.getenv("SERVER_HOST")?.let { return it }
    return try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it.address.size == 4 && !it.isLoopbackAddress }
            ?.hostAddress ?: "127.0.0.1"
    } catch (e: Exception) {
        "127.0.0.1"
    }
}

fun main() {
    val host = detectLanHost()
    val apiPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
    val proxyPort = System.getenv("PROXY_PORT")?.toIntOrNull() ?: 8081

    AppLogger.i("Bootstrapping CloudStream web-client server...")
    bootstrapServer()

    LocalStreamProxy.bindHost = "0.0.0.0"
    LocalStreamProxy.publicHost = host
    LocalStreamProxy.start(fixedPort = proxyPort)

    AppLogger.i("Starting HTTP API on $host:$apiPort (proxy on $host:$proxyPort)")
    embeddedServer(Netty, environment = applicationEnvironment {}, configure = {
        connector {
            this.host = "0.0.0.0"
            this.port = apiPort
        }
        // Netty's default (8192B) is too small for browsers that carry large cookies from other
        // apps sharing the bare "localhost" domain (cookies aren't port-scoped) — those requests
        // were being rejected at the transport layer with an empty 400, before reaching any route.
        maxHeaderSize = 65536
    }) {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        install(CORS) {
            anyHost()
            // Ktor's CORS default only allows GET/POST/HEAD and "simple" content types
            // (text/plain, form-urlencoded, multipart/form-data) — every JSON POST/PUT/PATCH/DELETE
            // route here was being rejected with an empty 403 before reaching routing.
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowNonSimpleContentTypes = true
        }
        // App-wide safety net: without this, any unhandled exception (e.g. malformed request
        // bodies failing content-negotiation deserialization) results in a bare status code
        // with an empty response body, which the frontend can't show a useful error for.
        install(StatusPages) {
            exception<ContentTransformationException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ActionResult(false, cause.message ?: "Invalid request body"))
            }
            exception<BadRequestException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ActionResult(false, cause.message ?: "Bad request"))
            }
            exception<Throwable> { call, cause ->
                AppLogger.e("Unhandled exception on ${call.request.uri}", cause)
                call.respond(HttpStatusCode.InternalServerError, ActionResult(false, cause.message ?: "Internal server error"))
            }
        }
        routing {
            // Optional: serve the compiled web-app bundle same-origin (so ApiClient's same-origin
            // base URL resolution works), pointed at :web-app's wasmJs browser distribution output.
            val distDir = System.getenv("WEB_APP_DIST")?.let(::File)?.takeIf { it.exists() }
                ?: File("web-frontend/dist").takeIf { it.exists() }
                ?: File("web-app/build/dist/wasmJs/productionExecutable").takeIf { it.exists() }

            distDir?.let { dist ->
                AppLogger.i("Serving web app static files from ${dist.absolutePath}")
                staticFiles("/", dist)
            }
            providerRoutes()
            searchRoutes()
            linksRoutes()
            resolveRoutes()
            imageRoutes()
            historyRoutes()
            bookmarksRoutes()
            pluginRoutes()
        }
    }.start(wait = true)
}
