package com.lagradost.cloudstream3.desktop.ui.screens.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

const val PREF_SELECTED_PROVIDER = "preferred_provider_name"
const val PREF_GLOBAL_SEARCH = "global_search_enabled"

/**
 * Returns true only for real, user-facing content providers:
 * - Excludes built-in MetaProviders (Trakt, TMDB, CrossTMDB)
 * - Excludes "NONE"
 */
fun MainAPI.isRealProvider(): Boolean {
    if (name == "NONE" || name == "None") return false
    if (providerType == com.lagradost.cloudstream3.ProviderType.MetaProvider) return false
    return true
}

class HomeViewModel(private val coroutineScope: CoroutineScope) {
    val providers = MutableStateFlow<List<MainAPI>>(emptyList())
    val selectedProviderName = MutableStateFlow<String?>(null)

    val selectedProvider: StateFlow<MainAPI?> = combine(providers, selectedProviderName) { provs, name ->
        provs.firstOrNull { it.name == name }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, null)

    val searchResultsGrouped = MutableStateFlow<List<Pair<MainAPI, List<SearchResponse>>>?>(null)
    val isLoadingSearch = MutableStateFlow(false)
    val isLoadingMore = MutableStateFlow(false)
    val canLoadMore = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null
    private var paginationJob: Job? = null
    private var searchGeneration = 0
    val searchQuery = MutableStateFlow("")
    val isGlobalSearchEnabled = MutableStateFlow(true)

    private data class ProviderSearchState(
        val provider: MainAPI,
        var currentPage: Int = 1,
        var hasNext: Boolean = false,
    )
    private val providerPagination = java.util.concurrent.ConcurrentHashMap<String, ProviderSearchState>()

    val historyList = MutableStateFlow<List<com.lagradost.common.storage.WatchHistory>>(emptyList())
    val mergedPluginIcons = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        // Initialize providers
        updateProviders()

