package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.screens.details.*
import com.lagradost.player.impl.PlayerLinkHandler
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDetailsScreen(navController: NavController, provider: MainAPI, url: String, preloadedName: String? = null, preloadedPoster: String? = null, preloadedBg: String? = null) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember(url) { DetailsViewModel(coroutineScope, provider, url, preloadedName, preloadedPoster, preloadedBg) }

    val response by viewModel.response.collectAsState()
    val fakeData by viewModel.fakeData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val activeLinkData by viewModel.activeLinkData.collectAsState()
    val isPanelOpen by viewModel.isPanelOpen.collectAsState()
    val enrichmentTrigger by viewModel.enrichmentTrigger.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Details Content
            if (isLoading) {
                if (fakeData != null) {
                    DetailsContent(navController, provider, fakeData!!, enrichmentTrigger, isLoading = true, onPlay = viewModel::openLinksPanel, onEnrichEpisodes = viewModel::enrichVisibleEpisodes)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (response != null) {
                DetailsContent(navController, provider, response!!, enrichmentTrigger, isLoading = false, onPlay = viewModel::openLinksPanel, onEnrichEpisodes = viewModel::enrichVisibleEpisodes)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (errorMessage != null) "Error: $errorMessage" else "Failed to load details.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.goBack() }) {
                            Text("Go Back")
                        }
                    }
                }
            }

            // 2. Stream Links Modal Dialog
            if (isPanelOpen && activeLinkData != null) {
                Dialog(
                    onDismissRequest = { viewModel.closeLinksPanel() },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.closeLinksPanel() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(min = 520.dp, max = 680.dp)
                                .fillMaxHeight(0.85f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* keep clicks inside dialog */ },
                                )
                                .shadow(32.dp, shape = RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            color = DesktopUi.SurfaceCard,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        ) {
                            activeLinkData?.let { (linkProvider, linkUrl, linkHistory, linkOnPlayNext) ->
                                LinksModal(
                                    provider = linkProvider,
                                    dataUrl = linkUrl,
                                    history = linkHistory,
                                    onClose = { viewModel.closeLinksPanel() },
                                    onPlayNext = linkOnPlayNext,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsContent(
    navController: NavController,
    provider: MainAPI,
    data: LoadResponse,
    enrichmentTrigger: Int,
    isLoading: Boolean = false,
    onPlay: (LinksPanelRequest) -> Unit,
    onEnrichEpisodes: (List<Episode>) -> Unit = {},
) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hazeState = remember { HazeState() }

    val historyUpdatesVal = com.lagradost.common.storage.DesktopDataStore.historyUpdates.collectAsState().value
    val latestHistory = remember(data.url, historyUpdatesVal) {
        com.lagradost.common.storage.DesktopDataStore.getLatestWatchHistoryForShow(data.url)
    }

    val dubStatuses = remember(data) { if (data is AnimeLoadResponse) data.episodes.keys.toList() else emptyList() }
    var selectedDub by remember(latestHistory?.episodeId, data) {
        mutableStateOf(
            if (data is AnimeLoadResponse) {
                if (latestHistory != null) {
                    dubStatuses.find { dub -> data.episodes[dub]?.any { it.data == latestHistory.episodeId } == true } ?: dubStatuses.firstOrNull()
                } else {
                    dubStatuses.firstOrNull()
                }
            } else {
                null
            },
        )
    }

    val lastSavedSeason = remember(data.url) {
        com.lagradost.common.storage.DesktopDataStore.getKey<Int>("last_season_${provider.name}_${data.url}")
    }

    val seasons = remember(data, enrichmentTrigger) {
        when (data) {
            is TvSeriesLoadResponse -> {
                normalizeTvSeriesEpisodes(data)
                data.episodes.mapNotNull { it.season }.distinct().sorted()
            }
            is AnimeLoadResponse -> {
                normalizeAnimeEpisodes(data)
                data.episodes.values.flatten().mapNotNull { it.season }.distinct().sorted()
            }
            else -> emptyList()
        }
    }
    var selectedSeason by remember(latestHistory?.season, lastSavedSeason, data, seasons) {
        mutableStateOf(
            latestHistory?.season?.takeIf { it in seasons }
                ?: lastSavedSeason?.takeIf { it in seasons }
                ?: seasons.firstOrNull()
                ?: 1
        )
    }

    LaunchedEffect(seasons, data) {
        if (seasons.isNotEmpty() && selectedSeason !in seasons) {
            val validSeason = latestHistory?.season?.takeIf { it in seasons }
                ?: lastSavedSeason?.takeIf { it in seasons }
                ?: seasons.first()
            selectedSeason = validSeason
            com.lagradost.common.storage.DesktopDataStore.setKey("last_season_${provider.name}_${data.url}", validSeason)
        }
    }

    val showHistory = remember(data.url, historyUpdatesVal) {
        com.lagradost.common.storage.DesktopDataStore.getAllWatchHistory()
            .filter { it.showUrl == data.url }
            .associateBy { it.episodeId ?: it.parentId }
    }

    var isSortAscending by remember(data.url) { mutableStateOf(true) }
    var episodeSearchQuery by remember(data.url) { mutableStateOf("") }
    var isSearchActive by remember(data.url) { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val pageSize = 6

    val allEpisodes = remember(data, selectedSeason, selectedDub, isSortAscending, episodeSearchQuery, seasons, enrichmentTrigger) {
        val rawList = when (data) {
            is TvSeriesLoadResponse -> data.episodes
            is AnimeLoadResponse -> selectedDub?.let { data.episodes[it] } ?: emptyList()
            else -> emptyList()
        }
        rawList
            .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) || seasons.isEmpty() }
            .let { list ->
                if (isSortAscending) {
                    list.sortedBy { it.episode ?: Int.MAX_VALUE }
                } else {
                    list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                }
            }
            .let { list ->
                if (episodeSearchQuery.isBlank()) {
                    list
                } else {
                    val q = episodeSearchQuery.trim()
                    list.filter { ep ->
                        ep.name?.contains(q, ignoreCase = true) == true ||
                            ep.episode?.toString()?.contains(q) == true
                    }
                }
            }
    }

    val totalPages = (allEpisodes.size + pageSize - 1) / pageSize

    var currentPage by remember(selectedSeason, selectedDub, episodeSearchQuery, data.url) {
        val resumeIdx = allEpisodes.indexOfFirst { it.data == latestHistory?.episodeId }
        val initialPage = if (resumeIdx >= 0) (resumeIdx / pageSize) + 1 else 1
        mutableStateOf(initialPage)
    }

    val pagedEpisodes = remember(allEpisodes, currentPage, totalPages, enrichmentTrigger) {
        if (allEpisodes.isEmpty()) emptyList()
        else {
            val page = currentPage.coerceIn(1, maxOf(1, totalPages))
            val start = ((page - 1) * pageSize).coerceIn(0, allEpisodes.size)
            val end = (start + pageSize).coerceIn(0, allEpisodes.size)
            allEpisodes.subList(start, end)
        }
    }

    LaunchedEffect(pagedEpisodes, data.url) {
        if (pagedEpisodes.isNotEmpty()) {
            onEnrichEpisodes(pagedEpisodes)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    DetailsBackdrop(provider = provider, data = data, scrollState = scrollState, hazeState = hazeState)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 2.0f))
                        DetailsMetadata(provider = provider, data = data, hazeState = hazeState)
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(modifier = Modifier.widthIn(max = 1000.dp).padding(horizontal = 16.dp)) {
                            when (data) {
                                is MovieLoadResponse, is TorrentLoadResponse -> {
                                    if (latestHistory != null && latestHistory.duration > 0) {
                                        com.lagradost.cloudstream3.desktop.ui.components.WatchProgressIndicator(
                                            position = latestHistory.position,
                                            duration = latestHistory.duration,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    Button(
                                        onClick = {
                                            val url = if (data is TorrentLoadResponse) (data.torrent ?: data.magnet ?: "") else (data as MovieLoadResponse).dataUrl
                                            val ep = provider.newEpisode(url) {
                                                name = data.name
                                                posterUrl = data.posterUrl
                                            }
                                            navigateToPlay(provider, data, ep, onPlay)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val canResume = latestHistory != null &&
                                            PlayerLinkHandler.resumeStartSeconds(latestHistory.position, latestHistory.duration) > 0
                                        val typeName = if (data is TorrentLoadResponse) "Torrent" else "Movie"
                                        Text(if (canResume) "Resume $typeName" else "Play $typeName", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                                is LiveStreamLoadResponse -> {
                                    Button(
                                        onClick = {
                                            val ep = provider.newEpisode(data.dataUrl) {
                                                name = data.name
                                                posterUrl = data.posterUrl
                                            }
                                            navigateToPlay(provider, data, ep, onPlay)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Watch Live", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                                is TvSeriesLoadResponse -> {
                                    // Continue Watching + Play Next buttons
                                    if (latestHistory != null) {
                                        val resumeEp = data.episodes.find { it.data == latestHistory.episodeId }
                                        val epLabel = buildString {
                                            latestHistory.season?.let { append("S$it ") }
                                            latestHistory.episode?.let { append("E$it") }
                                            resumeEp?.name?.let { append(" - $it") }
                                        }.trim().ifEmpty { "Continue Watching" }
                                        val canResume = com.lagradost.player.impl.PlayerLinkHandler.resumeStartSeconds(latestHistory.position, latestHistory.duration) > 0
                                        val buttonLabel = if (canResume) "Continue Watching [$epLabel]" else "Play [$epLabel]"

                                        // Resolve the "next" episode: next ep in same season, then first ep of next season
                                        val currentSeason = latestHistory.season
                                        val currentEpNum = latestHistory.episode
                                        val sortedSeasonEps = data.episodes
                                            .filter { it.season == currentSeason || (it.season == null && currentSeason == null) }
                                            .sortedBy { it.episode ?: Int.MAX_VALUE }
                                        val currentIdx = if (resumeEp != null) sortedSeasonEps.indexOf(resumeEp) else -1
                                        val nextEpSameSeason = if (currentIdx >= 0 && currentIdx + 1 < sortedSeasonEps.size) sortedSeasonEps[currentIdx + 1] else null
                                        val nextEp: Episode? = nextEpSameSeason ?: run {
                                            // Try first episode of next season
                                            val nextSeason = (currentSeason ?: 0) + 1
                                            data.episodes
                                                .filter { it.season == nextSeason }
                                                .minByOrNull { it.episode ?: Int.MAX_VALUE }
                                        }
                                        val nextEpLabel = nextEp?.let { ep ->
                                            buildString {
                                                ep.season?.let { append("S$it ") }
                                                ep.episode?.let { append("E$it") }
                                                ep.name?.let { append(" - $it") }
                                            }.trim()
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Button(
                                                onClick = {
                                                    val ep = resumeEp ?: data.episodes.firstOrNull()
                                                    if (ep != null) navigateToPlay(provider, data, ep, onPlay)
                                                },
                                                modifier = Modifier.weight(1f).height(56.dp),
                                                shape = RoundedCornerShape(8.dp),
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Continue Watching", tint = MaterialTheme.colorScheme.onPrimary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (canResume) "Continue" else "Play", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            }
                                            if (nextEp != null) {
                                                OutlinedButton(
                                                    onClick = { navigateToPlay(provider, data, nextEp, onPlay) },
                                                    modifier = Modifier.weight(1f).height(56.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    Icon(Icons.Default.SkipNext, contentDescription = "Play Next", modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (!nextEpLabel.isNullOrBlank()) "Next: $nextEpLabel" else "Play Next",
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }

                                        if (latestHistory.duration > 0) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.lagradost.cloudstream3.desktop.ui.components.WatchProgressIndicator(
                                                position = latestHistory.position,
                                                duration = latestHistory.duration,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }

                                    // Episode Toolbar (Season Dropdown, Search, Sort, Page Switcher)
                                    EpisodeToolbar(
                                        provider = provider,
                                        data = data,
                                        seasons = seasons,
                                        selectedSeason = selectedSeason,
                                        onSelectSeason = { season ->
                                            selectedSeason = season
                                            com.lagradost.common.storage.DesktopDataStore.setKey("last_season_${provider.name}_${data.url}", season)
                                            episodeSearchQuery = ""
                                        },
                                        dubStatuses = emptyList(),
                                        selectedDub = null,
                                        onSelectDub = {},
                                        currentPage = currentPage.coerceIn(1, maxOf(1, totalPages)),
                                        totalPages = totalPages,
                                        onPageChange = { currentPage = it },
                                        episodeSearchQuery = episodeSearchQuery,
                                        onSearchQueryChange = { episodeSearchQuery = it },
                                        isSearchActive = isSearchActive,
                                        onSearchActiveChange = { isSearchActive = it },
                                        isSortAscending = isSortAscending,
                                        onToggleSort = { isSortAscending = !isSortAscending },
                                        searchFocusRequester = searchFocusRequester,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                is AnimeLoadResponse -> {
                                    // Continue Watching + Play Next buttons for Anime
                                    if (latestHistory != null) {
                                        val allAnimeEpisodes = data.episodes.values.flatten()
                                        val resumeEp = allAnimeEpisodes.find { it.data == latestHistory.episodeId }
                                        val epLabel = buildString {
                                            latestHistory.episode?.let { append("E$it") }
                                            resumeEp?.name?.let { append(" - $it") }
                                        }.trim().ifEmpty { "Continue Watching" }
                                        val canResume = com.lagradost.player.impl.PlayerLinkHandler.resumeStartSeconds(latestHistory.position, latestHistory.duration) > 0
                                        val buttonLabel = if (canResume) "Continue Watching [$epLabel]" else "Play [$epLabel]"

                                        // Resolve next episode in same dub list
                                        val dubEps = selectedDub?.let { data.episodes[it] }?.sortedBy { it.episode ?: Int.MAX_VALUE } ?: emptyList()
                                        val currentIdx = if (resumeEp != null) dubEps.indexOf(resumeEp) else -1
                                        val nextEp: Episode? = if (currentIdx >= 0 && currentIdx + 1 < dubEps.size) dubEps[currentIdx + 1] else null
                                        val nextEpLabel = nextEp?.let { ep ->
                                            buildString {
                                                ep.episode?.let { append("E$it") }
                                                ep.name?.let { append(" - $it") }
                                            }.trim()
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Button(
                                                onClick = {
                                                    val ep = resumeEp ?: allAnimeEpisodes.firstOrNull()
                                                    if (ep != null) navigateToPlay(provider, data, ep, onPlay)
                                                },
                                                modifier = Modifier.weight(1f).height(56.dp),
                                                shape = RoundedCornerShape(8.dp),
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Continue Watching", tint = MaterialTheme.colorScheme.onPrimary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (canResume) "Continue" else "Play", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            }
                                            if (nextEp != null) {
                                                OutlinedButton(
                                                    onClick = { navigateToPlay(provider, data, nextEp, onPlay) },
                                                    modifier = Modifier.weight(1f).height(56.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    Icon(Icons.Default.SkipNext, contentDescription = "Play Next", modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (!nextEpLabel.isNullOrBlank()) "Next: $nextEpLabel" else "Play Next",
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }

                                        if (latestHistory.duration > 0) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.lagradost.cloudstream3.desktop.ui.components.WatchProgressIndicator(
                                                position = latestHistory.position,
                                                duration = latestHistory.duration,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }

                                    // Episode Toolbar (Dub Dropdown, Season Dropdown, Search, Sort, Page Switcher)
                                    EpisodeToolbar(
                                        provider = provider,
                                        data = data,
                                        seasons = seasons,
                                        selectedSeason = selectedSeason,
                                        onSelectSeason = { season ->
                                            selectedSeason = season
                                            com.lagradost.common.storage.DesktopDataStore.setKey("last_season_${provider.name}_${data.url}", season)
                                            episodeSearchQuery = ""
                                        },
                                        dubStatuses = dubStatuses,
                                        selectedDub = selectedDub,
                                        onSelectDub = { dub ->
                                            selectedDub = dub
                                            episodeSearchQuery = ""
                                        },
                                        currentPage = currentPage.coerceIn(1, maxOf(1, totalPages)),
                                        totalPages = totalPages,
                                        onPageChange = { currentPage = it },
                                        episodeSearchQuery = episodeSearchQuery,
                                        onSearchQueryChange = { episodeSearchQuery = it },
                                        isSearchActive = isSearchActive,
                                        onSearchActiveChange = { isSearchActive = it },
                                        isSortAscending = isSortAscending,
                                        onToggleSort = { isSortAscending = !isSortAscending },
                                        searchFocusRequester = searchFocusRequester,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }

                if (data is TvSeriesLoadResponse || data is AnimeLoadResponse) {
                    if (allEpisodes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Coming Soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Episodes are not available yet. Please check back later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(
                            items = pagedEpisodes,
                            key = { "${it.data}-${it.name}-${it.posterUrl}-$enrichmentTrigger" },
                        ) { ep ->
                            val isLatest = latestHistory != null && latestHistory.episodeId == ep.data
                            val history = showHistory.values.find { it.episodeId == ep.data }
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                Box(modifier = Modifier.widthIn(max = 1000.dp).padding(horizontal = 16.dp)) {
                                    EpisodeCard(ep, isLatest, history, provider, data, onPlay)
                                }
                            }
                        }

                        if (totalPages > 1) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                                    Box(modifier = Modifier.widthIn(max = 1000.dp).padding(horizontal = 16.dp)) {
                                        PaginationControls(
                                            currentPage = currentPage.coerceIn(1, totalPages),
                                            totalPages = totalPages,
                                            totalItems = allEpisodes.size,
                                            pageSize = pageSize,
                                            onPageChange = { page ->
                                                currentPage = page
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Back button ──
        IconButton(
            onClick = { navController.goBack() },
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun EpisodeToolbar(
    provider: MainAPI,
    data: LoadResponse,
    seasons: List<Int>,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    dubStatuses: List<DubStatus>,
    selectedDub: DubStatus?,
    onSelectDub: (DubStatus) -> Unit,
    currentPage: Int = 1,
    totalPages: Int = 1,
    onPageChange: (Int) -> Unit = {},
    episodeSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    isSortAscending: Boolean,
    onToggleSort: () -> Unit,
    searchFocusRequester: FocusRequester,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!isSearchActive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Dub selector if multiple dubs
                if (dubStatuses.size > 1) {
                    var isDubMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        FilledTonalButton(
                            onClick = { isDubMenuOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DesktopUi.SurfaceElevated),
                        ) {
                            Text(selectedDub?.name ?: "Dubs", fontWeight = FontWeight.Bold, color = DesktopUi.TextPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DesktopUi.TextPrimary)
                        }
                        DropdownMenu(
                            expanded = isDubMenuOpen,
                            onDismissRequest = { isDubMenuOpen = false },
                        ) {
                            dubStatuses.forEach { dub ->
                                val isSelected = selectedDub == dub
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            dub.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) DesktopUi.Accent else DesktopUi.TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        onSelectDub(dub)
                                        isDubMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Season Dropdown Selector
                if (seasons.size > 1) {
                    var isSeasonMenuOpen by remember { mutableStateOf(false) }
                    val seasonNamesMap = remember(data) {
                        if (data is TvSeriesLoadResponse) data.seasonNames?.associateBy { it.season } ?: emptyMap()
                        else emptyMap()
                    }

                    Box {
                        FilledTonalButton(
                            onClick = { isSeasonMenuOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DesktopUi.SurfaceElevated),
                        ) {
                            val currentName = seasonNamesMap[selectedSeason]?.name?.takeIf { it.isNotBlank() } ?: "Season $selectedSeason"
                            Text(currentName, fontWeight = FontWeight.Bold, color = DesktopUi.TextPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DesktopUi.TextPrimary)
                        }

                        DropdownMenu(
                            expanded = isSeasonMenuOpen,
                            onDismissRequest = { isSeasonMenuOpen = false },
                        ) {
                            seasons.forEach { season ->
                                val sName = seasonNamesMap[season]?.name?.takeIf { it.isNotBlank() } ?: "Season $season"
                                val isSelected = selectedSeason == season
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            sName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) DesktopUi.Accent else DesktopUi.TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        onSelectSeason(season)
                                        isSeasonMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                } else if (seasons.size == 1) {
                    Text(
                        text = "Season ${seasons.first()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DesktopUi.TextPrimary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = episodeSearchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f).height(52.dp).focusRequester(searchFocusRequester),
                placeholder = { Text("Search episodes...", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (episodeSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            LaunchedEffect(isSearchActive) { if (isSearchActive) searchFocusRequester.requestFocus() }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Quick page indicator / switcher in toolbar if multiple pages
            if (totalPages > 1 && !isSearchActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    IconButton(
                        onClick = { onPageChange(currentPage - 1) },
                        enabled = currentPage > 1,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Text(
                            "‹",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentPage > 1) DesktopUi.TextPrimary else DesktopUi.TextMuted.copy(alpha = 0.3f),
                        )
                    }
                    Text(
                        text = "$currentPage/$totalPages",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopUi.TextMuted,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    IconButton(
                        onClick = { onPageChange(currentPage + 1) },
                        enabled = currentPage < totalPages,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Text(
                            "›",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentPage < totalPages) DesktopUi.TextPrimary else DesktopUi.TextMuted.copy(alpha = 0.3f),
                        )
                    }
                }
            }

            IconButton(onClick = {
                onSearchActiveChange(!isSearchActive)
                if (isSearchActive) onSearchQueryChange("")
            }) {
                Icon(
                    if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearchActive) "Close search" else "Search episodes",
                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(onClick = onToggleSort) {
                Text(
                    text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun PaginationControls(
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalPages <= 1) return

    val startItem = ((currentPage - 1) * pageSize) + 1
    val endItem = minOf(currentPage * pageSize, totalItems)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Showing $startItem–$endItem of $totalItems episodes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Previous Button
            FilledTonalButton(
                onClick = { onPageChange(currentPage - 1) },
                enabled = currentPage > 1,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DesktopUi.SurfaceElevated,
                    contentColor = DesktopUi.TextPrimary,
                    disabledContainerColor = DesktopUi.SurfaceElevated.copy(alpha = 0.4f),
                    disabledContentColor = DesktopUi.TextMuted.copy(alpha = 0.4f),
                ),
            ) {
                Text("‹ Prev", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            // Page numbers
            val pagesToShow = remember(currentPage, totalPages) {
                val pages = mutableListOf<Int?>()
                if (totalPages <= 7) {
                    for (i in 1..totalPages) pages.add(i)
                } else {
                    pages.add(1)
                    if (currentPage > 4) {
                        pages.add(null) // ellipsis
                    }
                    val start = maxOf(2, currentPage - 1)
                    val end = minOf(totalPages - 1, currentPage + 1)
                    for (i in start..end) {
                        pages.add(i)
                    }
                    if (currentPage < totalPages - 3) {
                        pages.add(null) // ellipsis
                    }
                    pages.add(totalPages)
                }
                pages
            }

            pagesToShow.forEach { page ->
                if (page == null) {
                    Text(
                        text = "...",
                        color = DesktopUi.TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    val isSelected = page == currentPage
                    if (isSelected) {
                        Button(
                            onClick = { },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesktopUi.Accent,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = "$page",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { onPageChange(page) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DesktopUi.SurfaceElevated,
                                contentColor = DesktopUi.TextPrimary,
                            ),
                        ) {
                            Text(
                                text = "$page",
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // Next Button
            FilledTonalButton(
                onClick = { onPageChange(currentPage + 1) },
                enabled = currentPage < totalPages,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DesktopUi.SurfaceElevated,
                    contentColor = DesktopUi.TextPrimary,
                    disabledContainerColor = DesktopUi.SurfaceElevated.copy(alpha = 0.4f),
                    disabledContentColor = DesktopUi.TextMuted.copy(alpha = 0.4f),
                ),
            ) {
                Text("Next ›", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
