package com.lagradost.webclient.client

import com.lagradost.webclient.api.ActionResult
import com.lagradost.webclient.api.AggregatedSearchResponse
import com.lagradost.webclient.api.BookmarkDto
import com.lagradost.webclient.api.BookmarksResponse
import com.lagradost.webclient.api.ExtractorLinkDto
import com.lagradost.webclient.api.HomePageResponseDto
import com.lagradost.webclient.api.InstallPluginRequest
import com.lagradost.webclient.api.InstalledPluginsResponse
import com.lagradost.webclient.api.LinkEventDto
import com.lagradost.webclient.api.LoadRequest
import com.lagradost.webclient.api.LoadResponseDto
import com.lagradost.webclient.api.PlayableStreamDto
import com.lagradost.webclient.api.PluginCatalogResponse
import com.lagradost.webclient.api.ProviderListResponse
import com.lagradost.webclient.api.RepositoryDto
import com.lagradost.webclient.api.ResolveRequest
import com.lagradost.webclient.api.UninstallPluginRequest
import com.lagradost.webclient.api.WatchHistoryEntryDto
import com.lagradost.webclient.api.WatchHistoryResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Talks to :server-app's HTTP/WebSocket API. Never talks to :library directly — the browser build has no such dependency. */
class ApiClient(private val baseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val http = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
    }

    suspend fun getProviders(): ProviderListResponse =
        http.get("$baseUrl/api/providers").body()

    suspend fun getMainPage(provider: String, page: Int = 1): HomePageResponseDto =
        http.get("$baseUrl/api/providers/$provider/main-page") {
            url { parameters.append("page", page.toString()) }
        }.body()

    suspend fun search(query: String, provider: String? = null, global: Boolean = false): AggregatedSearchResponse =
        http.get("$baseUrl/api/search") {
            url {
                parameters.append("q", query)
                provider?.let { parameters.append("provider", it) }
                if (global) parameters.append("global", "true")
            }
        }.body()

    suspend fun load(provider: String, url: String): LoadResponseDto =
        http.post("$baseUrl/api/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadRequest(provider, url))
        }.body()

    suspend fun resolve(link: ExtractorLinkDto): PlayableStreamDto =
        http.post("$baseUrl/api/resolve") {
            contentType(ContentType.Application.Json)
            setBody(ResolveRequest(link))
        }.body()

    suspend fun getHistory(): WatchHistoryResponse =
        http.get("$baseUrl/api/history").body()

    suspend fun patchHistory(entry: WatchHistoryEntryDto) {
        http.patch("$baseUrl/api/history") {
            contentType(ContentType.Application.Json)
            setBody(entry)
        }
    }

    suspend fun deleteHistory(entry: WatchHistoryEntryDto) {
        http.delete("$baseUrl/api/history/item") {
            url {
                parameters.append("provider", entry.provider)
                parameters.append("url", entry.url)
                entry.season?.let { parameters.append("season", it.toString()) }
                entry.episode?.let { parameters.append("episode", it.toString()) }
                entry.episodeData?.let { parameters.append("episodeData", it) }
            }
        }
    }

    suspend fun clearHistory() {
        http.delete("$baseUrl/api/history")
    }

    suspend fun getBookmarks(): BookmarksResponse =
        http.get("$baseUrl/api/bookmarks").body()

    suspend fun putBookmark(bookmark: BookmarkDto) {
        http.put("$baseUrl/api/bookmarks") {
            contentType(ContentType.Application.Json)
            setBody(bookmark)
        }
    }

    suspend fun deleteBookmark(provider: String, url: String) {
        http.delete("$baseUrl/api/bookmarks") {
            url {
                parameters.append("provider", provider)
                parameters.append("url", url)
            }
        }
    }

    suspend fun getRepositories(): List<RepositoryDto> =
        http.get("$baseUrl/api/plugins/repositories").body()

    suspend fun addRepository(repo: RepositoryDto) {
        http.post("$baseUrl/api/plugins/repositories") {
            contentType(ContentType.Application.Json)
            setBody(repo)
        }
    }

    suspend fun removeRepository(url: String) {
        http.delete("$baseUrl/api/plugins/repositories") { url { parameters.append("url", url) } }
    }

    suspend fun getPluginCatalog(repoUrl: String): PluginCatalogResponse =
        http.get("$baseUrl/api/plugins/catalog") { url { parameters.append("repo", repoUrl) } }.body()

    suspend fun getInstalledPlugins(): InstalledPluginsResponse =
        http.get("$baseUrl/api/plugins/installed").body()

    suspend fun installPlugin(request: InstallPluginRequest): ActionResult =
        http.post("$baseUrl/api/plugins/install") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun uninstallPlugin(request: UninstallPluginRequest): ActionResult =
        http.post("$baseUrl/api/plugins/uninstall") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Streams resolved links/subtitles as MainAPI.loadLinks discovers them server-side. */
    @OptIn(DelicateCoroutinesApi::class)
    fun links(provider: String, data: String): Flow<LinkEventDto> = callbackFlow {
        val wsUrl = baseUrl.replaceFirst("http", "ws")
        val job = GlobalScope.launch {
            try {
                val encodedProvider = provider.encodeURLParameter()
                val encodedData = data.encodeURLParameter()
                http.webSocket("$wsUrl/api/links?provider=$encodedProvider&data=$encodedData") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val event = json.decodeFromString(LinkEventDto.serializer(), frame.readText())
                            trySend(event)
                            if (event.kind == "done" || event.kind == "error") break
                        }
                    }
                }
            } finally {
                close()
            }
        }
        awaitClose { job.cancel() }
    }
}
