package com.lagradost.cloudstream3.desktop.ui.screens.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class BrowseState(
    val filters: BrowseFilters = BrowseFilters(),
    val titles: List<BrowseTitle> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val totalResults: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val pageSize: Int = 24,
)

class BrowseViewModel(
    private val scope: CoroutineScope,
    initialFilters: BrowseFilters = BrowseFilters(),
    defaultPageSize: Int = 24,
    private val fetch: suspend (BrowseFilters, Int) -> BrowsePage = BrowseRepository()::discover,
) {
    private val mutableState = MutableStateFlow(BrowseState(filters = initialFilters, pageSize = defaultPageSize))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    // In-memory cache of fetched TMDB API pages: tmdbPage -> List<BrowseTitle>
    private val tmdbCache = ConcurrentHashMap<Int, List<BrowseTitle>>()
    private var highestFetchedTmdbPage = 0
    private var totalTmdbPages = 1
    private var totalTmdbResults = 0
    private var isLoaded = false
    private var pendingPage: Int? = null

    fun ensureLoaded() {
        if (!isLoaded && state.value.page == 0 && !state.value.loading && state.value.error == null) {
            loadPage(1)
        }
    }

    fun setPageSize(newSize: Int) {
        if (newSize <= 0 || newSize == state.value.pageSize) return
        val oldSize = state.value.pageSize
        val oldPage = state.value.page
        mutableState.value = state.value.copy(pageSize = newSize)
        if (isLoaded && oldPage > 0) {
            val firstItemIdx = (oldPage - 1) * oldSize
            val newPage = maxOf(1, (firstItemIdx / newSize) + 1)
            loadPage(newPage)
        }
    }

    fun setFilters(filters: BrowseFilters) {
        if (filters == state.value.filters) return
        job?.cancel()
        generation++
        tmdbCache.clear()
        highestFetchedTmdbPage = 0
        totalTmdbPages = 1
        totalTmdbResults = 0
        isLoaded = false
        pendingPage = null
        mutableState.value = BrowseState(filters = filters, pageSize = state.value.pageSize)
        loadPage(1)
    }

    fun retry() = loadPage(pendingPage ?: maxOf(1, state.value.page))

    fun nextPage() {
        if (state.value.page < state.value.totalPages) {
            loadPage(state.value.page + 1)
        }
    }

    fun prevPage() {
        if (state.value.page > 1) {
            loadPage(state.value.page - 1)
        }
    }

    fun loadMore() {
        if (state.value.page < state.value.totalPages) {
            loadPage(state.value.page + 1)
        }
    }

    private fun getAllLoadedTitles(): List<BrowseTitle> {
        val pages = tmdbCache.keys.sorted()
        return pages.flatMap { tmdbCache[it].orEmpty() }.distinctBy { it.key }
    }

    fun loadPage(targetPage: Int) {
        val before = state.value
        val pageSize = before.pageSize
        val target = maxOf(1, targetPage)
        pendingPage = target
        val requestGeneration = ++generation

        val startIdx = (target - 1) * pageSize
        val endIdx = target * pageSize

        val allLoaded = getAllLoadedTitles()

        // If we already have enough items loaded (or reached the end of the TMDB catalog), slice immediately
        if (isLoaded && (allLoaded.size >= endIdx || (highestFetchedTmdbPage >= totalTmdbPages && highestFetchedTmdbPage > 0))) {
            val effectiveTotal = if (totalTmdbResults > 0) minOf(totalTmdbResults, totalTmdbPages * 20) else allLoaded.size
            val totalUiPages = maxOf(1, (effectiveTotal + pageSize - 1) / pageSize)
            val clampedPage = target.coerceAtMost(totalUiPages)
            val finalStartIdx = (clampedPage - 1) * pageSize
            val pageTitles = allLoaded.drop(finalStartIdx).take(pageSize)
            pendingPage = null

            mutableState.value = before.copy(
                titles = pageTitles,
                page = clampedPage,
                totalPages = totalUiPages,
                totalResults = effectiveTotal,
                loading = false,
                error = null,
            )
            return
        }

        job?.cancel()
        mutableState.value = before.copy(loading = true, error = null)
        job = scope.launch {
            try {
                var currentLoaded = getAllLoadedTitles()
                var nextTmdbPage = highestFetchedTmdbPage + 1

                while (currentLoaded.size < endIdx && nextTmdbPage <= totalTmdbPages && requestGeneration == generation) {
                    val result = fetch(before.filters, nextTmdbPage)
                    tmdbCache[result.page] = result.titles
                    highestFetchedTmdbPage = maxOf(highestFetchedTmdbPage, result.page)
                    totalTmdbPages = result.totalPages
                    totalTmdbResults = result.totalResults
                    currentLoaded = getAllLoadedTitles()
                    nextTmdbPage = highestFetchedTmdbPage + 1
                    if (result.titles.isEmpty()) break
                }

                if (requestGeneration == generation) {
                    isLoaded = true
                    pendingPage = null
                    val effectiveTotal = if (totalTmdbResults > 0) minOf(totalTmdbResults, totalTmdbPages * 20) else currentLoaded.size
                    val totalUiPages = maxOf(1, (effectiveTotal + pageSize - 1) / pageSize)
                    val clampedPage = target.coerceAtMost(totalUiPages)
                    val finalStartIdx = (clampedPage - 1) * pageSize
                    val pageTitles = currentLoaded.drop(finalStartIdx).take(pageSize)

                    mutableState.value = before.copy(
                        titles = pageTitles,
                        page = clampedPage,
                        totalPages = totalUiPages,
                        totalResults = effectiveTotal,
                        loading = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == generation) {
                    mutableState.value = before.copy(
                        loading = false,
                        error = "Couldn’t load the TMDB catalog. Check your connection and try again.",
                    )
                }
            }
        }
    }
}
