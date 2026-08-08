package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap

object GlobalDetailsCache {
    // Size-limited LRU Cache for the last 50 visited pages to prevent OutOfMemory errors
    val cache: MutableMap<String, LoadResponse> = Collections.synchronizedMap(
        object : LinkedHashMap<String, LoadResponse>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadResponse>?): Boolean {
                return size > 50
            }
        },
    )

    suspend fun fetchRaw(provider: MainAPI, url: String): LoadResponse? {
        val existing = cache[url]
        if (existing != null) return existing

        val loaded = withContext(Dispatchers.IO) {
            try {
                provider.load(url)
            } catch (e: Exception) {
                null
            }
        }

        if (loaded != null) {
            cache[url] = loaded
        }
        return loaded
    }

    suspend fun enrich(loaded: LoadResponse, url: String) {
        withContext(Dispatchers.IO) {
            try {
                val tmdb = object : com.lagradost.cloudstream3.metaproviders.TmdbProvider() {
                    override val useMetaLoadResponse = true
                }
                val cleanName = loaded.name
                    .replace(Regex("""\s*\(\d{4}\).*"""), "")
                    .replace(Regex("""\s*(?i)(dual audio|720p|1080p|480p|2160p|webrip|web-dl|hdtv|bluray).*"""), "")
                    .replace(Regex("""\s*[\[\{].*"""), "")
                    .trim()

                val searchResults = tmdb.search(cleanName, 1)?.items ?: emptyList()
                val strippedCleanName = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")

                val match = searchResults.firstOrNull { result ->
                    val resultYear = when (result) {
                        is MovieSearchResponse -> result.year
                        is TvSeriesSearchResponse -> result.year
                        else -> null
                    }
                    val strippedResultName = result.name.replace(Regex("[^a-zA-Z0-9]"), "")

                    if (strippedResultName.equals(strippedCleanName, ignoreCase = true) && strippedCleanName.isNotEmpty()) {
                        if (result is MovieSearchResponse && resultYear != null && loaded.year != null && resultYear != loaded.year) {
                            false // Year conflicts for Movie
                        } else {
                            true // Name matches and either it's not a movie or year doesn't conflict
                        }
                    } else {
                        false
                    }
                }

                if (match != null) {
                    val enriched = tmdb.load(match.url)
                    if (enriched != null) {
                        if (!enriched.backgroundPosterUrl.isNullOrBlank()) {
                            loaded.backgroundPosterUrl = enriched.backgroundPosterUrl?.replace("/w500/", "/original/")
                        } else if (!enriched.posterUrl.isNullOrBlank() && loaded.backgroundPosterUrl.isNullOrBlank()) {
                            loaded.backgroundPosterUrl = enriched.posterUrl?.replace("/w500/", "/original/")
                        }

                        if (!enriched.posterUrl.isNullOrBlank()) {
                            loaded.posterUrl = enriched.posterUrl
                        }

                        if (loaded.actors.isNullOrEmpty() || loaded.actors!!.all { it.actor.image == null }) {
                            if (!enriched.actors.isNullOrEmpty()) {
                                loaded.actors = enriched.actors
                            }
                        }

                        if (loaded.plot.isNullOrBlank()) loaded.plot = enriched.plot
                        if (loaded.tags.isNullOrEmpty()) loaded.tags = enriched.tags
                        if (loaded.duration == null || loaded.duration == 0) loaded.duration = enriched.duration
                        if (loaded.score == null) loaded.score = enriched.score
                    }
                }
            } catch (t: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error enriching TMDB data", t)
            }
        }
        cache[url] = loaded
    }
}

class DetailsViewModel(
    private val viewModelScope: CoroutineScope,
    private val provider: MainAPI,
    private val url: String,
    private val preloadedName: String? = null,
    private val preloadedPoster: String? = null,
    private val preloadedBg: String? = null,
) {

    private val _response = MutableStateFlow<LoadResponse?>(GlobalDetailsCache.cache[url])
    val response: StateFlow<LoadResponse?> = _response.asStateFlow()

    private val _enrichmentTrigger = MutableStateFlow(0)
    val enrichmentTrigger: StateFlow<Int> = _enrichmentTrigger.asStateFlow()

    private val _fakeData = MutableStateFlow<LoadResponse?>(null)
    val fakeData: StateFlow<LoadResponse?> = _fakeData.asStateFlow()

    private val _isLoading = MutableStateFlow(_response.value == null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeLinkData = MutableStateFlow<LinksPanelRequest?>(null)
    val activeLinkData: StateFlow<LinksPanelRequest?> = _activeLinkData.asStateFlow()

    private val _isPanelOpen = MutableStateFlow(false)
    val isPanelOpen: StateFlow<Boolean> = _isPanelOpen.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        if (_response.value != null) return

        viewModelScope.launch {
            if (preloadedName != null) {
                _fakeData.value = provider.newMovieLoadResponse(
                    name = preloadedName,
                    url = url,
                    type = TvType.Movie,
                    dataUrl = url,
                ) {
                    this.posterUrl = preloadedPoster
                    this.backgroundPosterUrl = preloadedBg
                }
            }

            try {
                val rawData = GlobalDetailsCache.fetchRaw(provider, url)
                _response.value = rawData
                _isLoading.value = false

                if (rawData != null) {
                    viewModelScope.launch {
                        GlobalDetailsCache.enrich(rawData, url)
                        // Trigger a recomposition since the rawData object is mutated in-place
                        _enrichmentTrigger.value += 1
                    }
                }
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading details", e)
                _errorMessage.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun openLinksPanel(data: LinksPanelRequest) {
        _activeLinkData.value = data
        _isPanelOpen.value = true
    }

    fun closeLinksPanel() {
        _isPanelOpen.value = false
    }
}
