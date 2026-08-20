package com.lagradost.cloudstream3.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.lagradost.cloudstream3.desktop.player.ComposeMpvPlayer
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.common.storage.DesktopDataStore

@Composable
fun EmbeddedVideoPlayer(
    launchData: VideoLaunchData,
    onClose: () -> Unit,
) {
    var currentLinkIndex by remember { mutableStateOf(launchData.initialIndex) }
    val autoPlay = DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFinished by remember { mutableStateOf(false) }
    var replayTrigger by remember { mutableStateOf(0) }
    var currentStartPositionMs by remember { mutableStateOf(launchData.startPositionMs) }

    var lastPositionSec by remember { mutableStateOf(0L) }
    var lastDurationSec by remember { mutableStateOf(0L) }
    var lastSavedPositionSec by remember { mutableStateOf(0L) }
    val windowState = LocalWindowState.current
    val isFullscreen = windowState?.placement == WindowPlacement.Fullscreen

    DisposableEffect(Unit) {
        onDispose {
            if (lastDurationSec > 0 && lastPositionSec > 0) {
                val updatedHistory = launchData.history.copy(
                    position = lastPositionSec,
                    duration = lastDurationSec,
                    updateTime = System.currentTimeMillis(),
                )
                DesktopDataStore.setLastWatched(updatedHistory)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // --- Top AppBar for Back Button ---
        if (!isFullscreen && !isFinished) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.Black),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        launchData.onClosed?.invoke()
                        onClose()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Text(
                    text = launchData.title ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }

        // --- Video Engine / Loading / Error Container ---
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (errorMessage == null && !isFinished) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    key(currentLinkIndex, replayTrigger) {
                        ComposeMpvPlayer(
                            modifier = if (isLoading) Modifier.size(1.dp) else Modifier.fillMaxSize(),
                            link = launchData.links[currentLinkIndex],
                            title = launchData.title,
                            subtitles = launchData.subtitles,
                            startPositionMs = currentStartPositionMs,
                            onPlaybackReady = {
                                isLoading = false
                            },
                            onPlaybackError = { error ->
                                if (autoPlay && currentLinkIndex + 1 < launchData.links.size) {
                                    currentLinkIndex++
                                    isLoading = true
                                } else {
                                    isLoading = false
                                    if (launchData.onError != null) {
                                        launchData.onError.invoke(error)
                                        onClose()
                                    } else {
                                        errorMessage = error
                                    }
                                }
                            },
                            onFinished = {
                                val finalDuration = maxOf(lastDurationSec, launchData.history.duration)
                                if (finalDuration > 0) {
                                    val updatedHistory = launchData.history.copy(
                                        position = finalDuration,
                                        duration = finalDuration,
                                        updateTime = System.currentTimeMillis(),
                                    )
                                    DesktopDataStore.setLastWatched(updatedHistory)
                                }
                                isFinished = true
                            },
                            onFullscreenToggle = { fs ->
                                windowState?.placement = if (fs) {
                                    WindowPlacement.Fullscreen
                                } else {
                                    WindowPlacement.Floating
                                }
                            },
                            onPositionChange = { posMs, durMs ->
                                val currentPosSec = posMs / 1000L
                                val currentDurSec = durMs / 1000L
                                lastPositionSec = currentPosSec
                                lastDurationSec = currentDurSec

                                if (kotlin.math.abs(currentPosSec - lastSavedPositionSec) >= 5) {
                                    lastSavedPositionSec = currentPosSec
                                    val updatedHistory = launchData.history.copy(
                                        position = currentPosSec,
                                        duration = currentDurSec,
                                        updateTime = System.currentTimeMillis(),
                                    )
                                    DesktopDataStore.setLastWatched(updatedHistory)
                                }
                            },
                            onCloseRequest = {
                                launchData.onClosed?.invoke()
                                onClose()
                            },
                        )
                    }
                }
            }

            // --- Loading State ---
            if (isLoading && errorMessage == null && !isFinished) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp),
                        )
                        if (autoPlay && launchData.links.size > 1) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Trying link ${currentLinkIndex + 1} of ${launchData.links.size}...",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // --- Error State ---
            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Playback Failed",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Unknown error",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onClose) {
                        Text("Go Back")
                    }
                }
            }

            // --- Next Episode / Finished Screen Overlay ---
            if (isFinished) {
                NextEpisodeOverlay(
                    launchData = launchData,
                    onPlayNext = {
                        if (isFullscreen) {
                            windowState.placement = WindowPlacement.Floating
                        }
                        launchData.onClosed?.invoke()
                        launchData.nextEpisode?.onPlay?.invoke() ?: launchData.onPlayNext?.invoke()
                        onClose()
                    },
                    onReplay = {
                        currentStartPositionMs = 0L
                        isFinished = false
                        isLoading = true
                        replayTrigger++
                    },
                    onClose = {
                        if (isFullscreen) {
                            windowState.placement = WindowPlacement.Floating
                        }
                        launchData.onClosed?.invoke()
                        onClose()
                    },
                )
            }
        }
    }
}
