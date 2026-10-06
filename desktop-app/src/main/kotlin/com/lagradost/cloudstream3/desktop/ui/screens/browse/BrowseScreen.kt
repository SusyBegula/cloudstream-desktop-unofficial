package com.lagradost.cloudstream3.desktop.ui.screens.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.PaginationControls
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.home.HomeViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.PREF_SELECTED_PROVIDER
import com.lagradost.cloudstream3.desktop.ui.screens.home.isRealProvider
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Year
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(
    navController: NavController,
    viewModel: BrowseViewModel,
    gridState: LazyGridState,
    categoryPage: BrowseCategory? = null,
    homeViewModel: HomeViewModel? = null,
) {
    val state by viewModel.state.collectAsState()
    val filters = state.filters
    var selected by remember { mutableStateOf<BrowseTitle?>(null) }
    var fallbackNotice by remember { mutableStateOf<String?>(null) }
    var resolvingTitleKey by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val selectedProvider by (homeViewModel?.selectedProvider ?: remember {
        val saved = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER)
        val prov = APIHolder.allProviders.firstOrNull { it.name == saved && it.isRealProvider() }
            ?: APIHolder.allProviders.firstOrNull { it.isRealProvider() }
        kotlinx.coroutines.flow.MutableStateFlow(prov)
    }).collectAsState()

    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val minSize = when (gridScale) {
        "Compact" -> 140.dp
        "Large" -> 210.dp
        else -> 170.dp
    }
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    // A filter change starts a new catalog; navigating back preserves the existing position.
    var previousFilters by remember { mutableStateOf(filters) }
    LaunchedEffect(filters) {
        if (previousFilters != filters) gridState.scrollToItem(0)
        previousFilters = filters
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalSpacing = 18.dp
        val columns = maxOf(1, ((maxWidth + horizontalSpacing) / (minSize + horizontalSpacing)).toInt())
        val pageSize = columns * 3

        LaunchedEffect(pageSize) {
            viewModel.setPageSize(pageSize)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(categoryPage?.headerTitle ?: "Browser", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Discover your next movie, show or anime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (categoryPage == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrowseCategory.entries.forEach { category ->
                        FilterChip(selected = filters.category == category, onClick = {
                            viewModel.setFilters(filters.copy(category = category, genre = null))
                        }, label = { Text(category.label) })
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val hasActiveFilters = filters.genre != null ||
                filters.ageRating != null ||
                filters.year != null ||
                filters.minRating > 0 ||
                (filters.language != null && filters.language != "en") ||
                filters.sort != BrowseSort.POPULAR ||
                (filters.category == BrowseCategory.ANIME && filters.animeMovies)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DesktopUi.SurfaceCard.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (filters.category == BrowseCategory.ANIME) {
                            BrowseFilterChip(
                                label = "Format",
                                value = if (filters.animeMovies) "Movies" else "Shows",
                                isActive = filters.animeMovies,
                                selectedKey = filters.animeMovies,
                                options = listOf(false to "Shows", true to "Movies"),
                            ) {
                                viewModel.setFilters(filters.copy(animeMovies = it, genre = null))
                            }
                        }

                        val selectedGenre = filters.genres.firstOrNull { it.id == filters.genre }
                        BrowseFilterChip(
                            label = "Genre",
                            value = selectedGenre?.label ?: "All genres",
                            isActive = filters.genre != null,
                            selectedKey = filters.genre,
                            options = listOf(null to "All genres") + filters.genres.map { it.id to it.label },
                        ) {
                            viewModel.setFilters(filters.copy(genre = it))
                        }

                        val selectedAgeRating = findAgeRating(filters.ageRating)
                        BrowseFilterChip(
                            label = "Age rating",
                            value = selectedAgeRating?.label ?: "All ratings",
                            isActive = filters.ageRating != null,
                            selectedKey = filters.ageRating,
                            options = listOf(null to "All ratings") + filters.ageRatings.map { it.id to it.label },
                        ) {
                            viewModel.setFilters(filters.copy(ageRating = it))
                        }

                        BrowseFilterChip(
                            label = "Year",
                            value = filters.year?.toString() ?: "Any year",
                            isActive = filters.year != null,
                            selectedKey = filters.year,
                            options = listOf(null to "Any year") + (Year.now().value downTo 1900).map { it to it.toString() },
                        ) {
                            viewModel.setFilters(filters.copy(year = it))
                        }

                        BrowseFilterChip(
                            label = "TMDB rating",
                            value = if (filters.minRating == 0) "Any rating" else "${filters.minRating}+ ★",
                            isActive = filters.minRating > 0,
                            selectedKey = filters.minRating,
                            options = listOf(0 to "Any rating") + (5..9).map { it to "$it+ ★" },
                        ) {
                            viewModel.setFilters(filters.copy(minRating = it))
                        }

                        if (filters.category != BrowseCategory.ANIME) {
                            val languages = listOf(
                                null to "Any language", "en" to "English", "hi" to "Hindi", "ja" to "Japanese",
                                "ko" to "Korean", "es" to "Spanish", "fr" to "French", "de" to "German", "zh" to "Chinese",
                                "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam", "kn" to "Kannada",
                            )
                            val selectedLang = languages.firstOrNull { it.first == filters.language }?.second ?: "Any language"
                            BrowseFilterChip(
                                label = "Language",
                                value = selectedLang,
                                isActive = filters.language != null && filters.language != "en",
                                selectedKey = filters.language,
                                options = languages,
                            ) {
                                viewModel.setFilters(filters.copy(language = it))
                            }
                        }

                        BrowseFilterChip(
                            label = "Sort by",
                            value = filters.sort.label,
                            isActive = filters.sort != BrowseSort.POPULAR,
                            selectedKey = filters.sort,
                            options = BrowseSort.entries.map { it to it.label },
                        ) {
                            viewModel.setFilters(filters.copy(sort = it))
                        }

                        if (hasActiveFilters) {
                            Surface(
                                onClick = { viewModel.setFilters(BrowseFilters(category = filters.category)) },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Red.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.35f)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Reset filters",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFFFF6B6B),
                                    )
                                    Text(
                                        text = "Reset filters",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFF6B6B),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = buildString {
                                append("Catalog and ratings from TMDB")
                                if (filters.category == BrowseCategory.ANIME) append(" · Japanese animation")
                                if (filters.sort == BrowseSort.RATING || filters.minRating > 0) append(" · At least 100 votes")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopUi.TextMuted,
                        )
                        if (state.titles.isNotEmpty()) {
                            Text(
                                text = "${state.totalResults} titles",
                                style = MaterialTheme.typography.labelSmall,
                                color = DesktopUi.TextMuted,
                            )
                        }
                    }
                }
            }
        }
        if (state.titles.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "${state.totalResults} titles · ${state.titles.size} loaded",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.titles, key = { it.key }) { title ->
            BrowseCard(
                title = title,
                isLoading = resolvingTitleKey == title.key,
                onClick = {
                    val provider = selectedProvider
                    if (provider != null) {
                        coroutineScope.launch {
                            resolvingTitleKey = title.key
                            try {
                                val searchResponse = withContext(Dispatchers.IO) {
                                    withTimeoutOrNull(15_000) {
                                        provider.search(title.name, 1)
                                    }
                                }
                                val items = searchResponse?.items.orEmpty()
                                val match = items.firstOrNull { it.name.trim().equals(title.name.trim(), ignoreCase = true) }
                                    ?: items.firstOrNull { it.name.contains(title.name.trim(), ignoreCase = true) }
                                    ?: items.firstOrNull()

                                if (match != null) {
                                    navController.navigate(
                                        Screen.Details(
                                            provider = provider,
                                            url = match.url,
                                            preloadedName = match.name,
                                            preloadedPoster = match.posterUrl ?: title.poster,
                                        )
                                    )
                                } else {
                                    fallbackNotice = "Could not find “${title.name}” on ${provider.name}. Choose from another provider below:"
                                    selected = title
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                com.lagradost.common.logging.AppLogger.e("BrowseScreen: Failed to resolve ${title.name} on ${provider.name}", e)
                                fallbackNotice = "Error connecting to ${provider.name}. Choose from another provider below:"
                                selected = title
                            } finally {
                                resolvingTitleKey = null
                            }
                        }
                    } else {
                        fallbackNotice = null
                        selected = title
                    }
                },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.error != null -> {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retry) { Text("Try again") }
                    }
                    state.titles.isEmpty() && state.loading -> {
                        CircularProgressIndicator()
                        Text("Loading titles…")
                    }
                    state.titles.isEmpty() && !state.loading -> {
                        Text("No titles match these filters.", style = MaterialTheme.typography.titleMedium)
                        Text("Try another genre, age rating, year or rating.")
                        OutlinedButton(onClick = { viewModel.setFilters(BrowseFilters(category = filters.category)) }) { Text("Reset filters") }
                    }
                    else -> {
                        if (state.loading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(0.3f).padding(bottom = 8.dp),
                                color = DesktopUi.Accent,
                            )
                        }
                        PaginationControls(
                            currentPage = state.page,
                            totalPages = state.totalPages,
                            totalItems = state.totalResults,
                            pageSize = state.pageSize,
                            itemLabel = "titles",
                            onPageChange = { targetPage ->
                                viewModel.loadPage(targetPage)
                                coroutineScope.launch {
                                    gridState.scrollToItem(0)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    selected?.let { title ->
        BrowseSourcesDialog(
            title = title,
            notice = fallbackNotice,
            onDismiss = {
                selected = null
                fallbackNotice = null
            },
            onExtensions = {
                selected = null
                fallbackNotice = null
                navController.navigate(Screen.Extensions)
            },
            onSelect = { provider, result ->
                selected = null
                fallbackNotice = null
                navController.navigate(Screen.Details(provider, result.url, result.name, result.posterUrl ?: title.poster))
            },
        )
    }
    }
}

@Composable
private fun <T> BrowseFilterChip(
    label: String,
    value: String,
    isActive: Boolean,
    selectedKey: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = if (isActive) DesktopUi.Accent.copy(alpha = 0.14f) else DesktopUi.SurfaceElevated.copy(alpha = 0.65f),
            border = BorderStroke(
                1.dp,
                if (isActive) DesktopUi.Accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) DesktopUi.Accent else DesktopUi.TextMuted,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) DesktopUi.Accent.copy(alpha = 0.6f) else DesktopUi.TextMuted.copy(alpha = 0.4f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) DesktopUi.Accent else DesktopUi.TextPrimary,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isActive) DesktopUi.Accent else DesktopUi.TextMuted.copy(alpha = 0.8f),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 340.dp),
        ) {
            options.forEach { (key, text) ->
                val isSelected = key == selectedKey
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = DesktopUi.Accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = text,
                                color = if (isSelected) DesktopUi.Accent else Color.Unspecified,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}

@Composable
private fun BrowseCard(
    title: BrowseTitle,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (title.poster != null) {
                AsyncImage(
                    model = title.poster,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${title.year ?: "—"} · ${if (title.isMovie) "Movie" else "Show"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "TMDB ${title.rating?.let { String.format(Locale.ROOT, "%.1f / 10", it) } ?: "Unrated"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private data class ProviderMatches(val provider: MainAPI, val titles: List<SearchResponse>, val failed: Boolean = false)

@Composable
private fun BrowseSourcesDialog(
    title: BrowseTitle,
    notice: String? = null,
    onDismiss: () -> Unit,
    onExtensions: () -> Unit,
    onSelect: (MainAPI, SearchResponse) -> Unit,
) {
    val providers = remember { APIHolder.allProviders.filter { it.isRealProvider() } }
    var query by remember(title.key) { mutableStateOf(title.name) }
    var searchQuery by remember(title.key) { mutableStateOf(title.name) }
    var attempt by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var matches by remember { mutableStateOf(emptyList<ProviderMatches>()) }
    LaunchedEffect(title.key, searchQuery, attempt) {
        loading = true
        matches = emptyList()
        val semaphore = Semaphore(4)
        try {
            matches = withContext(Dispatchers.IO) {
                coroutineScope {
                    providers.map { provider ->
                        async {
                            semaphore.withPermit {
                                try {
                                    withTimeoutOrNull(20_000) {
                                        ProviderMatches(provider, provider.search(searchQuery, 1)?.items.orEmpty())
                                    } ?: ProviderMatches(provider, emptyList(), failed = true)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    ProviderMatches(provider, emptyList(), failed = true)
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        } finally {
            loading = false
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().heightIn(max = 720.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${title.year ?: "Unknown year"} · ${if (title.isMovie) "Movie" else "Show"} · TMDB ${title.rating?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "Unrated"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (title.overview.isNotBlank()) Text(title.overview, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (notice != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                HorizontalDivider()
                if (providers.isEmpty()) {
                    Text("Install a content provider to find this title and watch it.")
                    Button(onClick = onExtensions) { Text("Browse extensions") }
                } else {
                    Text("Find in your providers", style = MaterialTheme.typography.titleMedium)
                    Text("Choose a matching title to view details and play.", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            label = { Text("Title to search") },
                            modifier = Modifier.weight(1f),
                        )
                        Button(enabled = query.isNotBlank(), onClick = {
                            searchQuery = query.trim()
                            attempt++
                        }) { Text("Search") }
                    }
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Searching installed providers…")
                    } else {
                        val failed = matches.count { it.failed }
                        if (failed > 0) Text("$failed provider(s) couldn’t be reached. You can search again to retry.", color = MaterialTheme.colorScheme.error)
                        if (matches.all { it.titles.isEmpty() }) Text("No matches found. Try another title or add a provider.")
                        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            matches.filter { it.titles.isNotEmpty() }.forEach { group ->
                                item { Text(group.provider.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                                items(group.titles) { result ->
                                    Surface(
                                        onClick = { onSelect(group.provider, result) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            AsyncImage(
                                                model = result.posterUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(40.dp, 60.dp),
                                            )
                                            Column {
                                                Text(result.name)
                                                Text(result.type?.name ?: "Title", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}
