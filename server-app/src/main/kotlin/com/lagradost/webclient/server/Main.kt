package com.lagradost.webclient.server

import com.lagradost.common.logging.AppLogger
import com.lagradost.player.impl.proxy.LocalStreamProxy
import com.lagradost.webclient.server.init.bootstrapServer
import com.lagradost.webclient.server.routes.bookmarksRoutes
import com.lagradost.webclient.server.routes.historyRoutes
import com.lagradost.webclient.server.routes.imageRoutes
import com.lagradost.webclient.server.routes.linksRoutes
import com.lagradost.webclient.server.routes.pluginRoutes
import com.lagradost.webclient.server.routes.providerRoutes
import com.lagradost.webclient.server.routes.resolveRoutes
import com.lagradost.webclient.server.routes.searchRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
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
    embeddedServer(Netty, port = apiPort, host = "0.0.0.0") {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        install(CORS) {
            anyHost()
        }
        routing {
            // Optional: serve the compiled web-app bundle same-origin (so ApiClient's same-origin
            // base URL resolution works), pointed at :web-app's wasmJs browser distribution output.
            System.getenv("WEB_APP_DIST")?.let(::File)?.takeIf { it.exists() }?.let { dist ->
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
