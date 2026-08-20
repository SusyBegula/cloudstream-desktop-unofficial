package com.lagradost.cloudstream3.desktop.ui

data class NextEpisodeData(
    val title: String?,
    val episodeNumber: Int?,
    val seasonNumber: Int?,
    val posterUrl: String?,
    val description: String?,
    val showName: String?,
    val backdropUrl: String?,
    val onPlay: () -> Unit,
)
