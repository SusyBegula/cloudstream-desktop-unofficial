package com.lagradost.webclient.server.network

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.Request

/**
 * Server-side counterpart of desktop-app's PlaywrightResolverImpl (Cloudflare bypass driver).
 * Logic is unchanged; kept as a separate copy per the "leave desktop-app untouched" decision.
 */
object PlaywrightResolverImpl {

    // Playwright Java API is NOT thread-safe. All browser operations must be serialized.
    private val playwrightMutex = Mutex()

    suspend fun resolve(request: Request, requestCallBack: (Request) -> Boolean): Pair<Request?, List<Request>> = withContext(Dispatchers.IO) {
        playwrightMutex.withLock {
            try {
                val browser = PlaywrightManager.getBrowser()
                val contextOptions = com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent(com.lagradost.cloudstream3.USER_AGENT)
                    .setViewportSize(1920, 1080)
                    .setPermissions(emptyList())
                    .setBypassCSP(true)
                    .setOffline(false)
                    .setIgnoreHTTPSErrors(true)
                val context = browser.newContext(contextOptions)

                context.addInitScript(
                    """
                    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                    try {
                        for (let prop in window) {
                            if (prop.startsWith('cdc_')) delete window[prop];
                        }
                    } catch(e) {}
                    """.trimIndent(),
                )

                val page = context.newPage()

                page.setDefaultNavigationTimeout(15000.0)
                page.setDefaultTimeout(15000.0)

                page.route("**/*") { route ->
                    val type = route.request().resourceType()
                    if (type == "image" || type == "media" || type == "font" || type == "stylesheet") {
                        route.abort()
                    } else {
                        route.resume()
                    }
                }

                val collectedRequests = mutableListOf<Request>()
                var finalRequest: Request? = null
                var shouldExit = false

                page.onRequest { pwRequest ->
                    val url = pwRequest.url()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) return@onRequest

                    val method = pwRequest.method().uppercase()
                    val body = if (method == "POST" || method == "PUT" || method == "PATCH") {
                        val bytes = pwRequest.postData()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                        @Suppress("DEPRECATION")
                        okhttp3.RequestBody.create(null, bytes)
                    } else {
                        null
                    }

                    val okHttpRequest = Request.Builder()
                        .url(url)
                        .method(method, body)
                        .headers(
                            Headers.Builder().apply {
                                pwRequest.headers().forEach { (k, v) -> add(k, v) }
                            }.build(),
                        )
                        .build()

                    if (requestCallBack(okHttpRequest)) {
                        collectedRequests.add(okHttpRequest)
                        shouldExit = true
                    }

                    if (url == request.url.toString()) {
                        finalRequest = okHttpRequest
                    }
                }

                try {
                    page.navigate(request.url.toString())
                } catch (e: com.microsoft.playwright.TimeoutError) {
                    AppLogger.w("Playwright timed out loading ${request.url}")
                }

                // Poll for cf_clearance cookie or callback exit, up to 15 seconds
                for (i in 1..30) {
                    if (shouldExit) break

                    try {
                        val currentCookies = context.cookies()
                        val httpUrl = request.url
                        val okHttpCookies = currentCookies.mapNotNull { pwCookie ->
                            okhttp3.Cookie.Builder()
                                .name(pwCookie.name)
                                .value(pwCookie.value)
                                .domain(pwCookie.domain.removePrefix("."))
                                .path(pwCookie.path)
                                .apply { if (pwCookie.secure) secure() }
                                .apply { if (pwCookie.httpOnly) httpOnly() }
                                .build()
                        }
                        com.lagradost.cloudstream3.app.baseClient.cookieJar.saveFromResponse(httpUrl, okHttpCookies)

                        if (requestCallBack(request)) {
                            shouldExit = true
                            break
                        }

                        if (currentCookies.any { it.name == "cf_clearance" }) {
                            shouldExit = true
                            break
                        }
                    } catch (_: Exception) {
                        break
                    }

                    delay(500)
                }

                val cookies = try {
                    context.cookies()
                } catch (_: Exception) {
                    emptyList()
                }
                val cookieString = cookies.joinToString("; ") { "${it.name}=${it.value}" }

                val finalOkHttpRequest = (finalRequest ?: request).newBuilder()
                    .header("cookie", cookieString)
                    .build()

                try {
                    page.close()
                } catch (_: Exception) {
                }
                try {
                    context.close()
                } catch (_: Exception) {
                }

                return@withContext finalOkHttpRequest to collectedRequests
            } catch (e: Exception) {
                AppLogger.e("Playwright resolution failed", e)
                try {
                    PlaywrightManager.resetBrowser()
                } catch (_: Exception) {
                }
                return@withContext null to emptyList()
            }
        }
    }
}
