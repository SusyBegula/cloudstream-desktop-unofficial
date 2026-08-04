package com.lagradost.webclient.api

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleFileDto(
    val lang: String,
    val url: String,
)

@Serializable
data class AudioFileDto(
    val url: String,
)

@Serializable
data class PlayListItemDto(
    val url: String,
    val durationUs: Long,
)

/**
 * Mirrors ExtractorLink/ExtractorLinkPlayList. [headers]/[referer] are carried so the server
 * can re-derive them in /api/resolve; the browser itself never uses them directly (it can't
 * attach custom headers to media requests, which is why /api/resolve always routes through
 * the proxy instead of handing back a raw url).
 */
@Serializable
data class ExtractorLinkDto(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val type: String,
    val headers: Map<String, String> = emptyMap(),
    val audioTracks: List<AudioFileDto> = emptyList(),
    val playlist: List<PlayListItemDto>? = null,
)

@Serializable
data class LinksRequest(
    val provider: String,
    val data: String,
)

/** One event in the /api/links WebSocket stream. [kind] is "link" | "subtitle" | "done" | "error". */
@Serializable
data class LinkEventDto(
    val kind: String,
    val link: ExtractorLinkDto? = null,
    val subtitle: SubtitleFileDto? = null,
    val message: String? = null,
)

@Serializable
data class ResolveRequest(
    val link: ExtractorLinkDto,
    /** Restarts a transcode from this offset instead of the beginning — used when the player
     * seeks past what's been transcoded so far (see [PlayableStreamDto.durationSeconds]). */
    val startSeconds: Double = 0.0,
)

/** Playback kind is always resolved server-side to something a browser can consume directly. */
@Serializable
data class PlayableStreamDto(
    val proxyUrl: String,
    val mimeType: String,
    val kind: String,
    val subtitles: List<SubtitleFileDto> = emptyList(),
    val audioTracks: List<AudioFileDto> = emptyList(),
    /** Set only for transcoded streams, whose HLS playlist grows over time and so can't report
     * its own total length yet — lets the player show the real seek bar/duration immediately,
     * like a normal VOD, instead of only the portion transcoded so far. */
    val durationSeconds: Double? = null,
)
