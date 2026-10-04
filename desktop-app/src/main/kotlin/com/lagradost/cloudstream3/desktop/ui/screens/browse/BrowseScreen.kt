package com.lagradost.cloudstream3.desktop.ui.screens.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.home.isRealProvider
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Year
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(navController: NavController, viewModel: BrowseViewModel, gridState: LazyGridState) {
    val state by viewModel.state.collectAsState()
    val filters = state.filters
    var selected by remember { mutableStateOf<BrowseTitle?>(null) }
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

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Browse", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Discover your next movie, show or anime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrowseCategory.entries.forEach { category ->
                        FilterChip(selected = filters.category == category, onClick = {
                            viewModel.setFilters(filters.copy(category = category, genre = null))
                        }, label = { Text(category.label) })
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (filters.category == BrowseCategory.ANIME) {
                            BrowseDropdown("Format", if (filters.animeMovies) "Movies" else "Shows", listOf(false to "Shows", true to "Movies")) {
                                viewModel.setFilters(filters.copy(animeMovies = it, genre = null))
                            }
                        }
                        BrowseDropdown(
                            "Genre",
                            filters.genres.firstOrNull { it.id == filters.genre }?.label ?: "All genres",
                            listOf(null to "All genres") + filters.genres.map { it.id to it.label },
                        ) {
                            viewModel.setFilters(filters.copy(genre = it))
                        }
                        BrowseDropdown(
                            "Age rating",
                            findAgeRating(filters.ageRating)?.label ?: "All ratings",
                            listOf(null to "All ratings") + filters.ageRatings.map { it.id to it.label },
                        ) {
                            viewModel.setFilters(filters.copy(ageRating = it))
                        }
                        BrowseDropdown(
                            "Year",
                            filters.year?.toString() ?: "Any year",
                            listOf(null to "Any year") + (Year.now().value downTo 1900).map { it to it.toString() },
                        ) {
                            viewModel.setFilters(filters.copy(year = it))
                        }
                        BrowseDropdown(
                            "TMDB rating",
                            if (filters.minRating == 0) "Any rating" else "${filters.minRating}+",
                            listOf(0 to "Any rating") + (5..9).map { it to "$it+" },
                        ) {
                            viewModel.setFilters(filters.copy(minRating = it))
                        }
                        if (filters.category != BrowseCategory.ANIME) {
                            val languages = listOf(
                                null to "Any language", "en" to "English", "hi" to "Hindi", "ja" to "Japanese",
                                "ko" to "Korean", "es" to "Spanish", "fr" to "French", "de" to "German", "zh" to "Chinese",
                                "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam", "kn" to "Kannada",
                            )
                            BrowseDropdown("Original language", languages.first { it.first == filters.language }.second, languages) {
                                viewModel.setFilters(filters.copy(language = it))
                            }
                        }
                        BrowseDropdown("Sort by", filters.sort.label, BrowseSort.entries.map { it to it.label }) {
                            viewModel.setFilters(filters.copy(sort = it))
                        }
                        TextButton(onClick = { viewModel.setFilters(BrowseFilters(category = filters.category)) }) { Text("Reset filters") }
                    }
                    Text(
                        buildString {
                            append("Catalog and ratings from TMDB")
                            if (filters.category == BrowseCategory.ANIME) append(" · Japanese animation")
                            if (filters.sort == BrowseSort.RATING || filters.minRating > 0) append(" · At least 100 votes")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        items(state.titles, key = { it.key }) { title -> BrowseCard(title) { selected = title } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.loading -> {
                        CircularProgressIndicator()
                        Text("Loading titles…")
                    }
                    state.error != null -> {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retry) { Text("Try again") }
                    }
                    state.titles.isEmpty() -> {
                        Text("No titles match these filters.", style = MaterialTheme.typography.titleMedium)
                        Text("Try another genre, age rating, year or rating.")
                        OutlinedButton(onClick = { viewModel.setFilters(BrowseFilters(category = filters.category)) }) { Text("Reset filters") }
                    }
                    state.page < state.totalPages -> OutlinedButton(onClick = viewModel::loadMore) { Text("Load more") }
                }
            }
        }
    }
    selected?.let { title ->
        BrowseSourcesDialog(title, onDismiss = { selected = null }, onExtensions = {
            selected = null
            navController.navigate(Screen.Extensions)
        }, onSelect = { provider, result ->
            selected = null
            navController.navigate(Screen.Details(provider, result.url, result.name, result.posterUrl ?: title.poster))
        })
    }
}

@Composable
private fun <T> BrowseDropdown(label: String, value: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(value)
                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = {
                        expanded = false
                        onSelect(key)
                    })
                }
            }
        }
    }
}

@Composable
private fun BrowseCard(title: BrowseTitle, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
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
