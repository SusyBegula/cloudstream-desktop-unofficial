package com.lagradost.webclient.api

import kotlinx.serialization.Serializable

/**
 * Remembers which stream source (ExtractorLink.source, e.g. "MovieBox (English Audio)") to
 * prefer for a given show/movie, so the player doesn't default back to the first link on every
 * new episode. Keyed by [provider] + [seriesUrl] (the show's own page URL, not an episode's).
 */
@Serializable
data class PreferredSourceDto(
    val provider: String,
    val seriesUrl: String,
    val sourceName: String,
)
