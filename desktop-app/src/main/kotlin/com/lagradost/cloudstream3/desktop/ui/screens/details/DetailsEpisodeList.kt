package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
                val epNumber = ep.episode?.let { "E$it " } ?: ""
                val title = ep.name ?: "Episode ${ep.episode ?: "?"}"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$epNumber- $title",
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
        }
    }
}

data class LinksPanelRequest(
    val provider: MainAPI,
    val dataUrl: String,
    val history: WatchHistory,
    val onPlayNext: (() -> Unit)? = null,
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
            ?.sortedBy { it.episode ?: Int.MAX_VALUE }
        val currentIdx = dubEps?.indexOfFirst { it.data == ep.data } ?: -1
        if (dubEps != null && currentIdx >= 0 && currentIdx + 1 < dubEps.size) dubEps[currentIdx + 1] else null
    }
    else -> null
}

fun navigateToPlay(provider: MainAPI, data: LoadResponse, ep: Episode, onPlay: (LinksPanelRequest) -> Unit) {
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
            patchedData = patchedData.replaceFirst("{", "{\"title\":\"\$titleStr\",")
        }
        if (!patchedData.contains("\"tvtype\"")) {
            patchedData = patchedData.replaceFirst("{", "{\"tvtype\":\"\",")
        }
    }
    val nextEp = resolveNextEpisode(data, ep)
    val onPlayNext: (() -> Unit)? = nextEp?.let { ne -> { navigateToPlay(provider, data, ne, onPlay) } }
    onPlay(LinksPanelRequest(provider, patchedData, history, onPlayNext))
}
