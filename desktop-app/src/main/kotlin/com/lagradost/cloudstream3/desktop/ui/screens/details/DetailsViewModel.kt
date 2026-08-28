package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashMap

fun normalizeEpisodeList(episodes: List<Episode>) {
    if (episodes.isEmpty()) return

    val jsonSeasonPattern = Regex("""(?i)["']season["']\s*:\s*(\d+)""")
    val jsonEpisodePattern = Regex("""(?i)["']episode["']\s*:\s*(\d+)""")
    val sPattern1 = Regex("""(?i)\bS(?:eason)?\s*(\d{1,2})\s*[-._ ]*\s*(?:E(?:pisode)?|x)\s*(\d{1,3})\b""")
    val sPattern2 = Regex("""(?i)\b(\d{1,2})x(\d{1,3})\b""")
    val sOnlyPattern = Regex("""(?i)[/_-]s(?:eason)?[-_]?(\d{1,2})[/_-]""")

    var currentSeason = 1
    var prevEpNumber = -1

    for (ep in episodes) {
        val titleText = ep.name ?: ""
        val urlText = ep.data
        val descText = ep.description ?: ""

        var parsedSeason: Int? = null
        var parsedEpisode: Int? = null

        // 1. Check JSON encoded in ep.data (common in CineStream / Multi-providers)
        if (urlText.startsWith("{") && urlText.endsWith("}")) {
            val jsonSeasonMatch = jsonSeasonPattern.find(urlText)
            val jsonEpisodeMatch = jsonEpisodePattern.find(urlText)
            if (jsonSeasonMatch != null) {
                parsedSeason = jsonSeasonMatch.groupValues[1].toIntOrNull()
            }
            if (jsonEpisodeMatch != null) {
                parsedEpisode = jsonEpisodeMatch.groupValues[1].toIntOrNull()
            }
        }

        // 2. Check title / data / description for standard SxxExx
        if (parsedSeason == null) {
            val match1 = sPattern1.find(titleText) ?: sPattern1.find(urlText) ?: sPattern1.find(descText)
            if (match1 != null) {
                parsedSeason = match1.groupValues[1].toIntOrNull()
                parsedEpisode = match1.groupValues[2].toIntOrNull()
            } else {
                val match2 = sPattern2.find(titleText) ?: sPattern2.find(urlText)
                if (match2 != null) {
                    parsedSeason = match2.groupValues[1].toIntOrNull()
                    parsedEpisode = match2.groupValues[2].toIntOrNull()
                } else {
                    val matchS = sOnlyPattern.find(urlText) ?: sOnlyPattern.find(titleText)
                    if (matchS != null) {
                        parsedSeason = matchS.groupValues[1].toIntOrNull()
                    }
                }
            }
        }

        if (ep.season != null && ep.season!! > 0) {
            currentSeason = ep.season!!
            if (parsedEpisode != null && parsedEpisode > 0 && (ep.episode == null || ep.episode == 0)) {
                ep.episode = parsedEpisode
            }
            prevEpNumber = ep.episode ?: -1
        } else if (parsedSeason != null && parsedSeason > 0) {
            ep.season = parsedSeason
            currentSeason = parsedSeason
            if (parsedEpisode != null && parsedEpisode > 0 && (ep.episode == null || ep.episode == 0)) {
                ep.episode = parsedEpisode
            }
            prevEpNumber = ep.episode ?: -1
        } else {
            val epNum = ep.episode
            if (epNum != null && epNum > 0) {
                if (prevEpNumber != -1 && epNum <= prevEpNumber) {
                    currentSeason++
                }
                prevEpNumber = epNum
            }
            ep.season = currentSeason
        }
    }
}

fun normalizeTvSeriesEpisodes(data: TvSeriesLoadResponse) {
    normalizeEpisodeList(data.episodes)
}

fun normalizeAnimeEpisodes(data: AnimeLoadResponse) {
    data.episodes.values.forEach { list ->
        normalizeEpisodeList(list)
    }
}

