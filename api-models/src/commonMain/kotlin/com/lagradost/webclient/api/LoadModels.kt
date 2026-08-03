package com.lagradost.webclient.api

import kotlinx.serialization.Serializable

/**
 * Mirrors MainAPI's Episode. [dubStatus] is only populated for AnimeLoadResponse
 * (its episodes are keyed by DubStatus); it's null for TvSeriesLoadResponse episodes.
 */
@Serializable
data class EpisodeDto(
    val data: String,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val posterUrl: String? = null,
    val description: String? = null,
    val date: Long? = null,
    val runTime: Int? = null,
    val dubStatus: String? = null,
)

/**
 * Mirrors MainAPI's LoadResponse family. [kind] discriminates which loadLinks() shape to use:
 * - "movie"/"live"/"torrent": call loadLinks(dataUrl, ...) directly.
 * - "series": call loadLinks(episode.data, ...) per chosen episode.
 */
@Serializable
data class LoadResponseDto(
    val kind: String,
    val name: String,
    val url: String,
    val apiName: String,
    val type: String,
    val dataUrl: String? = null,
    val posterUrl: String? = null,
    val backgroundPosterUrl: String? = null,
    val year: Int? = null,
    val plot: String? = null,
    val tags: List<String>? = null,
    val duration: Int? = null,
    val showStatus: String? = null,
    val contentRating: String? = null,
    val episodes: List<EpisodeDto>? = null,
    val recommendations: List<SearchResultDto>? = null,
)

@Serializable
data class LoadRequest(
    val provider: String,
    val url: String,
)
