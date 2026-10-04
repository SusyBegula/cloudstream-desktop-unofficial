package com.lagradost.cloudstream3.desktop.ui.screens.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseState(
    val filters: BrowseFilters = BrowseFilters(),
    val titles: List<BrowseTitle> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val totalResults: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

class BrowseViewModel(
    private val scope: CoroutineScope,
    initialFilters: BrowseFilters = BrowseFilters(),
    private val fetch: suspend (BrowseFilters, Int) -> BrowsePage = BrowseRepository()::discover,
) {
    private val mutableState = MutableStateFlow(BrowseState(filters = initialFilters))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    fun ensureLoaded() {
        if (state.value.page == 0 && !state.value.loading && state.value.error == null) load(false)
    }

    fun setFilters(filters: BrowseFilters) {
        if (filters == state.value.filters) return
        job?.cancel()
        generation++
        mutableState.value = BrowseState(filters = filters)
        load(false)
    }

    fun retry() = load(state.value.page > 0)
    fun loadMore() {
        if (state.value.page < state.value.totalPages) load(true)
    }

    private fun load(append: Boolean) {
        if (state.value.loading) return
        val before = state.value
        val requestGeneration = ++generation
        mutableState.value = before.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val result = fetch(before.filters, if (append) before.page + 1 else 1)
                if (requestGeneration == generation) {
                    mutableState.value = before.copy(
                        titles = ((if (append) before.titles else emptyList()) + result.titles).distinctBy { it.key },
                        page = result.page,
                        totalPages = result.totalPages,
                        totalResults = result.totalResults,
                        loading = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == generation) {
                    mutableState.value = before.copy(loading = false, error = "Couldn’t load the TMDB catalog. Check your connection and try again.")
                }
            }
        }
    }
}
