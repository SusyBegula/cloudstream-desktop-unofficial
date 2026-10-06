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
    fun ageRatingFilterAppliesCertificationParameters() {
        val movieUrl = buildDiscoverUrl(BrowseFilters(ageRating = "13+"), 1).toHttpUrl()
        assertEquals("/3/discover/movie", movieUrl.encodedPath)
        assertEquals("US", movieUrl.queryParameter("certification_country"))
        assertEquals("PG-13", movieUrl.queryParameter("certification"))

        val tvUrl = buildDiscoverUrl(BrowseFilters(category = BrowseCategory.SHOWS, ageRating = "13+"), 1).toHttpUrl()
        assertEquals("/3/discover/tv", tvUrl.encodedPath)
        assertEquals("US", tvUrl.queryParameter("certification_country"))
        assertEquals("TV-14", tvUrl.queryParameter("certification"))

        val animeUrl = buildDiscoverUrl(BrowseFilters(category = BrowseCategory.ANIME, ageRating = "13+"), 1).toHttpUrl()
        assertEquals("/3/discover/tv", animeUrl.encodedPath)
        assertEquals("US", animeUrl.queryParameter("certification_country"))
        assertEquals("TV-14", animeUrl.queryParameter("certification"))

        val animeMovieUrl = buildDiscoverUrl(BrowseFilters(category = BrowseCategory.ANIME, animeMovies = true, ageRating = "13+"), 1).toHttpUrl()
        assertEquals("/3/discover/movie", animeMovieUrl.encodedPath)
        assertEquals("US", animeMovieUrl.queryParameter("certification_country"))
        assertEquals("PG-13", animeMovieUrl.queryParameter("certification"))
    }

    @Test
    fun directCertificationCodesAreMappedAcrossFormats() {
        val tvFromMovieCode = buildDiscoverUrl(BrowseFilters(category = BrowseCategory.SHOWS, ageRating = "R"), 1).toHttpUrl()
        assertEquals("US", tvFromMovieCode.queryParameter("certification_country"))
        assertEquals("TV-MA", tvFromMovieCode.queryParameter("certification"))

        val movieFromTvCode = buildDiscoverUrl(BrowseFilters(category = BrowseCategory.MOVIES, ageRating = "TV-14"), 1).toHttpUrl()
        assertEquals("US", movieFromTvCode.queryParameter("certification_country"))
        assertEquals("PG-13", movieFromTvCode.queryParameter("certification"))

        val unknownRating = buildDiscoverUrl(BrowseFilters(ageRating = "xyz"), 1).toHttpUrl()
        assertNull(unknownRating.queryParameter("certification_country"))
        assertNull(unknownRating.queryParameter("certification"))
    }

    @Test
    fun findAgeRatingResolvesTiersAndCodes() {
        assertEquals("13+", findAgeRating("13+")?.id)
        assertEquals("13+", findAgeRating("PG-13")?.id)
        assertEquals("13+", findAgeRating("TV-14")?.id)
        assertEquals("17+", findAgeRating("R")?.id)
        assertEquals("17+", findAgeRating("TV-MA")?.id)
        assertNull(findAgeRating("non-existent"))
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
            val vm = BrowseViewModel(scope, defaultPageSize = 1) { _, page ->
                requests += page
                if (page == 2 && fail) {
                    fail = false
                    error("offline")
                }
                BrowsePage(listOf(title(page)), page, 2, 2)
            }
            vm.ensureLoaded()
            vm.ensureLoaded()
            vm.loadMore()
            assertEquals(listOf(1), vm.state.value.titles.map { it.id })
            assertNotNull(vm.state.value.error)
            assertEquals(1, vm.state.value.page)
            vm.retry()
            assertNull(vm.state.value.error)
            assertEquals(listOf(2), vm.state.value.titles.map { it.id })
            assertEquals(2, vm.state.value.page)
            vm.prevPage()
            assertEquals(listOf(1), vm.state.value.titles.map { it.id })
            assertEquals(1, vm.state.value.page)
            // Cached page 1 doesn't trigger a new network request
            assertEquals(listOf(1, 2, 2), requests)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun dynamicThreeRowsPaginationSlicesAndCachesCorrectly() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requestedPages = mutableListOf<Int>()
            // TMDB returns 20 items per page
            val vm = BrowseViewModel(scope, defaultPageSize = 24) { _, page ->
                requestedPages += page
                val titles = (1..20).map { title((page - 1) * 20 + it) }
                BrowsePage(titles, page, 10, 200)
            }
            vm.ensureLoaded()
            // Page 1 with pageSize 24 needs TMDB pages 1 and 2
            assertEquals(listOf(1, 2), requestedPages)
            assertEquals(24, vm.state.value.titles.size)
            assertEquals(1, vm.state.value.page)
            assertEquals(1, vm.state.value.titles.first().id)
            assertEquals(24, vm.state.value.titles.last().id)

            // Navigate to page 2 (items 25..48)
            vm.nextPage()
            // Needs TMDB page 3 (page 2 was already cached!)
            assertEquals(listOf(1, 2, 3), requestedPages)
            assertEquals(24, vm.state.value.titles.size)
            assertEquals(2, vm.state.value.page)
            assertEquals(25, vm.state.value.titles.first().id)
            assertEquals(48, vm.state.value.titles.last().id)

            // Navigate back to page 1
            vm.prevPage()
            // No new network requests because pages 1 and 2 are cached!
            assertEquals(listOf(1, 2, 3), requestedPages)
            assertEquals(24, vm.state.value.titles.size)
            assertEquals(1, vm.state.value.page)
            assertEquals(1, vm.state.value.titles.first().id)
            assertEquals(24, vm.state.value.titles.last().id)

            // Resizing window: pageSize changes to 18 (e.g. 6 columns * 3 rows)
            vm.setPageSize(18)
            assertEquals(18, vm.state.value.titles.size)
            assertEquals(1, vm.state.value.page)
            assertEquals(1, vm.state.value.titles.first().id)
            assertEquals(18, vm.state.value.titles.last().id)
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

    @Test
    fun dedicatedCatalogsKeepTheirOwnCategoryFiltersAndPages() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requests = mutableListOf<BrowseCategory>()
            val models = BrowseCategory.entries.associateWith { category ->
                BrowseViewModel(scope, initialFilters = BrowseFilters(category = category), defaultPageSize = 1) { filters, page ->
                    requests += filters.category
                    BrowsePage(listOf(title(page)), page, 2, 2)
                }
            }
            models.values.forEach { it.ensureLoaded() }
            assertEquals(BrowseCategory.entries.toList(), requests)
            val movies = models.getValue(BrowseCategory.MOVIES)
            movies.setFilters(movies.state.value.filters.copy(year = 2020))
            movies.loadMore()
            models.getValue(BrowseCategory.ANIME).ensureLoaded()
            assertEquals(2020, movies.state.value.filters.year)
            assertEquals(2, movies.state.value.page)
            assertEquals(BrowseCategory.ANIME, models.getValue(BrowseCategory.ANIME).state.value.filters.category)
            assertNull(models.getValue(BrowseCategory.SHOWS).state.value.filters.year)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun duplicateTitlesWithinPageAreDeduplicatedWithoutCrashing() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            // Suppose TMDB returns duplicate titles on the same page or boundary
            val vm = BrowseViewModel(scope, defaultPageSize = 2) { _, page ->
                when (page) {
                    1 -> BrowsePage(listOf(title(1), title(2)), 1, 3, 4)
                    2 -> BrowsePage(listOf(title(2), title(2), title(3)), 2, 3, 4)
                    else -> BrowsePage(listOf(title(4)), 3, 3, 4)
                }
            }
            vm.ensureLoaded()
            assertEquals(listOf(1, 2), vm.state.value.titles.map { it.id })

            vm.nextPage()
            // Duplicate title(2) within page 2 is deduplicated!
            assertEquals(listOf(2, 3), vm.state.value.titles.map { it.id })
            // Ensure no duplicate keys anywhere on the page
            assertEquals(vm.state.value.titles.size, vm.state.value.titles.distinctBy { it.key }.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun jumpingToLastPageFetchesOnlyLastPageWithoutSequentialLoop() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requestedPages = mutableListOf<Int>()
            val vm = BrowseViewModel(scope, defaultPageSize = 24) { _, page ->
                requestedPages += page
                val titles = (1..20).map { title((page - 1) * 20 + it) }
                BrowsePage(titles, page, 500, 10000)
            }
            vm.ensureLoaded()
            assertEquals(listOf(1, 2), requestedPages)
            val lastPage = vm.state.value.totalPages
            assertEquals(417, lastPage)

            // Jump directly to the last page!
            vm.loadPage(lastPage)
            // Should ONLY request page 500! Should NOT request pages 3..499!
            assertTrue(500 in requestedPages)
            assertFalse(3 in requestedPages)
            assertFalse(250 in requestedPages)
            assertEquals(listOf(1, 2, 500), requestedPages)
            assertEquals(lastPage, vm.state.value.page)
            assertFalse(vm.state.value.loading)
        } finally {
            scope.cancel()
        }
    }

    private fun title(id: Int) = BrowseTitle(id, "Title $id", true, null, "", 2020, 7.0)
}
