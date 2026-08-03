@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class)

package com.lagradost.webclient.server.network

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.logging.AppLogger
import com.lagradost.nicehttp.ignoreAllSSLErrors
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Server-side counterpart of desktop-app's NetworkConfig. DoH provider selection is
 * out of scope for the server (no settings UI yet) — always uses system DNS.
 */
object NetworkConfig {

    fun updateGlobalNetworkClients() {
        val baseBuilder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        try {
            baseBuilder.dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    val addresses = okhttp3.Dns.SYSTEM.lookup(hostname)
                    return addresses.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
                }
            })
        } catch (e: Exception) {
            AppLogger.e("Failed to initialize custom DNS: ${e.message}", e)
        }

        baseBuilder.addInterceptor(CloudflareKiller())

        app.baseClient = baseBuilder.build()
        // CRITICAL: Restore defaultHeaders that NiceHttp uses for ALL requests.
        // Without this, OkHttp sends 'okhttp/4.x' as User-Agent which Cloudflare blocks.
        app.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        val insecureBuilder = app.baseClient.newBuilder()
        try {
            insecureBuilder.ignoreAllSSLErrors()
        } catch (_: Exception) {
        }
        insecureApp.baseClient = insecureBuilder.build()
        insecureApp.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        java.util.logging.Logger.getLogger(OkHttpClient::class.java.name).level = java.util.logging.Level.SEVERE
        java.util.logging.Logger.getLogger(okhttp3.internal.platform.Platform::class.java.name).level = java.util.logging.Level.SEVERE

        AppLogger.i("Initialized global NiceHttp clients")
    }
}
