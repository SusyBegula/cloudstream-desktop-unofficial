package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.download.FfmpegDownloadManager
import com.lagradost.cloudstream3.desktop.ui.NextEpisodeData
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import com.lagradost.player.impl.VlcPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val vlcPlayer = VlcPlayer()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksSidePanel(
    provider: MainAPI,
    dataUrl: String,
    history: WatchHistory,
    onClose: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    autoPlay: Boolean = true,
    nextEpisode: NextEpisodeData? = null,
) {
    LinksModal(provider, dataUrl, history, onClose, onPlayNext, autoPlay, nextEpisode)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksModal(
    provider: MainAPI,
    dataUrl: String,
    history: WatchHistory,
    onClose: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    autoPlay: Boolean = true,
    nextEpisode: NextEpisodeData? = null,
) {
    val links = remember { mutableStateListOf<ExtractorLink>() }
    val subtitles = remember { mutableStateListOf<SubtitleFile>() }
    var statusText by remember { mutableStateOf("Finding streams for you...") }
    var isScraping by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current
    var selectedPlayer by remember { mutableStateOf(DesktopDataStore.getKey<String>("preferred_player") ?: "mpv") }
    var isLaunchingPlayer by remember { mutableStateOf(false) }
    var playerLaunchError by remember { mutableStateOf<String?>(null) }
    var scrapeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var embeddedError by remember { mutableStateOf<String?>(null) }

    var selectedQuality by remember { mutableStateOf<String?>(null) }
    var currentPlayingUrl by remember { mutableStateOf<String?>(null) }

    val defaultStreamKey = remember(provider.name, history.showUrl) {
        "default_stream_${provider.name}_${history.showUrl}"
    }
    var defaultStreamName by remember(defaultStreamKey, history.showUrl) {
        mutableStateOf(DesktopDataStore.getKey<String>(defaultStreamKey))
    }
    var hasAutoPlayed by remember { mutableStateOf(false) }

    val onToggleDefault: (ExtractorLink) -> Unit = { link ->
        if (defaultStreamName.equals(link.name, ignoreCase = true)) {
            defaultStreamName = null
            DesktopDataStore.removeKey(defaultStreamKey)
        } else {
            defaultStreamName = link.name
            DesktopDataStore.setKey(defaultStreamKey, link.name)
        }
    }

    val availableQualities = remember(links.size) { links.map { it.quality.toString() }.distinct().sorted() }
    val filteredLinks = remember(links.size, selectedQuality) {
        if (selectedQuality == null) links else links.filter { it.quality.toString() == selectedQuality }
    }

    val displayTitle = remember(history) {
        buildString {
            append(history.showName)
            if (history.season != null && history.episode != null) {
                append(" - S${history.season}E${history.episode}")
            } else if (history.episode != null) {
                append(" - E${history.episode}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // no-op, player manages its own lifecycle or we could stop it if desired
        }
    }

    val vlcState = vlcPlayer.state.collectAsState().value
    val isAnyPlaying = vlcState.isPlaying

    var lastVlcSavedPositionSec by remember { mutableStateOf(0L) }

    LaunchedEffect(vlcState.position) {
        val currentPositionMs = if (vlcState.isPlaying) {
            vlcState.position
        } else {
            0L
        }
        val currentDurationMs = if (vlcState.isPlaying) {
            vlcState.duration
        } else {
            0L
        }

        if (currentPositionMs > 0 && currentDurationMs > 0) {
            val currentPosSec = currentPositionMs / 1000L
            if (kotlin.math.abs(currentPosSec - lastVlcSavedPositionSec) >= 5) {
                lastVlcSavedPositionSec = currentPosSec
                val updatedHistory = history.copy(
                    position = currentPosSec,
                    duration = currentDurationMs / 1000L,
                    updateTime = System.currentTimeMillis(),
                )
                DesktopDataStore.setLastWatched(updatedHistory)
            }
        }
    }

    DisposableEffect(isAnyPlaying) {
        onDispose {
            if (!isAnyPlaying && vlcState.position > 0 && vlcState.duration > 0) {
                val finalPosSec = vlcState.position / 1000L
                val finalDurSec = vlcState.duration / 1000L
                val updatedHistory = history.copy(
                    position = finalPosSec,
                    duration = finalDurSec,
                    updateTime = System.currentTimeMillis(),
                )
                DesktopDataStore.setLastWatched(updatedHistory)
            }
        }
    }

    LaunchedEffect(isAnyPlaying) {
        if (!isAnyPlaying) {
            if (statusText == "Player started." || statusText.startsWith("Playing:")) {
                statusText = "Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available."
            }
            isLaunchingPlayer = false
            currentPlayingUrl = null
        }
    }

    val playLink: (ExtractorLink) -> Unit = { link ->
        if (!(isLaunchingPlayer && currentPlayingUrl == null)) {
            val validation = PlayerLinkHandler.validate(link, displayTitle)
            if (validation.isFailure) {
                statusText = validation.exceptionOrNull()?.message ?: "Invalid stream"
            } else {
                isLaunchingPlayer = true
                currentPlayingUrl = link.url
                val effectivePlayer =
                    if (selectedPlayer == "vlc" && PlayerLinkHandler.shouldPreferMpv(link)) {
                        "mpv"
                    } else {
                        selectedPlayer
                    }
                statusText = "Launching ${effectivePlayer.uppercase()}..."

                val latestHistory = DesktopDataStore.getEpisodeWatched(history.parentId, history.episodeId) ?: history
                val startSec = PlayerLinkHandler.resumeStartSeconds(latestHistory.position, latestHistory.duration)
                val startMs = startSec * 1000L

                val subUrls = subtitles.map { it.url }.filter { it.isNotBlank() }
                if (effectivePlayer == "vlc") {
                    coroutineScope.launch {
                        val result = vlcPlayer.play(link, displayTitle, subUrls, startMs)
                        if (result.isSuccess) {
                            statusText = "Playing: ${link.name}"
                            playerLaunchError = null
                            onClose()
                        } else {
                            playerLaunchError = result.exceptionOrNull()?.message ?: "Failed to launch player"
                            statusText = "Could not start player."
                            isLaunchingPlayer = false
                            currentPlayingUrl = null
                        }
                    }
                } else {
                    val initialIndex = filteredLinks.indexOfFirst { it.url == link.url }.coerceAtLeast(0)
                    playVideo(
                        com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                            links = if (filteredLinks.isNotEmpty()) filteredLinks else listOf(link),
                            initialIndex = initialIndex,
                            title = displayTitle,
                            subtitles = subtitles.filter { it.url.isNotBlank() },
                            startPositionMs = startMs,
                            history = history,
                            onError = { err ->
                                embeddedError = err
                            },
                            onClosed = {
                                isLaunchingPlayer = false
                                currentPlayingUrl = null
                                statusText = "Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available."
                            },
                            onPlayNext = onPlayNext,
                            nextEpisode = nextEpisode,
                        ),
                    )
                    statusText = "Playing in embedded player: ${link.name}"
                    onClose()
                }
            }
        }
    }

    LaunchedEffect(vlcState.error, embeddedError) {
        val errorMessage = vlcState.error ?: embeddedError
        if (errorMessage != null) {
            playerLaunchError = errorMessage
            statusText = "Playback failed: $errorMessage"
            isLaunchingPlayer = false
            currentPlayingUrl = null
            embeddedError = null
        }
    }

    LaunchedEffect(dataUrl, autoPlay) {
        links.clear()
        subtitles.clear()
        isScraping = true
        hasAutoPlayed = false
        statusText = if (autoPlay && !defaultStreamName.isNullOrBlank()) {
            "Searching for preferred stream '$defaultStreamName'..."
        } else {
            "Finding streams for you..."
        }
        playerLaunchError = null
        currentPlayingUrl = null

        scrapeJob = launch(Dispatchers.IO) {
            try {
                provider.loadLinks(
                    data = dataUrl,
                    isCasting = false,
                    subtitleCallback = { sub: SubtitleFile -> coroutineScope.launch { subtitles.add(sub) } },
                    callback = { link: ExtractorLink ->
                        coroutineScope.launch {
                            links.add(link)
                            if (autoPlay && !hasAutoPlayed && !defaultStreamName.isNullOrBlank() && link.name.equals(defaultStreamName, ignoreCase = true)) {
                                hasAutoPlayed = true
                                playLink(link)
                            } else {
                                statusText = "Found ${links.size} stream${if (links.size == 1) "" else "s"}..."
                            }
                        }
                    },
                )
                isScraping = false
                if (!hasAutoPlayed) {
                    statusText = when {
                        links.isEmpty() -> "No streams found for this title."
                        !defaultStreamName.isNullOrBlank() && links.none { it.name.equals(defaultStreamName, ignoreCase = true) } ->
                            "Preferred stream '$defaultStreamName' not available. Choose a stream below:"
                        else -> "Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available."
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                isScraping = false
                statusText = "Search stopped (${links.size} found)."
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading links", e)
                isScraping = false
                statusText = "Error: ${e.message}"
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 700.dp).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Select stream",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DesktopUi.TextPrimary,
                        )
                        Text(
                            displayTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopUi.TextMuted,
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = DesktopUi.TextPrimary,
                        )
                    }
                }
                HorizontalDivider(color = DesktopUi.Divider)

                StreamStatusCard(
                    statusText = statusText,
                    isLoading = isScraping || isLaunchingPlayer,
                    isScraping = isScraping,
                    onStop = { scrapeJob?.cancel() },
                )

                PlayerSelector(
                    selectedPlayer = selectedPlayer,
                    onSelect = { player ->
                        selectedPlayer = player
                        DesktopDataStore.setKey("preferred_player", player)
                    },
                )

                if (availableQualities.size > 1) {
                    QualitySelector(
                        availableQualities = availableQualities,
                        selectedQuality = selectedQuality,
                        onSelect = { selectedQuality = it },
                    )
                }

                val downloadedItem = remember(history.showUrl, history.episodeId, DesktopDataStore.downloadUpdates.collectAsState().value) {
                    FfmpegDownloadManager.getDownloadedRecord(history.showUrl, history.episodeId)
                }
                val downloadsList = FfmpegDownloadManager.downloadsFlow.collectAsState().value
                val currentDownloading = downloadsList.find { it.showUrl == history.showUrl && it.episodeId == history.episodeId }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (downloadedItem != null) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1B382B),
                                border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.6f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Downloaded Offline File",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF81C784),
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val mb = downloadedItem.totalBytes / (1024 * 1024)
                                        Text(
                                            "Saved on disk · $mb MB · Instant Offline Playback",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DesktopUi.TextMuted,
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val localLink = com.lagradost.cloudstream3.utils.newExtractorLink(
                                                    source = "Offline File",
                                                    name = "Local Download",
                                                    url = downloadedItem.localFilePath,
                                                )
                                                playLink(localLink)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50),
                                            contentColor = Color.Black,
                                        ),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Play Offline", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (!isScraping && filteredLinks.isEmpty() && downloadedItem == null) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No Streams Found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No playable links were returned. Try another episode or provider.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    itemsIndexed(filteredLinks, key = { index, it -> "${it.name}-${it.url}-$index" }) { index, link ->
                        val isDefault = !defaultStreamName.isNullOrBlank() && link.name.equals(defaultStreamName, ignoreCase = true)
                        val isThisDownloading = currentDownloading != null && currentDownloading.streamUrl == link.url
                        StreamLinkCard(
                            link = link,
                            isDefault = isDefault,
                            onToggleDefault = { onToggleDefault(link) },
                            isBusy = isLaunchingPlayer && currentPlayingUrl != link.url,
                            isDownloading = isThisDownloading,
                            downloadSpeed = if (isThisDownloading) currentDownloading.speed else "",
                            onPlay = {
                                playLink(link)
                            },
                            onDownload = {
                                FfmpegDownloadManager.startDownload(
                                    showName = history.showName,
                                    showUrl = history.showUrl,
                                    episodeId = history.episodeId,
                                    episodeTitle = displayTitle,
                                    season = history.season,
                                    episode = history.episode,
                                    posterUrl = history.posterUrl,
                                    link = link,
                                )
                                statusText = "Downloading ${link.name} in background..."
                            },
                            onCopy = {
                                if (link.url.isNotBlank()) {
                                    val selection = java.awt.datatransfer.StringSelection(link.url)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(selection, selection)
                                    statusText = "URL copied to clipboard."
                                }
                            },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }

            if (playerLaunchError != null) {
                AlertDialog(
                    onDismissRequest = { playerLaunchError = null },
                    title = { Text("Player error") },
                    text = { Text(playerLaunchError!!) },
                    confirmButton = {
                        TextButton(onClick = { playerLaunchError = null }) { Text("OK") }
                    },
                )
            }
        }
    }
}

