package com.lagradost.webclient.client

import com.lagradost.webclient.api.HomePageResponseDto
import com.lagradost.webclient.api.ProviderDto
import com.lagradost.webclient.api.SearchResultDto
import com.lagradost.webclient.api.WatchHistoryEntryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Web-client counterpart of desktop-app's HomeViewModel, backed by ApiClient instead of MainAPI/APIHolder directly. */
class HomeViewModel(private val api: ApiClient, private val scope: CoroutineScope) {
    private val _providers = MutableStateFlow<List<ProviderDto>>(emptyList())
    val providers: StateFlow<List<ProviderDto>> = _providers.asStateFlow()

    private val _selectedProviderName = MutableStateFlow<String?>(null)
    val selectedProviderName: StateFlow<String?> = _selectedProviderName.asStateFlow()

    private val _homePage = MutableStateFlow<HomePageResponseDto?>(null)
    val homePage: StateFlow<HomePageResponseDto?> = _homePage.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultDto>>(emptyList())
    val searchResults: StateFlow<List<SearchResultDto>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _history = MutableStateFlow<List<WatchHistoryEntryDto>>(emptyList())
    val history: StateFlow<List<WatchHistoryEntryDto>> = _history.asStateFlow()

    init {
        refreshProviders()
    }

    fun refreshHistory() {
        scope.launch(Dispatchers.Default) {
            _history.value = runCatching { api.getHistory().entries.sortedByDescending { it.updatedAt } }.getOrDefault(emptyList())
        }
    }

    fun removeHistory(entry: WatchHistoryEntryDto) {
        _history.value = _history.value.filterNot { it == entry }
        scope.launch(Dispatchers.Default) {
            runCatching { api.deleteHistory(entry) }
        }
    }

    fun refreshProviders() {
        scope.launch(Dispatchers.Default) {
            _isLoading.value = true
            try {
                _providers.value = api.getProviders().providers
                _providers.value.firstOrNull()?.let { selectProvider(it.name) }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectProvider(name: String) {
        _selectedProviderName.value = name
        scope.launch(Dispatchers.Default) {
            _isLoading.value = true
            try {
                _homePage.value = api.getMainPage(name)
            } catch (e: Exception) {
                _homePage.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String, global: Boolean = false) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        scope.launch(Dispatchers.Default) {
            _isLoading.value = true
            try {
                val response = api.search(query, provider = selectedProviderName.value?.takeUnless { global }, global = global)
                _searchResults.value = response.perProvider.flatMap { it.results }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
