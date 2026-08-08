package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HeroMeta(
    val backdropUrl: String?,
    val tags: List<String>,
    val plot: String?,
    val score: String?,
    val year: Int?,
)

object HeroCache {
    val cache = java.util.concurrent.ConcurrentHashMap<String, HeroMeta>()
}

fun cleanHeroTitle(raw: String): String {
    val coreTitle = raw.split(Regex("[\\(\\[\\{\\|]")).firstOrNull() ?: raw
    val s = coreTitle
        .replace(Regex("\\b(WEB-DL|HDTC|HDRip|BluRay|CAM|DVDSCR)\\b.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("(?i)Anime Series$"), "")
        .trim()
    return s.ifBlank { raw }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeHeroCarousel(items: List<SearchResponse>, provider: MainAPI?, onItemClick: (SearchResponse, String?) -> Unit) {
    if (items.isEmpty()) return

    val displayItems = items.take(10)
    val maxPages = displayItems.size * 1000
    val initialPage = maxPages / 2
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { maxPages })
    val metaMap = remember { mutableStateMapOf<String, HeroMeta>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)
            if (!pagerState.isScrollInProgress) {
                try {
                    pagerState.animateScrollToPage(
                        page = pagerState.currentPage + 1,
                        animationSpec = tween(durationMillis = 1200),
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (!isActive) throw e
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val pagesToFetch = listOf(
            pagerState.currentPage % displayItems.size,
            (pagerState.currentPage + 1) % displayItems.size,
        ).distinct()

        for (pageIdx in pagesToFetch) {
            val item = displayItems[pageIdx]
            val cacheKey = "${provider?.name}_${item.url}"
            if (metaMap.containsKey(item.url)) continue
            val existing = HeroCache.cache[cacheKey]
            if (existing != null) {
                metaMap[item.url] = existing
                continue
            }
            scope.launch(Dispatchers.IO) {
                try {
                    var backdropUrl: String? = null
                    var tags: List<String> = emptyList()
                    var plot: String? = null
                    var score: String? = null
                    var year: Int? = null

                    try {
                        val enriched = provider?.load(item.url)
                        if (enriched != null) {
                            backdropUrl = enriched.backgroundPosterUrl
                            tags = enriched.tags?.take(4) ?: emptyList()
                            plot = enriched.plot?.take(200)
                            score = enriched.score?.toStringNull(0.5, 10)
                            year = enriched.year
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        AppLogger.w("HomeScreen", "Error fetching native provider metadata for ${item.name}", e)
                    }

                    if (backdropUrl == null || tags.isEmpty() || score == null) {
                        try {
                            val tmdb = object : com.lagradost.cloudstream3.metaproviders.TmdbProvider() {
                                override val useMetaLoadResponse = true
                            }

                            val cleanName = cleanHeroTitle(item.name)
                            val results = tmdb.search(cleanName, 1)?.items ?: emptyList()
                            val strippedCleanName = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")

                            val itemYear = when (item) {
                                is com.lagradost.cloudstream3.MovieSearchResponse -> item.year
                                is com.lagradost.cloudstream3.TvSeriesSearchResponse -> item.year
                                else -> null
                            }

                            val match = results.firstOrNull { result ->
                                val resultYear = when (result) {
                                    is com.lagradost.cloudstream3.MovieSearchResponse -> result.year
                                    is com.lagradost.cloudstream3.TvSeriesSearchResponse -> result.year
                                    else -> null
                                }
                                val strippedResultName = result.name.replace(Regex("[^a-zA-Z0-9]"), "")

                                if (strippedResultName.equals(strippedCleanName, ignoreCase = true) && strippedCleanName.isNotEmpty()) {
                                    if (result is com.lagradost.cloudstream3.MovieSearchResponse && resultYear != null && itemYear != null && resultYear != itemYear) {
                                        false // Year conflicts
                                    } else {
                                        true // Name matches and year doesn't conflict
                                    }
                                } else {
                                    false
                                }
                            }

                            if (match != null) {
                                val tmdbEnriched = tmdb.load(match.url)
                                if (tmdbEnriched != null) {
                                    if (backdropUrl == null) {
                                        backdropUrl = tmdbEnriched.backgroundPosterUrl?.replace("/w500/", "/original/")
                                    } else {
                                        backdropUrl = backdropUrl?.replace("/w500/", "/original/")
                                    }

                                    if (tags.isEmpty()) tags = tmdbEnriched.tags?.take(4) ?: emptyList()
                                    if (plot == null) plot = tmdbEnriched.plot?.take(200)
                                    if (score == null) score = tmdbEnriched.score?.toStringNull(0.5, 10)
                                    if (year == null) year = tmdbEnriched.year
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            AppLogger.w("HomeScreen", "Error fetching TMDB enrichment for ${item.name}", e)
                        }
                    }

                    val meta = HeroMeta(backdropUrl, tags, plot, score, year)
                    HeroCache.cache[cacheKey] = meta
                    metaMap[item.url] = meta
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    AppLogger.e("HomeScreen", "Failed to enrich hero metadata for ${item.name}", e)
                }
            }
        }
    }

    // Always use a deep cinematic dark for the hero overlays — works in both light & dark mode
    val heroFade = Color(0xFF0C0C16)
    // The actual surface color the content rows sit on (SurfaceCard via MaterialTheme.colorScheme.surface)
    val surfaceColor = LocalDesktopTheme.current.SurfaceCard

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .padding(bottom = 0.dp)
            .graphicsLayer { clip = false },
    ) {
        // Bottom bleed gradient — transitions from cinematic dark into the actual page surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .offset(y = 80.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to heroFade,
                        0.5f to surfaceColor.copy(alpha = 0.85f),
                        1.0f to surfaceColor,
                    ),
                ),
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val realPage = page % displayItems.size
            val item = displayItems[realPage]
            val posterUrl = provider?.fixUrlNull(item.posterUrl)
            val meta = metaMap[item.url]

            val backdropAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (meta?.backdropUrl != null) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(1000),
                label = "backdropFade",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                // Blurred portrait poster — immediate placeholder
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.20f),
                    )
                }

                // Widescreen backdrop — full image visible, no crop
                if (meta?.backdropUrl != null) {
                    AsyncImage(
                        model = meta.backdropUrl,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().alpha(backdropAlpha),
                    )
                }

                // Vertical gradient — always cinematic dark fade at bottom
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.60f to heroFade.copy(alpha = 0.40f),
                            0.75f to heroFade.copy(alpha = 0.82f),
                            0.88f to heroFade.copy(alpha = 0.97f),
                            1.0f to heroFade,
                        ),
                    ),
                )
                // Horizontal vignette — cinematic left-side darkening for text readability
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0.0f to heroFade.copy(alpha = 0.85f),
                            0.15f to heroFade.copy(alpha = 0.60f),
                            0.50f to Color.Transparent,
                        ),
                    ),
                )

                // Foreground content (Metadata text directly on backdrop)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 56.dp, end = 56.dp, bottom = 90.dp, top = 24.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    // Left Side: Clear Poster Image next to the text
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = "Poster",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(220.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .shadow(24.dp, RoundedCornerShape(16.dp)),
                        )
                        Spacer(modifier = Modifier.width(48.dp))
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(0.65f), // Take up remaining space comfortably
                    ) {
                        // Content-type badge
                        item.type?.let { tvType ->
                            val typeLabel = when (tvType) {
                                com.lagradost.cloudstream3.TvType.Movie -> "MOVIE"
                                com.lagradost.cloudstream3.TvType.TvSeries -> "SERIES"
                                com.lagradost.cloudstream3.TvType.Anime -> "ANIME"
                                com.lagradost.cloudstream3.TvType.AnimeMovie -> "ANIME FILM"
                                com.lagradost.cloudstream3.TvType.Live -> "LIVE"
                                else -> tvType.name.uppercase()
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    typeLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Title
                        Text(
                            text = cleanHeroTitle(item.name),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 48.sp,
                        )

                        // Rating & Year
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (meta?.score != null) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFFFD700), // Gold
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = meta.score,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                            if (meta?.year != null) {
                                Text(
                                    text = meta.year.toString(),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        // Genre pills
                        if (!meta?.tags.isNullOrEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                meta!!.tags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(tag, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Plot synopsis
                        if (!meta?.plot.isNullOrBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = meta!!.plot!!,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 22.sp,
                            )
                        }

                        Spacer(Modifier.height(26.dp))

                        Button(
                            onClick = { onItemClick(item, meta?.backdropUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play / View Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // Left/Right buttons
        IconButton(
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = pagerState.currentPage - 1,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000),
                    )
                }
            },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape),
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
        }

        IconButton(
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = pagerState.currentPage + 1,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000),
                    )
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape),
        ) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
        }

        // Pill-style page indicators
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(displayItems.size) { dotIndex ->
                val isSelected = (pagerState.currentPage % displayItems.size) == dotIndex
                val dotWidth by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 6.dp,
                    animationSpec = androidx.compose.animation.core.spring(stiffness = 600f),
                    label = "dot_w_$dotIndex",
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(if (isSelected) DesktopUi.Accent else DesktopUi.TextMuted.copy(alpha = 0.5f)),
                )
            }
        }
    }
}
