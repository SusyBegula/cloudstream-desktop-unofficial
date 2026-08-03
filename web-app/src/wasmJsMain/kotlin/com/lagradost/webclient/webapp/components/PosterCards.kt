package com.lagradost.webclient.webapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.webclient.api.SearchResultDto
import com.lagradost.webclient.api.WatchHistoryEntryDto
import com.lagradost.webclient.client.isHistoryCompleted
import com.lagradost.webclient.webapp.theme.AppearanceConfig
import com.lagradost.webclient.webapp.theme.DesktopUi

/**
 * Web-client port of desktop-app's ui/components/PosterCards.kt, retargeted onto DTOs.
 * The letterbox-fill uses a semi-transparent scrim instead of blur (Modifier.blur() is a
 * confirmed no-op on this Compose-for-Web target — see plan's spike findings).
 */
@Composable
fun PosterCard(
    item: SearchResultDto,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val width = when (gridScale) {
        "Compact" -> 150.dp
        "Large" -> 220.dp
        else -> 190.dp
    }

    Surface(
        modifier = modifier.width(width).posterHoverEffect().clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = DesktopUi.SurfaceCard,
        tonalElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            if (item.posterUrl != null) {
                WebAsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(DesktopUi.SurfaceElevated),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(DesktopUi.SurfaceElevated)) {
                    Text(
                        item.name.take(2).uppercase(),
                        color = DesktopUi.Accent,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.35f to Color.Black.copy(alpha = 0.7f),
                                1f to Color.Black.copy(alpha = 0.92f),
                            ),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Column {
                    item.quality?.let { quality ->
                        Box(
                            modifier = Modifier
                                .background(DesktopUi.Accent.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(quality.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        item.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun WatchHistoryCard(
    history: WatchHistoryEntryDto,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val width = when (gridScale) {
        "Compact" -> 120.dp
        "Large" -> 180.dp
        else -> 150.dp
    }

    Surface(
        modifier = modifier.width(width).posterHoverEffect().clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = DesktopUi.SurfaceCard,
        tonalElevation = 2.dp,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                if (history.posterUrl != null) {
                    WebAsyncImage(
                        model = history.posterUrl,
                        contentDescription = history.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(DesktopUi.SurfaceElevated)) {
                        Text("No Image", color = DesktopUi.TextMuted, modifier = Modifier.align(Alignment.Center))
                    }
                }

                if (history.durationMs > 0) {
                    val progress = if (isHistoryCompleted(history.positionMs, history.durationMs)) {
                        1f
                    } else {
                        (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                        color = DesktopUi.Accent,
                        trackColor = Color.Black.copy(alpha = 0.5f),
                    )
                }

                if (history.season != null && history.episode != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text("S${history.season} E${history.episode}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.85f), CircleShape),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove from history", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            PosterTitleLabel(title = history.name)
        }
    }
}