        // Load saved provider preference
        val savedName = DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER)
        if (savedName != null && APIHolder.allProviders.any { it.name == savedName && it.isRealProvider() }) {
            selectedProviderName.value = savedName
        }

        // Save selected provider when it changes
        coroutineScope.launch {
            selectedProviderName.collect { name ->
                if (!name.isNullOrBlank()) {
                    DesktopDataStore.setKey(PREF_SELECTED_PROVIDER, name)
                } else {
                    DesktopDataStore.removeKey(PREF_SELECTED_PROVIDER)
                }
            }
        }

        isGlobalSearchEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_GLOBAL_SEARCH) ?: true

        // Poll for provider updates
        coroutineScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                updateProviders()
            }
        }

        // Clear obsolete results immediately; debounce only the next request.
        coroutineScope.launch {
            combine(searchQuery, selectedProvider, isGlobalSearchEnabled, providers) { query, provider, global, available ->
                val active = if (global) available else listOfNotNull(provider)
                Triple(query.trim(), active.map { it.name }, active)
            }.distinctUntilChanged { old, new ->
                old.first == new.first && old.second == new.second
            }.collectLatest { (query, _, activeProviders) ->
                searchJob?.cancel()
                paginationJob?.cancel()
                searchGeneration++
                searchResultsGrouped.value = null
                searchError.value = null
                canLoadMore.value = false
                isLoadingMore.value = false
                providerPagination.clear()
                isLoadingSearch.value = query.isNotBlank() && activeProviders.isNotEmpty()
                if (query.isNotBlank()) {
                    delay(500)
                    if (searchJob?.isActive != true && searchResultsGrouped.value == null) {
                        search()
                    }
                }
            }
        }

        // Listen for sync generation updates
        coroutineScope.launch {
            DesktopRepositoryManager.syncGeneration.collect { syncGen ->
                if (syncGen > 0) {
                    updateProviders()
                    reloadIcons()
                }
            }
        }

        // Handle history updates
        coroutineScope.launch {
            combine(selectedProvider, DesktopDataStore.historyUpdates) { _, _ -> }.collect {
                updateHistory()
            }
        }

        updateHistory()
        reloadIcons()
    }

    private fun updateProviders() {
        val currentProviders = APIHolder.allProviders.filter { it.isRealProvider() }
        if (currentProviders != providers.value) {
            providers.value = currentProviders
            if (currentProviders.none { it.name == selectedProviderName.value }) {
                val saved = DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER)
                selectedProviderName.value = currentProviders.firstOrNull { it.name == saved }?.name
                    ?: currentProviders.firstOrNull()?.name
            }
        }
    }

    fun selectProvider(name: String) {
        selectedProviderName.value = name
    }

    fun refreshSearchPreference() {
        isGlobalSearchEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_GLOBAL_SEARCH) ?: true
    }

    private fun updateHistory() {
        historyList.value = DesktopDataStore.getAllWatchHistory()
            .filter { it.duration >= 30L && (it.position * 100 / it.duration) > 1L }
            .sortedByDescending { it.updateTime }
            .distinctBy { it.parentId }
    }

    private fun reloadIcons() {
        coroutineScope.launch(Dispatchers.IO) {
            mergedPluginIcons.value = DesktopRepositoryManager.remotePluginIcons.value
        }
    }

    fun search() {
        searchJob?.cancel()
        paginationJob?.cancel()
        val generation = ++searchGeneration
        val query = searchQuery.value.trim()
        val activeProviders = if (isGlobalSearchEnabled.value) providers.value else listOfNotNull(selectedProvider.value)
        searchResultsGrouped.value = null
        searchError.value = null
        canLoadMore.value = false
        isLoadingMore.value = false
        providerPagination.clear()

        if (query.isBlank() || activeProviders.isEmpty()) {
            isLoadingSearch.value = false
            return
        }
        isLoadingSearch.value = true
        searchJob = coroutineScope.launch {
            val totalProviders = activeProviders.size
            val successfulResults = java.util.concurrent.ConcurrentHashMap<String, Pair<MainAPI, List<SearchResponse>>>()
            val failedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

            try {
                coroutineScope {
                    activeProviders.forEach { provider ->
                        launch(Dispatchers.IO) {
                            try {
                                val searchResponse = withTimeoutOrNull(45_000) {
                                    provider.search(query, 1)
                                }
                                if (searchResponse == null) {
                                    DesktopErrorReporter.report("Search provider ${provider.name} timed out or returned null", null)
                                    failedCount.incrementAndGet()
                                } else {
                                    val items = searchResponse.items
                                    val hasNext = searchResponse.hasNext && items.isNotEmpty()
                                    providerPagination[provider.name] = ProviderSearchState(provider, 1, hasNext)

                                    if (items.isNotEmpty()) {
                                        successfulResults[provider.name] = provider to items
                                        if (generation == searchGeneration) {
                                            synchronized(searchResultsGrouped) {
                                                val currentList = searchResultsGrouped.value.orEmpty()
                                                val existingIndex = currentList.indexOfFirst { it.first.name == provider.name }
                                                if (existingIndex >= 0) {
                                                    val updated = currentList.toMutableList()
                                                    updated[existingIndex] = provider to items
                                                    searchResultsGrouped.value = updated
                                                } else {
                                                    searchResultsGrouped.value = currentList + (provider to items)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                DesktopErrorReporter.report("Search provider ${provider.name} failed", e)
                                failedCount.incrementAndGet()
                            } finally {
                                val finished = completedCount.incrementAndGet()
                                if (finished == totalProviders && generation == searchGeneration) {
                                    canLoadMore.value = providerPagination.values.any { it.hasNext }
                                    if (successfulResults.isEmpty()) {
                                        searchResultsGrouped.value = emptyList()
                                        if (failedCount.get() == totalProviders) {
                                            searchError.value = "Search failed. Check your connection or try another provider."
                                        }
                                    } else if (failedCount.get() > 0) {
                                        searchError.value = "Some providers couldn’t be searched. Results may be incomplete."
                                    }
                                    isLoadingSearch.value = false
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (generation == searchGeneration) {
                    canLoadMore.value = providerPagination.values.any { it.hasNext }
                    isLoadingSearch.value = false
                }
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingSearch.value || isLoadingMore.value || !canLoadMore.value) return
        val currentSearch = searchJob
        if (currentSearch != null && !currentSearch.isCompleted) return

        val providersToLoad = providerPagination.values.filter { it.hasNext }
        if (providersToLoad.isEmpty()) {
            canLoadMore.value = false
            return
        }

        isLoadingMore.value = true
        val generation = searchGeneration
        val query = searchQuery.value.trim()

        paginationJob = coroutineScope.launch {
            try {
                coroutineScope {
                    providersToLoad.forEach { state ->
                        launch(Dispatchers.IO) {
                            try {
                                val nextPage = state.currentPage + 1
                                val searchResponse = withTimeoutOrNull(45_000) {
                                    state.provider.search(query, nextPage)
                                }
                                if (searchResponse != null) {
                                    val newItems = searchResponse.items
                                    state.currentPage = nextPage

                                    if (newItems.isNotEmpty() && generation == searchGeneration) {
                                        var addedAny = false
                                        synchronized(searchResultsGrouped) {
                                            val currentGrouped = searchResultsGrouped.value.orEmpty()
                                            val existingIndex = currentGrouped.indexOfFirst { it.first.name == state.provider.name }
                                            if (existingIndex >= 0) {
                                                val existingList = currentGrouped[existingIndex].second
                                                val existingUrls = existingList.map { it.url }.toSet()
                                                val distinctNew = newItems.filter { it.url !in existingUrls }
                                                if (distinctNew.isNotEmpty()) {
                                                    val updatedList = currentGrouped.toMutableList()
                                                    updatedList[existingIndex] = state.provider to (existingList + distinctNew)
                                                    searchResultsGrouped.value = updatedList
                                                    addedAny = true
                                                }
                                            } else {
                                                searchResultsGrouped.value = currentGrouped + (state.provider to newItems)
                                                addedAny = true
                                            }
                                        }
                                        state.hasNext = searchResponse.hasNext && addedAny
                                    } else {
                                        state.hasNext = false
                                    }
                                } else {
                                    state.hasNext = false
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                DesktopErrorReporter.report("Pagination for ${state.provider.name} failed", e)
                                state.hasNext = false
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (generation == searchGeneration) {
                    canLoadMore.value = providerPagination.values.any { it.hasNext }
                    isLoadingMore.value = false
                }
            }
        }
    }

    fun clearHistory() {
        DesktopDataStore.clearAllWatchHistory()
        historyList.value = emptyList()
    }

    fun removeHistoryItem(parentId: String) {
        DesktopDataStore.removeWatchHistory(parentId)
        updateHistory()
    }

}
