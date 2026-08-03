package com.lagradost.webclient.webapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.api.BookmarkDto
import com.lagradost.webclient.api.EpisodeDto
import com.lagradost.webclient.client.ApiClient
import com.lagradost.webclient.client.DetailsViewModel
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.client.Screen
import com.lagradost.webclient.webapp.components.WatchProgressIndicator
import com.lagradost.webclient.webapp.components.WebAsyncImage
import com.lagradost.webclient.webapp.components.glassPanel
import com.lagradost.webclient.webapp.theme.DesktopUi
import kotlinx.coroutines.launch

/** Web-client port of desktop-app's DetailsScreen.kt (haze glassmorphism replaced by a plain glassPanel scrim — see plan's spike findings). */
@Composable
fun DetailsScreen(vm: DetailsViewModel, screen: Screen.Details, nav: NavController, api: ApiClient) {
    val details by vm.details.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val scope = rememberCoroutineScope()

    var isBookmarked by remember(screen) { mutableStateOf(false) }
    LaunchedEffect(screen) {
        isBookmarked = runCatching { api.getBookmarks().bookmarks }.getOrDefault(emptyList())
            .any { it.provider == screen.providerName && it.url == screen.url }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            error != null -> Text("Error: $error", modifier = Modifier.align(Alignment.Center))
            details != null -> {
                val d = details!!
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(320.dp)) {
                            val backdrop = d.backgroundPosterUrl ?: d.posterUrl
                            if (backdrop != null) {
                                WebAsyncImage(
                                    model = backdrop,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    Modifier.fillMaxSize().background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                        ),
                                    ),
                                )
                            }

                            Row(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp)
                                    .glassPanel(shape = RoundedCornerShape(20.dp))
                                    .padding(16.dp),
                            ) {
                                d.posterUrl?.let { poster ->
                                    WebAsyncImage(
                                        model = poster,
                                        contentDescription = d.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(100.dp).height(150.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                    Spacer(Modifier.width(16.dp))
                                }
                                Column {
                                    Text(d.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                    Row(Modifier.padding(top = 4.dp)) {
                                        d.year?.let { Text("$it", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(end = 12.dp)) }
                                        d.duration?.let { Text("${it}min", color = Color.White.copy(alpha = 0.8f)) }
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            isBookmarked = !isBookmarked
                                            if (isBookmarked) {
                                                api.putBookmark(BookmarkDto(screen.providerName, screen.url, d.name, d.posterUrl, 0L))
                                            } else {
                                                api.deleteBookmark(screen.providerName, screen.url)
                                            }
                                        }
                                    }) {
                                        Icon(
                                            if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (isBookmarked) DesktopUi.Accent else Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            d.plot?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DesktopUi.TextMuted) }
                            d.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                                Text(tags.joinToString(" • "), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
                            }
                        }
                    }

                    if (d.kind == "series" && !d.episodes.isNullOrEmpty()) {
                        items(d.episodes!!) { ep ->
                            EpisodeRow(ep, onClick = { nav.navigate(Screen.Player(screen.providerName, ep.data, "${d.name} - ${ep.name.orEmpty()}")) })
                        }
                    } else {
                        item {
                            d.dataUrl?.let { dataUrl ->
                                Button(
                                    onClick = { nav.navigate(Screen.Player(screen.providerName, dataUrl, d.name)) },
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Text("Play", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: EpisodeDto, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        color = DesktopUi.SurfaceCard,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DesktopUi.Accent)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    "S${ep.season ?: 1}E${ep.episode ?: 0} ${ep.name.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopUi.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ep.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = DesktopUi.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