@Composable
private fun StreamStatusCard(
    statusText: String,
    isLoading: Boolean,
    isScraping: Boolean,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = DesktopUi.Accent,
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = DesktopUi.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (isScraping) {
                FilledTonalButton(
                    onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF3D2028),
                        contentColor = Color(0xFFFF8A8A),
                    ),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun PlayerSelector(selectedPlayer: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Text("Player", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedPlayer == "mpv",
                onClick = { onSelect("mpv") },
                label = { Text("MPV") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DesktopUi.AccentSoft,
                    selectedLabelColor = DesktopUi.Accent,
                ),
            )
            FilterChip(
                selected = selectedPlayer == "vlc",
                onClick = { onSelect("vlc") },
                label = { Text("VLC") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DesktopUi.AccentSoft,
                    selectedLabelColor = DesktopUi.Accent,
                ),
            )
        }
    }
}

@Composable
private fun StreamLinkCard(
    link: ExtractorLink,
    isDefault: Boolean,
    onToggleDefault: () -> Unit,
    isBusy: Boolean,
    isDownloading: Boolean = false,
    downloadSpeed: String = "",
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.01f else 1f, tween(150), label = "linkScale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .hoverable(interaction),
        shape = RoundedCornerShape(12.dp),
        color = if (isDefault) DesktopUi.AccentSoft.copy(alpha = 0.22f) else if (hovered) DesktopUi.SurfaceElevated else DesktopUi.SurfaceCard,
        border = if (isDefault) BorderStroke(1.5.dp, DesktopUi.Accent.copy(alpha = 0.75f)) else null,
        tonalElevation = if (hovered) 6.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        link.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = DesktopUi.TextPrimary,
                    )
                    if (isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = DesktopUi.Accent,
                        ) {
                            Text(
                                "DEFAULT",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildString {
                        append(link.quality.toString())
                        append(" · ")
                        append(
                            if (link.isM3u8) {
                                "HLS Stream"
                            } else if (link.isDash) {
                                "DASH Stream"
                            } else {
                                "Direct MP4"
                            },
                        )
                        if (isDownloading) {
                            append(" · ⬇ Downloading")
                            if (downloadSpeed.isNotBlank()) {
                                append(" ($downloadSpeed)")
                            }
                        }
                    },
                    color = if (isDownloading) Color(0xFF64B5F6) else DesktopUi.Accent,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Checkbox to set / unset as default for this show
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onToggleDefault() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Checkbox(
                    checked = isDefault,
                    onCheckedChange = { onToggleDefault() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = DesktopUi.Accent,
                        uncheckedColor = DesktopUi.TextMuted,
                    ),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    "Default",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDefault) DesktopUi.Accent else DesktopUi.TextMuted,
                    fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedButton(
                onClick = onDownload,
                enabled = !isBusy && !isDownloading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDownloading) Color(0xFF64B5F6) else DesktopUi.TextPrimary,
                ),
            ) {
                Text(if (isDownloading) "⬇ Downloading..." else "⬇ Download")
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedButton(
                onClick = onCopy,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Copy")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onPlay,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesktopUi.Accent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play")
            }
        }
    }
}
