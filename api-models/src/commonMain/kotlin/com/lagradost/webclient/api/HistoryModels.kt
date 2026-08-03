package com.lagradost.webclient.api

import kotlinx.serialization.Serializable

@Serializable
data class WatchHistoryEntryDto(
    val provider: String,
    val url: String,
    val name: String,
    val posterUrl: String? = null,
    val episodeData: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Serializable
data class WatchHistoryResponse(
    val entries: List<WatchHistoryEntryDto>,
)

@Serializable
data class BookmarkDto(
    val provider: String,
    val url: String,
    val name: String,
    val posterUrl: String? = null,
    val addedAt: Long,
)

@Serializable
data class BookmarksResponse(
    val bookmarks: List<BookmarkDto>,
)
