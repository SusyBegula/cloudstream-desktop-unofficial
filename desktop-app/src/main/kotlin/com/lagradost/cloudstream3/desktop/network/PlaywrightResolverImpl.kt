package com.lagradost.cloudstream3.desktop.network

import com.lagradost.cloudstream3.desktop.utils.PlaywrightManager
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.Request

object PlaywrightResolverImpl {

    // Playwright Java API is NOT thread-safe. All browser operations must be serialized.
    private val playwrightMutex = Mutex()

    suspend fun resolve(request: Request, requestCallBack: (Request) -> Boolean): Pair<Request?, List<Request>> = withContext(Dispatchers.IO) {
        if (!PlaywrightManager.isInstalled.value) {
            PlaywrightManager.triggerPrompt()
            throw UnsupportedOperationException("WEBVIEW_NOT_INSTALLED")
        }

        // Serialize ALL Playwright access through a single mutex
        playwrightMutex.withLock {
            try {
                val browser = PlaywrightManager.getBrowser()
                val contextOptions = com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent(com.lagradost.cloudstream3.USER_AGENT)
                    .setViewportSize(1920, 1080)
                    .setPermissions(emptyList()) // Deny all
                    .setBypassCSP(true)
                    .setOffline(false)
                    .setIgnoreHTTPSErrors(true)
                val context = browser.newContext(contextOptions)

                // Hide navigator.webdriver and CDP cdc_ variables to avoid Cloudflare bot detection
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

                page.setDefaultNavigationTimeout(15000.0) // 15 seconds max to load
                page.setDefaultTimeout(15000.0) // 15 seconds max for any action

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

                        // Re-evaluate the callback with the original request so CloudflareKiller can check the updated cookies
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

                    delay(500) // Mutex.withLock safely suspends, keeping IO thread pool free
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
                } catch (_: Exception) {}
                try {
                    context.close()
                } catch (_: Exception) {}

                return@withContext finalOkHttpRequest to collectedRequests
            } catch (e: Exception) {
                AppLogger.e("Playwright resolution failed", e)
                // If the browser got into a bad state, reset it
                try {
                    PlaywrightManager.resetBrowser()
                } catch (_: Exception) {}
                return@withContext null to emptyList()
            }
        }
    }
}
