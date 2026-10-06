package com.lagradost.cloudstream3.desktop.ui.components

import com.lagradost.cloudstream3.TvType
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchBadgeTest {

    private fun resolveType(url: String, fallbackType: TvType?): TvType? {
        val urlType: TvType? = when {
            url.contains("\"type\"") -> {
                val typeVal = Regex("""\"type\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
                    .find(url)?.groupValues?.get(1)?.lowercase()
                when (typeVal) {
                    "series", "tv", "show" -> TvType.TvSeries
                    "anime" -> TvType.Anime
                    "movie" -> TvType.Movie
                    "live" -> TvType.Live
                    "ova" -> TvType.OVA
                    else -> null
                }
            }
            url.contains("/series/") || url.contains("/tv/") || url.contains("/show/") || url.contains("/shows/") -> TvType.TvSeries
            url.contains("/movie/") || url.contains("/movies/") -> TvType.Movie
            url.contains("/anime/") -> TvType.Anime
            else -> null
        }
        return urlType ?: fallbackType
    }

    @Test
    fun testCineStreamSeriesPayloadDetection() {
        val seriesPayload = """{"id":"tt0409591","type":"series"}"""
        val moviePayload = """{"id":"tt1234567","type":"movie"}"""

        assertEquals(TvType.TvSeries, resolveType(seriesPayload, TvType.Movie))
        assertEquals(TvType.Movie, resolveType(moviePayload, TvType.Movie))
    }

    @Test
    fun testUrlPathDetection() {
        assertEquals(TvType.TvSeries, resolveType("https://example.com/series/naruto", null))
        assertEquals(TvType.Movie, resolveType("https://example.com/movies/naruto-the-movie", null))
    }
}
