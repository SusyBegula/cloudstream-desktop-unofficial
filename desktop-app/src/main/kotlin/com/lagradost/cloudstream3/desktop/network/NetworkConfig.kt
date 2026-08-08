@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class)

package com.lagradost.cloudstream3.desktop.network

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.nicehttp.ignoreAllSSLErrors
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

enum class DohProvider(val title: String) {
    NONE("Off (System Default)"),
    GOOGLE("Google"),
    CLOUDFLARE("Cloudflare"),
    ADGUARD("AdGuard"),
    QUAD9("Quad9"),
    DNSWATCH("DNSWatch"),
    DNSSB("DNS.SB"),
    CANADIAN_SHIELD("Canadian Shield"),
}

object NetworkConfig {
    const val PREF_DOH_PROVIDER = "doh_provider"

    /**
     * Rebuilds and assigns the global NiceHttp clients (`app.baseClient` and `insecureApp.baseClient`)
     * using the current DNS over HTTPS configuration from the DesktopDataStore.
     */
    fun updateGlobalNetworkClients() {
        val providerIndex = DesktopDataStore.getKey<Int>(PREF_DOH_PROVIDER) ?: 0
        val provider = DohProvider.values().getOrNull(providerIndex) ?: DohProvider.NONE

        val baseBuilder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        try {
            // Sort IPv4 first to bypass OkHttp's faulty Happy Eyeballs implementation on Windows
            baseBuilder.dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    val addresses = okhttp3.Dns.SYSTEM.lookup(hostname)
                    return addresses.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
                }
            })
        } catch (e: Exception) {
            AppLogger.e("Failed to initialize custom DNS: ${e.message}", e)
        }

        // Apply CloudflareKiller interceptor
        baseBuilder.addInterceptor(CloudflareKiller())

        // Apply DoH Provider
        when (provider) {
            DohProvider.GOOGLE -> baseBuilder.addGoogleDns()
            DohProvider.CLOUDFLARE -> baseBuilder.addCloudFlareDns()
            DohProvider.ADGUARD -> baseBuilder.addAdGuardDns()
            DohProvider.QUAD9 -> baseBuilder.addQuad9Dns()
            DohProvider.DNSWATCH -> baseBuilder.addDNSWatchDns()
            DohProvider.DNSSB -> baseBuilder.addDnsSbDns()
            DohProvider.CANADIAN_SHIELD -> baseBuilder.addCanadianShieldDns()
            DohProvider.NONE -> { /* Use System DNS */ }
        }

        // Apply to main client
        app.baseClient = baseBuilder.build()
        // CRITICAL: Restore defaultHeaders that NiceHttp uses for ALL requests.
        // Without this, OkHttp sends 'okhttp/4.x' as User-Agent which Cloudflare blocks.
        app.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        // Apply to insecure client
        val insecureBuilder = app.baseClient.newBuilder()
        try {
            insecureBuilder.ignoreAllSSLErrors()
        } catch (_: Exception) {}
        insecureApp.baseClient = insecureBuilder.build()
        insecureApp.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        // Suppress noisy OkHttp connection pool leak warnings from third-party plugins
        java.util.logging.Logger.getLogger(OkHttpClient::class.java.name).level = java.util.logging.Level.SEVERE
        java.util.logging.Logger.getLogger(okhttp3.internal.platform.Platform::class.java.name).level = java.util.logging.Level.SEVERE

        AppLogger.i("Initialized global NiceHttp clients with DoH Provider: ${provider.title}")
    }
}
