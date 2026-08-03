package com.lagradost.webclient.server.routes

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.PlayListItem
import com.lagradost.webclient.api.EpisodeDto
import com.lagradost.webclient.api.ExtractorLinkDto
import com.lagradost.webclient.api.LoadResponseDto
import com.lagradost.webclient.api.PlayListItemDto
import com.lagradost.webclient.api.ProviderDto
import com.lagradost.webclient.api.SearchResultDto
import com.lagradost.webclient.api.SubtitleFileDto
import com.lagradost.webclient.api.BookmarkDto
import com.lagradost.webclient.api.WatchHistoryEntryDto
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.WatchHistory

fun DesktopBookmark.toDto(): BookmarkDto = BookmarkDto(
    provider = apiName,
    url = url,
    name = name,
    posterUrl = posterUrl,
    addedAt = 0L,
)

fun WatchHistory.toDto(): WatchHistoryEntryDto = WatchHistoryEntryDto(
    provider = apiName,
    url = showUrl,
    name = showName,
    posterUrl = posterUrl,
    episodeData = episodeId,
    season = season,
    episode = episode,
    positionMs = position * 1000,
    durationMs = duration * 1000,
    updatedAt = updateTime,
)

/**
 * Returns true only for real, user-facing content providers (excludes built-in
 * MetaProviders and the "NONE" placeholder) — mirrors desktop-app's HomeViewModel.isRealProvider().
 */
fun MainAPI.isRealProvider(): Boolean {
    if (name == "NONE" || name == "None") return false
    if (providerType == ProviderType.MetaProvider) return false
    return true
}

fun MainAPI.toDto(): ProviderDto = ProviderDto(
    name = name,
    mainUrl = mainUrl,
    lang = lang,
    hasMainPage = hasMainPage,
    hasQuickSearch = hasQuickSearch,
    providerType = providerType.name,
    supportedTypes = supportedTypes.map { it.name },
)

fun SearchResponse.toDto(): SearchResultDto {
    val year = (this as? com.lagradost.cloudstream3.MovieSearchResponse)?.year
        ?: (this as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year
        ?: (this as? com.lagradost.cloudstream3.AnimeSearchResponse)?.year
    val episodes = (this as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.episodes
    return SearchResultDto(
        name = name,
        url = url,
        apiName = apiName,
        type = type?.name,
        posterUrl = posterUrl,
        year = year,
        episodes = episodes,
        quality = quality?.name,
    )
}

fun Episode.toDto(dubStatus: String? = null): EpisodeDto = EpisodeDto(
    data = data,
    name = name,
    season = season,
    episode = episode,
    posterUrl = posterUrl,
    description = description,
    date = date,
    runTime = runTime,
    dubStatus = dubStatus,
)

fun LoadResponse.toDto(): LoadResponseDto {
    val kind: String
    val dataUrl: String?
    val episodes: List<EpisodeDto>?
    when (this) {
        is MovieLoadResponse -> {
            kind = "movie"
            dataUrl = this.dataUrl
            episodes = null
        }
        is LiveStreamLoadResponse -> {
            kind = "live"
            dataUrl = this.dataUrl
            episodes = null
        }
        is TvSeriesLoadResponse -> {
            kind = "series"
            dataUrl = null
            episodes = this.episodes.map { it.toDto() }
        }
        is AnimeLoadResponse -> {
            kind = "series"
            dataUrl = null
            episodes = this.episodes.flatMap { (status, eps) -> eps.map { it.toDto(status.name) } }
        }
        else -> {
            kind = "movie"
            dataUrl = null
            episodes = null
        }
    }

    return LoadResponseDto(
        kind = kind,
        name = name,
        url = url,
        apiName = apiName,
        type = type.name,
        dataUrl = dataUrl,
        posterUrl = posterUrl,
        backgroundPosterUrl = backgroundPosterUrl,
        year = year,
        plot = plot,
        tags = tags,
        duration = duration,
        showStatus = (this as? TvSeriesLoadResponse)?.showStatus?.name
            ?: (this as? AnimeLoadResponse)?.showStatus?.name,
        contentRating = contentRating,
        episodes = episodes,
        recommendations = recommendations?.map { it.toDto() },
    )
}

fun SubtitleFile.toDto(): SubtitleFileDto = SubtitleFileDto(lang = lang, url = url)

fun AudioFile.toDto(): com.lagradost.webclient.api.AudioFileDto = com.lagradost.webclient.api.AudioFileDto(url = url)

fun ExtractorLink.toDto(): ExtractorLinkDto = ExtractorLinkDto(
    source = source,
    name = name,
    url = url,
    referer = referer,
    quality = quality,
    type = type.name,
    headers = headers,
    audioTracks = audioTracks.map { it.toDto() },
    playlist = (this as? ExtractorLinkPlayList)?.playlist?.map { PlayListItemDto(it.url, it.durationUs) },
)

suspend fun ExtractorLinkDto.toDomain(): ExtractorLink {
    val playlistItems = playlist
    val audio = audioTracks.map { com.lagradost.cloudstream3.newAudioFile(it.url) }
    return if (playlistItems != null) {
        @Suppress("DEPRECATION")
        ExtractorLinkPlayList(
            source = source,
            name = name,
            playlist = playlistItems.map { PlayListItem(it.url, it.durationUs) },
            referer = referer,
            quality = quality,
            headers = headers,
            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.valueOf(type),
            audioTracks = audio,
        )
    } else {
        @Suppress("DEPRECATION")
        ExtractorLink(
            source = source,
            name = name,
            url = url,
            referer = referer,
            quality = quality,
            headers = headers,
            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.valueOf(type),
            audioTracks = audio,
        )
    }
}