fun normalizeLoadResponseEpisodes(data: LoadResponse) {
    when (data) {
        is TvSeriesLoadResponse -> normalizeTvSeriesEpisodes(data)
        is AnimeLoadResponse -> normalizeAnimeEpisodes(data)
        else -> {}
    }
}

object GlobalDetailsCache {
    // Size-limited LRU Cache for the last 50 visited pages to prevent OutOfMemory errors
    val cache: MutableMap<String, LoadResponse> = Collections.synchronizedMap(
        object : LinkedHashMap<String, LoadResponse>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadResponse>?): Boolean {
                return size > 50
            }
        },
    )

    data class TmdbSeasonRange(val seasonNum: Int, val startAbsoluteEp: Int, val endAbsoluteEp: Int, val epCount: Int)
    data class TmdbEpisodeInfo(
        val season: Int,
        val episode: Int,
        val name: String?,
        val stillUrl: String?,
        val desc: String?,
        val runTime: Int?,
    )

    class TmdbShowContext(
        val tmdbId: Int,
        val isTv: Boolean,
        val seasonRanges: List<TmdbSeasonRange>,
        val cachedSeasons: MutableMap<Int, List<TmdbEpisodeInfo>> = Collections.synchronizedMap(mutableMapOf()),
    )

    val tmdbContexts: MutableMap<String, TmdbShowContext> = Collections.synchronizedMap(mutableMapOf())

    suspend fun fetchRaw(provider: MainAPI, url: String): LoadResponse? {
        val existing = cache[url]
        if (existing != null) {
            normalizeLoadResponseEpisodes(existing)
            return existing
        }

        val loaded = withContext(Dispatchers.IO) {
            try {
                provider.load(url)
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading raw details", e)
                null
            }
        }

        if (loaded != null) {
            normalizeLoadResponseEpisodes(loaded)
            cache[url] = loaded
        }
        return loaded
    }

    suspend fun enrich(loaded: LoadResponse, url: String) {
        withContext(Dispatchers.IO) {
            try {
                val tmdbApiKey = "e6333b32409e02a4a6eba6fb7ff866bb"

                fun cleanTitle(s: String): String = s
                    .replace(Regex("""\s*\(\d{4}\).*"""), "")
                    .replace(Regex("""\s*(?i)(season\s*\d+|s\d+|dual audio|dubbed|subbed|720p|1080p|480p|2160p|webrip|web-dl|hdtv|bluray).*"""), "")
                    .replace(Regex("""\s*[\[\{].*"""), "")
                    .replace(":", " ")
                    .trim()

                fun normalizeForMatch(str: String): String {
                    return java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
                        .replace(Regex("\\p{M}"), "")
                        .replace(Regex("[^a-zA-Z0-9]"), "")
                        .lowercase()
                }

                val cleanName = cleanTitle(loaded.name)
                val encodedQuery = java.net.URLEncoder.encode(cleanName, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/multi?query=$encodedQuery&api_key=$tmdbApiKey"
                val searchJsonStr = try {
                    com.lagradost.cloudstream3.app.get(searchUrl).text
                } catch (e: Exception) {
                    val encodedRaw = java.net.URLEncoder.encode(loaded.name.trim(), "UTF-8")
                    com.lagradost.cloudstream3.app.get("https://api.themoviedb.org/3/search/multi?query=$encodedRaw&api_key=$tmdbApiKey").text
                }

                val searchObj = JSONObject(searchJsonStr)
                val resultsArr = searchObj.optJSONArray("results") ?: org.json.JSONArray()
                val normClean = normalizeForMatch(cleanName)

                val isLoadedAnime = loaded is AnimeLoadResponse || loaded.type == TvType.Anime || loaded.type == TvType.AnimeMovie ||
                    loaded.tags?.any { it.contains("anime", ignoreCase = true) || it.contains("animation", ignoreCase = true) } == true ||
                    loaded.name.contains("anime", ignoreCase = true)

                val isLoadedTv = loaded is TvSeriesLoadResponse || loaded is AnimeLoadResponse || loaded.type == TvType.TvSeries || loaded.type == TvType.Anime
                val loadedYear = loaded.year ?: Regex("""\((\d{4})\)""").find(loaded.name)?.groupValues?.get(1)?.toIntOrNull()

                var matchedId: Int? = null
                var matchedMediaType: String? = null
                var bestScore = -10000

                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.getJSONObject(i)
                    val mediaType = item.optString("media_type", "")
                    if (mediaType != "tv" && mediaType != "movie") continue

                    val title = item.optString("name").ifBlank { item.optString("title", "") }
                    val normTitle = normalizeForMatch(title)
                    val yearStr = item.optString("first_air_date").ifBlank { item.optString("release_date", "") }
                    val itemYear = yearStr.take(4).toIntOrNull()
                    val genreIds = item.optJSONArray("genre_ids")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) } } ?: emptyList()
                    val isAnim = genreIds.contains(16) || item.optString("original_language") == "ja"
                    val pop = item.optDouble("popularity", 0.0)

                    var score = 0
                    val isExact = normTitle == normClean ||
                        normTitle.replace("shippuuden", "shippuden") == normClean.replace("shippuuden", "shippuden")

                    if (isExact) {
                        score += 300
                    } else if (normTitle.startsWith(normClean) || normClean.startsWith(normTitle)) {
                        score += 100 - minOf(kotlin.math.abs(normTitle.length - normClean.length) * 2, 60)
                    } else if (normTitle.contains(normClean) || normClean.contains(normTitle)) {
                        score += 60 - minOf(kotlin.math.abs(normTitle.length - normClean.length) * 2, 40)
                    } else {
                        continue
                    }

                    if (isLoadedTv && mediaType == "tv") {
                        score += 50
                    } else if (!isLoadedTv && mediaType == "movie") {
                        score += 50
                    } else {
                        score -= 30
                    }

                    if (isLoadedAnime) {
                        if (isAnim) score += 100 else score -= 150
                    } else {
                        if (isAnim) score -= 120 else score += 30
                    }

                    if (loadedYear != null && itemYear != null) {
                        if (loadedYear == itemYear) {
                            score += 50
                        } else {
                            val diff = kotlin.math.abs(loadedYear - itemYear)
                            val maxPenalty = if (isExact && isLoadedTv) 30 else 60
                            score -= minOf(diff * 4, maxPenalty)
                        }
                    }

                    score += minOf(pop.toInt(), 50)

                    if (score > bestScore) {
                        bestScore = score
                        matchedId = item.optInt("id", 0).takeIf { it > 0 }
                        matchedMediaType = mediaType
                    }
                }

                if (matchedId != null) {
                    val isTv = matchedMediaType == "tv" || loaded is TvSeriesLoadResponse || loaded is AnimeLoadResponse

                    if (isTv) {
                        val showUrl = "https://api.themoviedb.org/3/tv/$matchedId?api_key=$tmdbApiKey&append_to_response=credits,keywords"
                        val showJson = JSONObject(com.lagradost.cloudstream3.app.get(showUrl).text)

                        val backdropPath = showJson.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
                        val posterPath = showJson.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                        val plot = showJson.optString("overview").takeIf { it.isNotBlank() && it != "null" }
                        val score = showJson.optDouble("vote_average", 0.0).takeIf { it > 0 }

                        if (!backdropPath.isNullOrBlank()) {
                            loaded.backgroundPosterUrl = "https://image.tmdb.org/t/p/original$backdropPath"
                        } else if (!posterPath.isNullOrBlank() && loaded.backgroundPosterUrl.isNullOrBlank()) {
                            loaded.backgroundPosterUrl = "https://image.tmdb.org/t/p/original$posterPath"
                        }

                        if (!posterPath.isNullOrBlank()) {
                            loaded.posterUrl = "https://image.tmdb.org/t/p/w500$posterPath"
                        }

                        if (plot != null && loaded.plot.isNullOrBlank()) {
                            loaded.plot = plot
                        }

                        if (score != null && loaded.score == null) {
                            loaded.score = com.lagradost.cloudstream3.Score.from10(score.toFloat())
                        }

                        // Parse cast
                        val credits = showJson.optJSONObject("credits")
                        val castArr = credits?.optJSONArray("cast")
                        if (castArr != null && castArr.length() > 0 && (loaded.actors.isNullOrEmpty() || loaded.actors!!.all { it.actor.image == null })) {
                            val actorList = mutableListOf<com.lagradost.cloudstream3.ActorData>()
                            for (i in 0 until minOf(castArr.length(), 20)) {
                                val c = castArr.getJSONObject(i)
                                val actorName = c.optString("name", "")
                                val profilePath = c.optString("profile_path").takeIf { it.isNotBlank() && it != "null" }
                                val character = c.optString("character").takeIf { it.isNotBlank() && it != "null" }
                                if (actorName.isNotBlank()) {
                                    val profileUrl = profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }
                                    actorList.add(
                                        com.lagradost.cloudstream3.ActorData(
                                            actor = com.lagradost.cloudstream3.Actor(actorName, profileUrl),
                                            roleString = character,
                                        ),
                                    )
                                }
                            }
                            if (actorList.isNotEmpty()) {
                                loaded.actors = actorList
                            }
                        }

                        // Build season ranges
                        val seasonsArr = showJson.optJSONArray("seasons")
                        val seasonRanges = mutableListOf<TmdbSeasonRange>()
                        var cumEp = 0
                        if (seasonsArr != null) {
                            for (i in 0 until seasonsArr.length()) {
                                val sObj = seasonsArr.getJSONObject(i)
                                val sNum = sObj.optInt("season_number", 0)
                                val epCount = sObj.optInt("episode_count", 0)
                                if (sNum > 0 && epCount > 0) {
                                    seasonRanges.add(TmdbSeasonRange(sNum, cumEp + 1, cumEp + epCount, epCount))
                                    cumEp += epCount
                                }
                            }
                        }

                        val showContext = TmdbShowContext(matchedId, isTv = true, seasonRanges)
                        tmdbContexts[url] = showContext

                        // Eagerly fetch Season 1 only for instant initial page loading
                        try {
                            val s1JsonStr = com.lagradost.cloudstream3.app.get("https://api.themoviedb.org/3/tv/$matchedId/season/1?api_key=$tmdbApiKey").text
                            val s1Obj = JSONObject(s1JsonStr)
                            val epsArr = s1Obj.optJSONArray("episodes")
                            if (epsArr != null) {
                                val s1List = mutableListOf<TmdbEpisodeInfo>()
                                for (j in 0 until epsArr.length()) {
                                    val epObj = epsArr.getJSONObject(j)
                                    val stillPath = epObj.optString("still_path").takeIf { it.isNotBlank() && it != "null" }
                                    s1List.add(
                                        TmdbEpisodeInfo(
                                            season = 1,
                                            episode = epObj.optInt("episode_number", j + 1),
                                            name = epObj.optString("name").takeIf { it.isNotBlank() && it != "null" },
                                            stillUrl = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                                            desc = epObj.optString("overview").takeIf { it.isNotBlank() && it != "null" },
                                            runTime = epObj.optInt("runtime", 0).takeIf { it > 0 },
                                        ),
                                    )
                                }
                                showContext.cachedSeasons[1] = s1List
                            }
                        } catch (e: Throwable) {
                            com.lagradost.common.logging.AppLogger.e("Error preloading Season 1", e)
                        }

                        // Apply Season 1 metadata to initial episodes (strictly only for Season 1)
                        val targetEpisodes = when (loaded) {
                            is TvSeriesLoadResponse -> loaded.episodes
                            is AnimeLoadResponse -> loaded.episodes.values.flatten()
                            else -> emptyList()
                        }
                        val s1Eps = showContext.cachedSeasons[1] ?: emptyList()
                        if (s1Eps.isNotEmpty()) {
                            for (ep in targetEpisodes) {
                                if ((ep.season ?: 1) != 1) continue
                                val epNum = ep.episode ?: continue
                                if (epNum in 1..s1Eps.size) {
                                    val match = s1Eps.getOrNull(epNum - 1)
                                    if (match != null) {
                                        if (!match.name.isNullOrBlank()) ep.name = match.name
                                        if (!match.desc.isNullOrBlank()) ep.description = match.desc
                                        if (!match.stillUrl.isNullOrBlank()) ep.posterUrl = match.stillUrl
                                        if (match.runTime != null && match.runTime > 0) ep.runTime = match.runTime
                                    }
                                }
                            }
                        }
                    } else {
                        // Movie enrichment
                        val movieUrl = "https://api.themoviedb.org/3/movie/$matchedId?api_key=$tmdbApiKey&append_to_response=credits"
                        val movieJson = JSONObject(com.lagradost.cloudstream3.app.get(movieUrl).text)

                        val backdropPath = movieJson.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
                        val posterPath = movieJson.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                        val plot = movieJson.optString("overview").takeIf { it.isNotBlank() && it != "null" }
                        val score = movieJson.optDouble("vote_average", 0.0).takeIf { it > 0 }
                        val runtime = movieJson.optInt("runtime", 0).takeIf { it > 0 }

                        if (!backdropPath.isNullOrBlank()) {
                            loaded.backgroundPosterUrl = "https://image.tmdb.org/t/p/original$backdropPath"
                        } else if (!posterPath.isNullOrBlank() && loaded.backgroundPosterUrl.isNullOrBlank()) {
                            loaded.backgroundPosterUrl = "https://image.tmdb.org/t/p/original$posterPath"
                        }

                        if (!posterPath.isNullOrBlank()) {
                            loaded.posterUrl = "https://image.tmdb.org/t/p/w500$posterPath"
                        }

                        if (plot != null && loaded.plot.isNullOrBlank()) loaded.plot = plot
                        if (score != null && loaded.score == null) loaded.score = com.lagradost.cloudstream3.Score.from10(score.toFloat())
                        if (runtime != null && (loaded.duration == null || loaded.duration == 0)) loaded.duration = runtime
                    }
                }
            } catch (t: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error enriching TMDB data", t)
            }
        }
        cache[url] = loaded
    }

    suspend fun enrichEpisodes(loaded: LoadResponse, url: String, visibleEpisodes: List<Episode>): Boolean {
        if (visibleEpisodes.isEmpty()) return false
        val context = tmdbContexts[url] ?: return false
        if (!context.isTv) return false

        val tmdbApiKey = "e6333b32409e02a4a6eba6fb7ff866bb"

        val hasMultipleSeasons = when (loaded) {
            is TvSeriesLoadResponse -> loaded.episodes.mapNotNull { it.season }.distinct().size > 1
            is AnimeLoadResponse -> loaded.episodes.values.flatten().mapNotNull { it.season }.distinct().size > 1
            else -> false
        }

        fun resolveEpisodeTarget(ep: Episode): Pair<Int, Int>? {
            val s = ep.season ?: 1
            val epNum = ep.episode ?: return null

            if (hasMultipleSeasons) {
                val sRange = context.seasonRanges.find { it.seasonNum == s }
                val epInSeason = if (sRange != null && epNum >= sRange.startAbsoluteEp && epNum <= sRange.endAbsoluteEp && sRange.startAbsoluteEp > 1) {
                    epNum - sRange.startAbsoluteEp + 1
                } else {
                    epNum
                }
                return Pair(s, epInSeason)
            } else {
                if (context.seasonRanges.isNotEmpty()) {
                    val r = context.seasonRanges.find { epNum in it.startAbsoluteEp..it.endAbsoluteEp }
                    if (r != null) {
                        return Pair(r.seasonNum, epNum - r.startAbsoluteEp + 1)
                    }
                }
                return Pair(s, epNum)
            }
        }

        val seasonsToFetch = mutableSetOf<Int>()
        for (ep in visibleEpisodes) {
            val target = resolveEpisodeTarget(ep) ?: continue
            val targetSeason = target.first
            if (!context.cachedSeasons.containsKey(targetSeason)) {
                seasonsToFetch.add(targetSeason)
            }
        }

        if (seasonsToFetch.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    seasonsToFetch.map { sNum ->
                        async(Dispatchers.IO) {
                            try {
                                val sJsonStr = com.lagradost.cloudstream3.app.get("https://api.themoviedb.org/3/tv/${context.tmdbId}/season/$sNum?api_key=$tmdbApiKey").text
                                val epList = mutableListOf<TmdbEpisodeInfo>()
                                val epsArr = JSONObject(sJsonStr).optJSONArray("episodes") ?: return@async
                                for (j in 0 until epsArr.length()) {
                                    val epObj = epsArr.getJSONObject(j)
                                    val stillPath = epObj.optString("still_path").takeIf { it.isNotBlank() && it != "null" }
                                    epList.add(
                                        TmdbEpisodeInfo(
                                            season = epObj.optInt("season_number", sNum),
                                            episode = epObj.optInt("episode_number", j + 1),
                                            name = epObj.optString("name").takeIf { it.isNotBlank() && it != "null" },
                                            stillUrl = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                                            desc = epObj.optString("overview").takeIf { it.isNotBlank() && it != "null" },
                                            runTime = epObj.optInt("runtime", 0).takeIf { it > 0 },
                                        ),
                                    )
                                }
                                context.cachedSeasons[sNum] = epList
                            } catch (e: Throwable) {
                                com.lagradost.common.logging.AppLogger.e("Error fetching TMDB season $sNum", e)
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        var anyUpdated = false
        for (ep in visibleEpisodes) {
            val target = resolveEpisodeTarget(ep) ?: continue
            val (targetSeason, epInSeason) = target

            val seasonEps = context.cachedSeasons[targetSeason]
            val resolved = seasonEps?.find { it.episode == epInSeason }
                ?: seasonEps?.getOrNull(epInSeason - 1)

            if (resolved != null) {
                if (!resolved.name.isNullOrBlank() && ep.name != resolved.name) {
                    ep.name = resolved.name
                    anyUpdated = true
                }
                if (!resolved.desc.isNullOrBlank() && ep.description != resolved.desc) {
                    ep.description = resolved.desc
                    anyUpdated = true
                }
                if (!resolved.stillUrl.isNullOrBlank() && ep.posterUrl != resolved.stillUrl) {
                    ep.posterUrl = resolved.stillUrl
                    anyUpdated = true
                }
                if (resolved.runTime != null && resolved.runTime > 0 && ep.runTime != resolved.runTime) {
                    ep.runTime = resolved.runTime
                    anyUpdated = true
                }
            }
        }

        return anyUpdated
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
        viewModelScope.launch {
            if (preloadedName != null && _response.value == null) {
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
                val rawData = _response.value ?: GlobalDetailsCache.fetchRaw(provider, url)
                if (_response.value == null) {
                    _response.value = rawData
                    _isLoading.value = false
                }

                if (rawData != null) {
                    viewModelScope.launch {
                        GlobalDetailsCache.enrich(rawData, url)
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

    fun enrichVisibleEpisodes(visibleEpisodes: List<Episode>) {
        viewModelScope.launch {
            val currentLoaded = _response.value ?: return@launch
            val updated = GlobalDetailsCache.enrichEpisodes(currentLoaded, url, visibleEpisodes)
            if (updated) {
                _enrichmentTrigger.value += 1
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
