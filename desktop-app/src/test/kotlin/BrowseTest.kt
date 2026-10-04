package com.lagradost.cloudstream3.desktop.ui.screens.browse

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import kotlin.test.*

class BrowseTest {
    @Test
    fun movieFiltersAreSentToCatalog() {
        val url = buildDiscoverUrl(
            BrowseFilters(genre = 28, year = 2020, minRating = 7, language = "hi", sort = BrowseSort.RATING),
            3,
            LocalDate.of(2026, 10, 4),
        ).toHttpUrl()
        assertEquals("/3/discover/movie", url.encodedPath)
        assertEquals("2020", url.queryParameter("primary_release_year"))
        assertEquals("2026-10-04", url.queryParameter("primary_release_date.lte"))
        assertEquals("28", url.queryParameter("with_genres"))
        assertEquals("7", url.queryParameter("vote_average.gte"))
        assertEquals("100", url.queryParameter("vote_count.gte"))
        assertEquals("vote_average.desc", url.queryParameter("sort_by"))
        assertEquals("hi", url.queryParameter("with_original_language"))
        assertEquals("3", url.queryParameter("page"))
    }

    @Test
    fun animeUsesAnimationAndJapaneseForBothFormats() {
        for (movie in listOf(false, true)) {
            val filters = BrowseFilters(category = BrowseCategory.ANIME, animeMovies = movie, genre = 18, language = "en", year = 2024, sort = BrowseSort.NEWEST)
            val url = buildDiscoverUrl(filters, 1).toHttpUrl()
            assertEquals("16,18", url.queryParameter("with_genres"))
            assertEquals("ja", url.queryParameter("with_original_language"))
            assertEquals(if (movie) "primary_release_date.desc" else "first_air_date.desc", url.queryParameter("sort_by"))
            assertEquals("2024", url.queryParameter(if (movie) "primary_release_year" else "first_air_date_year"))
            assertFalse(filters.genres.any { it.id == 16 })
        }
    }

    @Test
    fun showGenreIdsAreDifferentFromMovieIds() {
        val filters = BrowseFilters(category = BrowseCategory.SHOWS)
        assertTrue(filters.genres.any { it.id == 10759 })
        assertFalse(filters.genres.any { it.id == 28 })
        assertNull(buildDiscoverUrl(filters, 1).toHttpUrl().queryParameter("vote_count.gte"))
    }

    @Test
    fun parsesMissingMetadataAndSkipsInvalidTitles() {
        val page = parseDiscoverPage(
            """{
          "page":1,"total_pages":900,"total_results":3,"results":[
            {"id":1,"name":"Example","poster_path":null,"first_air_date":"","overview":null,"vote_average":0,"vote_count":0},
            {"id":2,"name":"Rated","poster_path":"/poster.jpg","first_air_date":"2020-01-01","vote_average":8.5,"vote_count":500},
            {"id":3,"name":""}
          ]
        }""",
            false,
        )
        assertEquals(500, page.totalPages)
        assertEquals(2, page.titles.size)
        assertNull(page.titles[0].poster)
        assertNull(page.titles[0].rating)
        assertNull(page.titles[0].year)
        assertEquals("", page.titles[0].overview)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", page.titles[1].poster)
        assertEquals(8.5, page.titles[1].rating)
        assertEquals("tv/2", page.titles[1].key)
        assertFails { parseDiscoverPage("""{"success":false,"status_message":"Invalid key"}""", true) }
    }

    @Test
    fun paginationDeduplicatesAndRetryKeepsExistingResults() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var fail = true
            val requests = mutableListOf<Int>()
            val vm = BrowseViewModel(scope) { _, page ->
                requests += page
                if (page == 2 && fail) {
                    fail = false
                    error("offline")
                }
                BrowsePage(if (page == 1) listOf(title(1)) else listOf(title(1), title(2)), page, 2, 2)
            }
            vm.ensureLoaded()
            vm.ensureLoaded()
            vm.loadMore()
            assertEquals(listOf(1), vm.state.value.titles.map { it.id })
            assertNotNull(vm.state.value.error)
            assertEquals(1, vm.state.value.page)
            vm.retry()
            assertNull(vm.state.value.error)
            assertEquals(listOf(1, 2), vm.state.value.titles.map { it.id })
            vm.loadMore()
            assertEquals(listOf(1, 2, 2), requests)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun changingFiltersCancelsOldRequestAndResetsPagination() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val oldRequest = CompletableDeferred<BrowsePage>()
            val vm = BrowseViewModel(scope) { filters, _ ->
                if (filters.category == BrowseCategory.MOVIES) {
                    oldRequest.await()
                } else {
                    BrowsePage(listOf(title(2)), 1, 1, 1)
                }
            }
            vm.ensureLoaded()
            assertTrue(vm.state.value.loading)
            vm.setFilters(BrowseFilters(category = BrowseCategory.SHOWS))
            oldRequest.complete(BrowsePage(listOf(title(1)), 1, 1, 1))
            assertFalse(vm.state.value.loading)
            assertEquals(listOf(2), vm.state.value.titles.map { it.id })
            assertEquals(BrowseCategory.SHOWS, vm.state.value.filters.category)
        } finally {
            scope.cancel()
        }
    }

    private fun title(id: Int) = BrowseTitle(id, "Title $id", true, null, "", 2020, 7.0)
}
