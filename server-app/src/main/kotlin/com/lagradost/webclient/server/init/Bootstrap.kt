package com.lagradost.webclient.server.init

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.metaproviders.CrossTmdbProvider
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.runtime.loader.ExtensionLoader
import com.lagradost.webclient.server.ServerErrorReporter
import com.lagradost.webclient.server.network.NetworkConfig
import com.lagradost.webclient.server.network.PlaywrightResolverImpl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Headless bootstrap for the web-client server, mirroring desktop-app's init/{SecurityInit,
 * NetworkInit,PluginInit}.kt sequence (see plugin-sandbox's PluginTester.kt for the same
 * pattern applied to a pure-CLI context). Re-implemented here rather than shared/extracted,
 * per the "leave desktop-app untouched" decision.
 */

fun initSecurity() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        ServerErrorReporter.report("Unhandled exception in ${thread.name}", throwable)
    }

    java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 1)
    AppLogger.i("Registered BouncyCastle Security Provider")

    // Force initialization of DataStore BEFORE plugins are loaded (prevents plugin <clinit>
    // from racing DataStore's own lazy init).
    DesktopDataStore.init()
}

fun initNetwork() {
    NetworkConfig.updateGlobalNetworkClients()

    // Patch Jackson mapper for dex2jar Kotlin reflection bugs (VerifiedRepo inner data classes)
    val fallbackModule = object : com.fasterxml.jackson.databind.module.SimpleModule() {
        override fun setupModule(context: SetupContext) {
            super.setupModule(context)
            context.addDeserializers(object : com.fasterxml.jackson.databind.deser.Deserializers.Base() {
                override fun findBeanDeserializer(
                    type: com.fasterxml.jackson.databind.JavaType,
                    config: com.fasterxml.jackson.databind.DeserializationConfig,
                    beanDesc: com.fasterxml.jackson.databind.BeanDescription,
                ): com.fasterxml.jackson.databind.JsonDeserializer<*>? {
                    if (type.rawClass.name.contains("VerifiedRepo")) {
                        return object : com.fasterxml.jackson.databind.JsonDeserializer<Any>() {
                            override fun deserialize(
                                p: com.fasterxml.jackson.core.JsonParser,
                                ctxt: com.fasterxml.jackson.databind.DeserializationContext,
                            ): Any? {
                                val node = p.codec.readTree<com.fasterxml.jackson.databind.JsonNode>(p)
                                val name = node.get("name")?.asText() ?: ""
                                val url = node.get("url")?.asText() ?: ""
                                return try {
                                    val clazz = type.rawClass
                                    val c = clazz.constructors.find { it.parameterCount == 2 } ?: clazz.constructors.firstOrNull()
                                    c?.let {
                                        try {
                                            it.newInstance(name, url)
                                        } catch (e: Exception) {
                                            try {
                                                it.newInstance(url, name)
                                            } catch (e2: Exception) {
                                                null
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("VerifiedRepo deserialization failed", e)
                                    null
                                }
                            }
                        }
                    }
                    return null
                }
            })
        }
    }
    mapper.registerModule(fallbackModule)

    WebViewResolver.webViewHandler = { request, callback ->
        PlaywrightResolverImpl.resolve(request, callback)
    }

    android.webkit.CookieManager.setCookieHandler = { url, value ->
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl != null) {
            val cookie = okhttp3.Cookie.parse(httpUrl, value)
            if (cookie != null) {
                com.lagradost.cloudstream3.app.baseClient.cookieJar.saveFromResponse(httpUrl, listOf(cookie))
            }
        }
    }

    android.webkit.CookieManager.getCookieHandler = { url ->
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl != null) {
            val cookies = com.lagradost.cloudstream3.app.baseClient.cookieJar.loadForRequest(httpUrl)
            if (cookies.isNotEmpty()) cookies.joinToString("; ") { "${it.name}=${it.value}" } else null
        } else {
            null
        }
    }
}

fun initProviders() {
    val builtIns = listOf(TmdbProvider(), TraktProvider(), CrossTmdbProvider())
    synchronized(APIHolder.allProviders) {
        builtIns.forEach { provider ->
            provider.sourcePlugin = "built-in"
            APIHolder.allProviders.add(provider)
            APIHolder.addPluginMapping(provider)
        }
    }
    AppLogger.i("Registered ${builtIns.size} built-in meta-providers")
}

/** Scans the shared Extensions directory and loads all installed plugin JARs/`.cs3`s. */
fun initPlugins() {
    val extensionsDir = PlatformPaths.extensionsDir
    if (!extensionsDir.exists()) {
        AppLogger.w("No extensions directory found at ${extensionsDir.absolutePath}")
        return
    }

    val jarFiles = extensionsDir.walkTopDown()
        .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
        .filter { !it.name.endsWith("-jvm.jar") }
        .toList()

    if (jarFiles.isEmpty()) {
        AppLogger.i("No plugins found in ${extensionsDir.absolutePath}")
        return
    }

    var loaded = 0
    var failed = 0
    for (jarFile in jarFiles) {
        try {
            ExtensionLoader.loadAndInit(jarFile)
            loaded++
        } catch (e: Throwable) {
            failed++
            AppLogger.e("Failed to load plugin ${jarFile.name}: ${e.message}")
        }
    }
    AppLogger.i("Plugin loading complete: $loaded loaded, $failed failed (of ${jarFiles.size} total)")
}

fun bootstrapServer() {
    initSecurity()
    initNetwork()
    initProviders()
    initPlugins()
}
