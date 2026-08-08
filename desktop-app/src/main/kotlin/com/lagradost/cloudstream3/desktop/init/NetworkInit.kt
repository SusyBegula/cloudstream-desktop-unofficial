package com.lagradost.cloudstream3.desktop.init

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.cloudstream3.desktop.network.PlaywrightResolverImpl
import com.lagradost.cloudstream3.desktop.utils.appScope
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Initializes all network-related subsystems:
 * - Global NiceHttp clients (timeouts + HTTP/1.1)
 * - Jackson mapper VerifiedRepo fallback module
 * - WebViewResolver handler binding (Playwright)
 * - CookieManager stub binding (OkHttp CookieJar)
 */
fun initNetwork() {
    // Initialize global NiceHttp clients
    NetworkConfig.updateGlobalNetworkClients()

    // Patch Jackson mapper for dex2jar Kotlin reflection bugs
    val mapper = mapper

    // We register a custom deserializer for VerifiedRepo to bypass Jackson's
    // KotlinReflectionInternalError on dex2jar'd inner data classes.
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

                                try {
                                    val clazz = type.rawClass
                                    var instance: Any? = null

                                    val c = clazz.constructors.find { it.parameterCount == 2 } ?: clazz.constructors.firstOrNull()
                                    if (c != null) {
                                        instance = try {
                                            c.newInstance(name, url)
                                        } catch (e: Exception) {
                                            try {
                                                c.newInstance(url, name)
                                            } catch (e2: Exception) {
                                                null
                                            }
                                        }
                                    }

                                    if (instance == null) {
                                        AppLogger.e("FATAL: Could not instantiate VerifiedRepo via direct mapping!")
                                    }
                                    return instance
                                } catch (e: Exception) {
                                    AppLogger.e("VerifiedRepo deserialization failed", e)
                                    return null
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

    // Initialize WebViewResolver
    WebViewResolver.webViewHandler = { request, callback ->
        PlaywrightResolverImpl.resolve(request, callback)
    }

    // Bind the raw WebView stub to Playwright
    android.webkit.WebView.loadUrlHandler = java.util.function.Consumer { url ->
        appScope.launch {
            WebViewResolver.webViewHandler?.invoke(
                okhttp3.Request.Builder().url(url).build(),
            ) { true }
        }
    }

    // Bind the CookieManager stub to OkHttp CookieJar
    android.webkit.CookieManager.setCookieHandler = { url, value ->
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl != null) {
            val cookie = okhttp3.Cookie.parse(httpUrl, value)
            if (cookie != null) {
                app.baseClient.cookieJar.saveFromResponse(httpUrl, listOf(cookie))
            }
        }
    }

    android.webkit.CookieManager.getCookieHandler = { url ->
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl != null) {
            val cookies = app.baseClient.cookieJar.loadForRequest(httpUrl)
            if (cookies.isNotEmpty()) cookies.joinToString("; ") { "${it.name}=${it.value}" } else null
        } else {
            null
        }
    }
}
