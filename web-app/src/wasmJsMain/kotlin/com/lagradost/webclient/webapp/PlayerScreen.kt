package com.lagradost.webclient.webapp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.api.ExtractorLinkDto
import com.lagradost.webclient.api.PlayableStreamDto
import com.lagradost.webclient.api.WatchHistoryEntryDto
import com.lagradost.webclient.client.ApiClient
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.client.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList

private const val TOP_BAR_HEIGHT_PX = 56

/** Web-client port of desktop-app's EmbeddedVideoPlayer.kt: auto-fallback across links, periodic resume-position tracking. */
@Composable
fun PlayerScreen(api: ApiClient, screen: Screen.Player, nav: NavController) {
    var links by remember(screen) { mutableStateOf<List<ExtractorLinkDto>>(emptyList()) }
    var linkIndex by remember(screen) { mutableStateOf(0) }
    var stream by remember(screen) { mutableStateOf<PlayableStreamDto?>(null) }
    var error by remember(screen) { mutableStateOf<String?>(null) }

    LaunchedEffect(screen) {
        error = null
        try {
            links = api.links(screen.providerName, screen.dataUrl).toList()
                .filter { it.kind == "link" }
                .mapNotNull { it.link }
            if (links.isEmpty()) error = "No playable links found"
        } catch (e: Exception) {
            error = e.message ?: "Failed to fetch links"
        }
    }

    LaunchedEffect(links, linkIndex) {
        val link = links.getOrNull(linkIndex) ?: return@LaunchedEffect
        stream = try {
            api.resolve(link)
        } catch (e: Exception) {
            null
        }
        if (stream == null && linkIndex < links.size - 1) {
            linkIndex++
        } else if (stream == null) {
            error = "All playback links failed"
        }
    }

    // Poll playback state: auto-advance to next link on error/end, periodically save resume position.
    LaunchedEffect(stream) {
        if (stream == null) return@LaunchedEffect
        var lastSaveMs = 0L
        while (true) {
            delay(2000)
            if (VideoPlayerState.isDoneOrErrored()) {
                if (linkIndex < links.size - 1) {
                    linkIndex++
                    stream = null
                } else {
                    nav.back()
                }
                break
            }
            val position = VideoPlayerState.currentPositionMs()
            val duration = VideoPlayerState.durationMs()
            if (duration > 0 && kotlin.math.abs(position - lastSaveMs) > 4000) {
                lastSaveMs = position
                runCatching {
                    api.patchHistory(
                        WatchHistoryEntryDto(
                            provider = screen.providerName,
                            url = screen.dataUrl,
                            name = screen.title,
                            positionMs = position,
                            durationMs = duration,
                            updatedAt = 0L,
                        ),
                    )
                }
            }
        }
    }

    Row(Modifier.height(TOP_BAR_HEIGHT_PX.dp).padding(8.dp)) {
        IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(screen.title, modifier = Modifier.padding(start = 8.dp))
        IconButton(onClick = { VideoPlayerState.requestFullscreen() }) { Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen") }
    }

    when {
        error != null -> Text(error!!, Modifier.padding(16.dp))
        stream == null -> CircularProgressIndicator(Modifier.padding(16.dp))
        else -> VideoOverlay(stream!!.proxyUrl, stream!!.mimeType, topOffsetPx = TOP_BAR_HEIGHT_PX)
    }
}
