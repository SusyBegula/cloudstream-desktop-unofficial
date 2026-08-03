package com.lagradost.webclient.api

import kotlinx.serialization.Serializable

/** Mirrors the common fields across MainAPI's SearchResponse subtypes (Movie/TvSeries/Anime/etc). */
@Serializable
data class SearchResultDto(
    val name: String,
    val url: String,
    val apiName: String,
    val type: String? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val episodes: Int? = null,
    val quality: String? = null,
)

@Serializable
data class SearchResponseDto(
    val provider: String,
    val results: List<SearchResultDto>,
)

@Serializable
data class AggregatedSearchResponse(
    val query: String,
    val perProvider: List<SearchResponseDto>,
)
