@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class)

package com.lagradost.cloudstream3.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.init.initNetwork
import com.lagradost.cloudstream3.desktop.init.initPlugins
import com.lagradost.cloudstream3.desktop.init.initProviders
import com.lagradost.cloudstream3.desktop.init.initProxy
import com.lagradost.cloudstream3.desktop.init.initSecurity
import com.lagradost.cloudstream3.desktop.init.launchAutoUpdater
import com.lagradost.cloudstream3.desktop.ui.CloudstreamApp
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Single unified entry point for CloudStream Desktop Client.
 *
 * Initialization order (matches Android startup semantics):
 *   1. Local stream proxy
 *   2. Security (exception handler, BouncyCastle, DataStore)
 *   3. Network (OkHttp clients, Jackson mapper, WebView, cookies)
 *   4. Built-in meta-providers (TMDB, Trakt, CrossTMDB)
 *   5. Load installed plugins (dex2jar conversion) + cloned sites
 *   6. Background auto-updater
 *   7. Compose UI window
 */
fun main() {
    AppLogger.i("Launching CloudStream Desktop Client...")
    AppLogger.i("Platform: ${PlatformPaths.currentOS}")
    AppLogger.i("App data directory: ${PlatformPaths.appDataDir.absolutePath}")

    initProxy()
    initSecurity()
    initNetwork()
    initProviders()
    initPlugins()
    launchAutoUpdater()

    application {
        setSingletonImageLoaderFactory { context ->
            coil3.ImageLoader.Builder(context)
                .memoryCache {
                    coil3.memory.MemoryCache.Builder()
                        .maxSizePercent(context, 0.1)
                        .build()
                }
                .diskCache {
                    coil3.disk.DiskCache.Builder()
                        .directory(File(PlatformPaths.appDataDir, "image_cache").also { it.mkdirs() }.toOkioPath())
                        .maxSizeBytes(512L * 1024 * 1024) // 512MB
                        .build()
                }
                .components {
                    add(
                        coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                            callFactory = { request ->
                                com.lagradost.cloudstream3.app.baseClient.newCall(request)
                            },
                        ),
                    )
                }
                .crossfade(true)
                .build()
        }

        val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
        val windowWidth = (screenSize.width * 0.7).toInt().dp
        val windowHeight = (screenSize.height * 0.7).toInt().dp
        val state = androidx.compose.ui.window.rememberWindowState(
            width = windowWidth,
            height = windowHeight,
            position = androidx.compose.ui.window.WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "CloudStream - Unofficial Desktop Client (Pre-Alpha)",
            state = state,
            icon = androidx.compose.ui.res.painterResource("logo_ui.png"),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.lagradost.cloudstream3.desktop.ui.LocalWindowState provides state,
            ) {
                CloudstreamApp()
            }
        }
    }
}
