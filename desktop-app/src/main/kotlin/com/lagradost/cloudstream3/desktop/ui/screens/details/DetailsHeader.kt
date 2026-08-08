package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

@Composable
fun DetailsBackdrop(provider: MainAPI, data: LoadResponse, scrollState: LazyListState, hazeState: HazeState) {
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(constraints.maxWidth, 0) {
                    placeable.placeRelative(0, 0)
                }
            }
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .haze(state = hazeState),
    ) {
        // Full backdrop image
        val bgUrl = provider.fixUrlNull(data.backgroundPosterUrl) ?: provider.fixUrlNull(data.posterUrl)
        if (bgUrl != null) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .run { if (data.backgroundPosterUrl == null) this.blur(40.dp) else this },
            )
        }

        // Gradient: transparent top -> solid background bottom
        val bgColor = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.25f to bgColor.copy(alpha = 0.25f),
                            0.50f to bgColor.copy(alpha = 0.80f),
                            0.75f to bgColor.copy(alpha = 0.98f),
                            1.00f to bgColor,
                        ),
                    ),
                ),
        )
        // Left vignette so text pops
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to bgColor.copy(alpha = 0.55f),
                            0.60f to Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsMetadata(provider: MainAPI, data: LoadResponse, hazeState: HazeState) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .hazeChild(
                    state = hazeState,
                    shape = RoundedCornerShape(24.dp),
                    style = HazeStyle(
                        tint = Color(0xFF0C0C14).copy(alpha = 0.60f),
                        blurRadius = 4.dp,
                    ),
                )
                .padding(32.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Poster
                AsyncImage(
                    model = provider.fixUrlNull(data.posterUrl),
                    contentDescription = data.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(160.dp)
                        .height(240.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.width(24.dp))

                // Metadata Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = data.name,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                data.year?.let {
                                    Text(it.toString(), style = MaterialTheme.typography.titleSmall, color = Color.LightGray)
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                data.duration?.let {
                                    Text("${it}m", style = MaterialTheme.typography.titleSmall, color = Color.LightGray)
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                data.score?.let {
                                    Text("★ ${it.toString(10)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }

                        // Bookmark button
                        val bookmarkId = "${provider.name}_${data.url.hashCode()}"
                        var isBookmarked by remember { mutableStateOf(DesktopDataStore.isBookmarked(bookmarkId)) }
                        IconButton(
                            onClick = {
                                if (isBookmarked) {
                                    DesktopDataStore.removeBookmark(bookmarkId)
                                } else {
                                    DesktopDataStore.addBookmark(
                                        DesktopBookmark(
                                            id = bookmarkId,
                                            name = data.name,
                                            url = data.url,
                                            apiName = provider.name,
                                            posterUrl = data.posterUrl,
                                        ),
                                    )
                                }
                                isBookmarked = !isBookmarked
                            },
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color.Red else Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    // Tags
                    if (!data.tags.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            data.tags!!.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        tag,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    // Plot
                    data.plot?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                        )
                    }
                }
            }

            // Cast
            if (!data.actors.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Cast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(12.dp))
                val castScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.foundation.lazy.LazyRow(
                    state = castScrollState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            castScrollState.dispatchRawDelta(-dragAmount)
                        }
                    },
                ) {
                    items(data.actors!!) { actor ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                            AsyncImage(
                                model = provider.fixUrlNull(actor.actor.image),
                                contentDescription = actor.actor.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(actor.actor.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            actor.roleString?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
