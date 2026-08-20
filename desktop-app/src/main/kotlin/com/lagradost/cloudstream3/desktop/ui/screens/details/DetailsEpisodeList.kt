package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.*
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun EpisodeCard(ep: Episode, isLatest: Boolean, history: WatchHistory?, provider: MainAPI, data: LoadResponse, onPlay: (LinksPanelRequest) -> Unit) {
    val isDownloaded = remember(data.url, ep.data, DesktopDataStore.downloadUpdates.collectAsState().value) {
        com.lagradost.cloudstream3.desktop.download.FfmpegDownloadManager.isEpisodeDownloaded(data.url, ep.data)
    }
    val downloads = com.lagradost.cloudstream3.desktop.download.FfmpegDownloadManager.downloadsFlow.collectAsState().value
    val downloading = downloads.find { it.showUrl == data.url && it.episodeId == ep.data && it.status == com.lagradost.cloudstream3.desktop.download.DownloadStatus.Downloading }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { navigateToPlay(provider, data, ep, onPlay) },
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        border = if (isLatest) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val epImg = provider.fixUrlNull(ep.posterUrl)
            if (epImg != null) {
                AsyncImage(
                    model = epImg,
                    contentDescription = ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(224.dp)
                        .height(126.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                Box(
                    modifier = Modifier
                        .width(224.dp)
                        .height(126.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                val epNum = ep.episode
                val rawName = ep.name?.trim() ?: ""
                val title = when {
                    rawName.isBlank() -> "Episode ${epNum ?: "?"}"
                    epNum != null && (
                        rawName.equals("E$epNum", ignoreCase = true) ||
                        rawName.equals("Episode $epNum", ignoreCase = true) ||
                        rawName.equals("S${ep.season}E$epNum", ignoreCase = true) ||
                        rawName.matches(Regex("""(?i)^S\d+E$epNum$"""))
                    ) -> "Episode $epNum"
                    epNum != null -> "E$epNum - $rawName"
                    else -> rawName
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val epRunTime = ep.runTime ?: data.duration
                    epRunTime?.let { rt ->
                        val runTimeStr = if (rt > 300) "${rt / 60}m" else "${rt}m"
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = runTimeStr,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF1B382B),
                        ) {
                            Text(
                                "✓ Downloaded",
                                color = Color(0xFF81C784),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else if (downloading != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF1C2D42),
                        ) {
                            Text(
                                "⬇ Downloading...",
                                color = Color(0xFF64B5F6),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                ep.description?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (history != null && history.duration > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.lagradost.cloudstream3.desktop.ui.components.WatchProgressIndicator(
                        position = history.position,
                        duration = history.duration,
                    )
                }
            }

            // Download Action Button directly on Episode Card
            IconButton(
                onClick = {
                    navigateToPlay(provider, data, ep, onPlay, autoPlay = false)
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                if (isDownloaded) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(24.dp),
                    )
                } else if (downloading != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF64B5F6),
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download Episode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

data class LinksPanelRequest(
    val provider: MainAPI,
    val dataUrl: String,
    val history: WatchHistory,
    val onPlayNext: (() -> Unit)? = null,
    val autoPlay: Boolean = true,
)

private fun resolveNextEpisode(data: LoadResponse, ep: Episode): Episode? = when (data) {
    is TvSeriesLoadResponse -> {
        val currentSeason = ep.season
        val sortedSeasonEps = data.episodes
            .filter { it.season == currentSeason || (it.season == null && currentSeason == null) }
            .sortedBy { it.episode ?: Int.MAX_VALUE }
        val currentIdx = sortedSeasonEps.indexOfFirst { it.data == ep.data }
        val nextEpSameSeason = if (currentIdx >= 0 && currentIdx + 1 < sortedSeasonEps.size) sortedSeasonEps[currentIdx + 1] else null
        nextEpSameSeason ?: run {
            val nextSeason = (currentSeason ?: 0) + 1
            data.episodes
                .filter { it.season == nextSeason }
                .minByOrNull { it.episode ?: Int.MAX_VALUE }
        }
    }
    is AnimeLoadResponse -> {
        val dubEps = data.episodes.values
            .find { list -> list.any { it.data == ep.data } }
        val currentSeason = ep.season
        val sortedSeasonEps = (dubEps ?: emptyList())
            .filter { it.season == currentSeason || (it.season == null && currentSeason == null) }
            .sortedBy { it.episode ?: Int.MAX_VALUE }
        val currentIdx = sortedSeasonEps.indexOfFirst { it.data == ep.data }
        val nextEpSameSeason = if (currentIdx >= 0 && currentIdx + 1 < sortedSeasonEps.size) sortedSeasonEps[currentIdx + 1] else null
        nextEpSameSeason ?: run {
            val nextSeason = (currentSeason ?: 0) + 1
            (dubEps ?: emptyList())
                .filter { it.season == nextSeason }
                .minByOrNull { it.episode ?: Int.MAX_VALUE }
        }
    }
    else -> null
}

fun navigateToPlay(
    provider: MainAPI,
    data: LoadResponse,
    ep: Episode,
    onPlay: (LinksPanelRequest) -> Unit,
    autoPlay: Boolean = true,
) {
    val parentId = DesktopDataStore.watchHistoryId(
        apiName = provider.name,
        showUrl = data.url,
    )
    val saved = DesktopDataStore.getEpisodeWatched(parentId, ep.data)
    val resumePos = PlayerLinkHandler.resumeStartSeconds(
        saved?.position ?: 0L,
        saved?.duration ?: 0L,
    )
    val history = WatchHistory(
        parentId = parentId,
        showName = data.name,
        showUrl = data.url,
        apiName = provider.name,
        posterUrl = data.posterUrl,
        episode = ep.episode,
        season = ep.season,
        episodeId = ep.data,
        position = resumePos,
        duration = saved?.duration ?: 0L,
    )
    var patchedData = ep.data
    if (patchedData.startsWith("{") && patchedData.endsWith("}")) {
        if (!patchedData.contains("\"title\"")) {
            val titleStr = data.name.replace("\"", "\\\"")
            patchedData = patchedData.replaceFirst("{", "{\"title\":\"$titleStr\",")
        }
        if (!patchedData.contains("\"tvtype\"")) {
            patchedData = patchedData.replaceFirst("{", "{\"tvtype\":\"\",")
        }
    }
    val nextEp = resolveNextEpisode(data, ep)
    val onPlayNext: (() -> Unit)? = nextEp?.let { ne -> { navigateToPlay(provider, data, ne, onPlay, autoPlay = true) } }
    onPlay(LinksPanelRequest(provider, patchedData, history, onPlayNext, autoPlay))
}
