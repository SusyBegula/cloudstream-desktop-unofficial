package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.lagradost.cloudstream3.desktop.download.ActiveDownload
import com.lagradost.cloudstream3.desktop.download.DownloadStatus
import com.lagradost.cloudstream3.desktop.download.FfmpegDownloadManager
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.DownloadedItemRecord
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ComposeDownloadsScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val playVideo = LocalVideoPlayer.current

    val activeDownloads by FfmpegDownloadManager.downloadsFlow.collectAsState()
    val inProgress = activeDownloads.filter { it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued }

    val downloadUpdates by DesktopDataStore.downloadUpdates.collectAsState()
    val downloadedItems = remember(downloadUpdates) {
        DesktopDataStore.getAllDownloads().filter { File(it.localFilePath).exists() }
    }

    var itemToDelete by remember { mutableStateOf<DownloadedItemRecord?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(downloadedItems, searchQuery) {
        if (searchQuery.isBlank()) {
            downloadedItems
        } else {
            downloadedItems.filter {
                it.showName.contains(searchQuery, ignoreCase = true) ||
                it.episodeTitle.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val totalBytesOnDisk = remember(downloadedItems) {
        downloadedItems.sumOf { it.totalBytes }
    }
    val formattedTotalSize = remember(totalBytesOnDisk) {
        formatBytes(totalBytesOnDisk)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesktopUi.TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (downloadedItems.isEmpty() && inProgress.isEmpty()) {
                        "No downloads yet · Saved media will appear here for offline viewing"
                    } else {
                        "${downloadedItems.size} downloaded (${formattedTotalSize}) · ${inProgress.size} active"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopUi.TextMuted,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(PlatformPaths.downloadsDir)
                            }
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Folder")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (inProgress.isEmpty() && downloadedItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = DesktopUi.SurfaceElevated,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = DesktopUi.Accent.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "No Downloaded Content",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DesktopUi.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Download movies and episodes from any stream link to watch offline anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DesktopUi.TextMuted,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { navController.navigate(Screen.Home) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.Accent),
                    ) {
                        Text("Browse Shows")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // Active Downloads Section
                if (inProgress.isNotEmpty()) {
                    item {
                        Text(
                            "Downloading (${inProgress.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopUi.TextPrimary,
                        )
                    }

                    items(inProgress, key = { it.id }) { item ->
                        ActiveDownloadCard(
                            download = item,
                            onCancel = { FfmpegDownloadManager.cancelDownload(item.id) },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = DesktopUi.Divider)
                    }
                }

                // Completed Downloads Section
                if (downloadedItems.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Downloaded Media (${downloadedItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopUi.TextPrimary,
                            )
                        }
                    }

                    items(filteredItems, key = { it.id }) { item ->
                        DownloadedItemCard(
                            item = item,
                            onPlay = {
                                coroutineScope.launch {
                                    val link = newExtractorLink(
                                        source = "Offline File",
                                        name = "Local Download",
                                        url = item.localFilePath,
                                    )
                                    playVideo(
                                        VideoLaunchData(
                                            links = listOf(link),
                                            initialIndex = 0,
                                            title = "${item.showName} - ${item.episodeTitle}",
                                            subtitles = emptyList(),
                                            startPositionMs = 0L,
                                            history = WatchHistory(
                                                parentId = item.showUrl.hashCode().toString(),
                                                showName = item.showName,
                                                showUrl = item.showUrl,
                                                apiName = "Offline",
                                                posterUrl = item.posterUrl,
                                                episode = item.episode,
                                                season = item.season,
                                                episodeId = item.episodeId,
                                                position = 0L,
                                                duration = 0L,
                                            ),
                                        ),
                                    )
                                }
                            },
                            onOpenFolder = {
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        val parent = File(item.localFilePath).parentFile
                                        if (parent != null && parent.exists()) {
                                            Desktop.getDesktop().open(parent)
                                        }
                                    }
                                } catch (_: Exception) {}
                            },
                            onDelete = { itemToDelete = item },
                        )
                    }
                }
            }
        }
    }

    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Download?") },
            text = {
                Text("Are you sure you want to delete \"${target.showName} - ${target.episodeTitle}\" from your disk? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        DesktopDataStore.removeDownload(target.id)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B)),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ActiveDownloadCard(
    download: ActiveDownload,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
        border = BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DesktopUi.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (download.posterUrl != null) {
                    AsyncImage(
                        model = download.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, tint = DesktopUi.Accent)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    download.showName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesktopUi.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    download.episodeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopUi.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val statusStr = if (download.speed.isNotBlank()) {
                        "Speed: ${download.speed}"
                    } else {
                        "Downloading via FFmpeg..."
                    }
                    Text(
                        statusStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64B5F6),
                        fontWeight = FontWeight.Medium,
                    )
                    if (download.totalBytes > 0) {
                        Text(
                            formatBytes(download.totalBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = DesktopUi.TextMuted,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFFF8A8A))
            }
        }
    }
}

@Composable
private fun DownloadedItemCard(
    item: DownloadedItemRecord,
    onPlay: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DesktopUi.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (item.posterUrl != null) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = DesktopUi.TextMuted)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.showName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesktopUi.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    item.episodeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopUi.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${formatBytes(item.totalBytes)} · ${formatDate(item.downloadDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopUi.TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenFolder) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open Folder", tint = DesktopUi.TextMuted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFFF8A8A))
                }
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopUi.Accent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        "%.2f GB".format(Locale.US, mb / 1024)
    } else {
        "%.1f MB".format(Locale.US, mb)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
