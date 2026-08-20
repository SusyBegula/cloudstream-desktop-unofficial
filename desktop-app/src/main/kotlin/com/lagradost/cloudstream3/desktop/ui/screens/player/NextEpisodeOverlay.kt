package com.lagradost.cloudstream3.desktop.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.ui.NextEpisodeData
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.delay

@Composable
fun NextEpisodeOverlay(
    launchData: VideoLaunchData,
    onPlayNext: () -> Unit,
    onReplay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nextEpisode = launchData.nextEpisode
    val hasNextEpisode = nextEpisode != null || launchData.onPlayNext != null

    val isAutoPlayEnabled = remember {
        DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY_NEXT_EPISODE) ?: true
    }
    val totalCountdownSeconds = remember {
        (DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_NEXT_EPISODE_SECONDS)?.toIntOrNull() ?: 10)
            .coerceIn(3, 60)
    }

    var secondsRemaining by remember { mutableStateOf(totalCountdownSeconds) }
    var isCountdownActive by remember { mutableStateOf(hasNextEpisode && isAutoPlayEnabled) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Auto-play Countdown Timer
    LaunchedEffect(isCountdownActive) {
        if (isCountdownActive) {
            while (secondsRemaining > 0 && isCountdownActive) {
                delay(1000L)
                if (isCountdownActive) {
                    secondsRemaining--
                }
            }
            if (isCountdownActive && secondsRemaining <= 0) {
                onPlayNext()
            }
        }
    }

    val backdropUrl = nextEpisode?.backdropUrl
        ?: nextEpisode?.posterUrl
        ?: launchData.history.posterUrl

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Enter, Key.Spacebar -> {
                            if (hasNextEpisode) {
                                onPlayNext()
                            } else {
                                onReplay()
                            }
                            true
                        }
                        Key.Escape, Key.Q -> {
                            onClose()
                            true
                        }
                        Key.R -> {
                            onReplay()
                            true
                        }
                        Key.C, Key.P -> {
                            if (isCountdownActive) {
                                isCountdownActive = false
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // --- Ambient Blurred Backdrop ---
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp),
            )
        }

        // Dark Vignette & Scrim Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )

        // --- Main Content Container ---
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 760.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (hasNextEpisode) {
                // --- Top Header Tag & Countdown ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DesktopUi.Accent.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = "UP NEXT",
                            color = DesktopUi.Accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    if (isCountdownActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Auto-playing in ${secondsRemaining}s",
                                color = DesktopUi.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(
                                onClick = { isCountdownActive = false },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "Cancel Countdown",
                                    tint = DesktopUi.TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Next Episode Card ---
                val nextThumb = nextEpisode?.posterUrl ?: launchData.history.posterUrl
                val nextShowName = nextEpisode?.showName ?: launchData.history.showName
                val epNum = nextEpisode?.episodeNumber
                val seasonNum = nextEpisode?.seasonNumber
                val nextEpTitle = nextEpisode?.title ?: "Next Episode"
                val nextDescription = nextEpisode?.description

                val seasonEpLabel = buildString {
                    if (seasonNum != null) append("SEASON $seasonNum ")
                    if (seasonNum != null && epNum != null) append("• ")
                    if (epNum != null) append("EPISODE $epNum")
                }.trim()

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DesktopUi.SurfaceCard.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(24.dp, shape = RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayNext,
                        ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Thumbnail Preview
                            Box(
                                modifier = Modifier
                                    .width(220.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!nextThumb.isNullOrBlank()) {
                                    AsyncImage(
                                        model = nextThumb,
                                        contentDescription = nextEpTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                // Play Badge Overlay on Thumbnail
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Episode Info Details
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (nextShowName.isNotBlank()) {
                                    Text(
                                        text = nextShowName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DesktopUi.TextMuted,
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (seasonEpLabel.isNotBlank()) {
                                    Text(
                                        text = seasonEpLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DesktopUi.Accent,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Text(
                                    text = nextEpTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = DesktopUi.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (!nextDescription.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = nextDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DesktopUi.TextMuted,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp,
                                    )
                                }
                            }
                        }

                        // Countdown Progress Bar
                        if (isCountdownActive) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val progress by animateFloatAsState(
                                targetValue = (totalCountdownSeconds - secondsRemaining).toFloat() / totalCountdownSeconds.toFloat(),
                                animationSpec = tween(durationMillis = 900),
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = DesktopUi.Accent,
                                trackColor = Color.White.copy(alpha = 0.1f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- Action Buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Play Next Button (Primary)
                    Button(
                        onClick = onPlayNext,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopUi.Accent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCountdownActive) "Play Now ($secondsRemaining)" else "Play Next Episode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }

                    // Replay Current Episode Button
                    OutlinedButton(
                        onClick = onReplay,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DesktopUi.TextPrimary,
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Replay", fontWeight = FontWeight.SemiBold)
                    }

                    // Close Player Button
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DesktopUi.TextMuted,
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // --- No Next Episode (Movie or Series Finale) ---
                val posterUrl = launchData.history.posterUrl
                val showName = launchData.title ?: launchData.history.showName

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DesktopUi.SurfaceCard.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(24.dp, shape = RoundedCornerShape(16.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = showName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .shadow(12.dp, shape = RoundedCornerShape(10.dp)),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text(
                            text = "Playback Completed",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DesktopUi.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )

                        if (showName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = showName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = DesktopUi.TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = onReplay,
                                modifier = Modifier.height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesktopUi.Accent,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Replay Video", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onClose,
                                modifier = Modifier.height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = DesktopUi.TextPrimary,
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Close Player", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
