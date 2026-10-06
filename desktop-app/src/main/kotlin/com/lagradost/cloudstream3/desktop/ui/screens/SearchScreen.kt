package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.home.HomeViewModel
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun SearchScreen(navController: NavController, viewModel: HomeViewModel, gridState: LazyGridState) {
    val query by viewModel.searchQuery.collectAsState()
    val grouped by viewModel.searchResultsGrouped.collectAsState()
    val loading by viewModel.isLoadingSearch.collectAsState()
    val loadingMore by viewModel.isLoadingMore.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val error by viewModel.searchError.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val global by viewModel.isGlobalSearchEnabled.collectAsState()
    val scale by AppearanceConfig.gridScale.collectAsState()

    val results = remember(grouped) {
        grouped.orEmpty().flatMap { (provider, titles) -> titles.distinctBy { it.url }.map { provider to it } }
    }

    val cleanGrouped = remember(grouped) {
        grouped.orEmpty()
            .map { (provider, titles) -> provider to titles.distinctBy { it.url } }
            .filter { (_, titles) -> titles.isNotEmpty() }
    }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.refreshSearchPreference() }

    var previousContext by remember { mutableStateOf(Triple(query, selectedProvider, global)) }
    LaunchedEffect(query, selectedProvider, global) {
        val context = Triple(query, selectedProvider, global)
        if (previousContext != context) {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        }
        previousContext = context
    }

    val shouldLoadMoreGrid by remember(gridState, results.size, canLoadMore, loadingMore, loading, global) {
        derivedStateOf {
            if (global) return@derivedStateOf false
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !loading && !loadingMore && canLoadMore && totalItems > 0 && lastVisibleItemIndex >= totalItems - 6
        }
    }
    LaunchedEffect(shouldLoadMoreGrid) {
        if (shouldLoadMoreGrid) {
            viewModel.loadNextPage()
        }
    }

    val shouldLoadMoreList by remember(listState, cleanGrouped.size, canLoadMore, loadingMore, loading, global) {
        derivedStateOf {
            if (!global) return@derivedStateOf false
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !loading && !loadingMore && canLoadMore && totalItems > 0 && lastVisibleItemIndex >= totalItems - 2
        }
    }
    LaunchedEffect(shouldLoadMoreList) {
        if (shouldLoadMoreList) {
            viewModel.loadNextPage()
        }
    }

    val searchHeader = @Composable {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f), Color.Transparent)),
            ).padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Text("Search", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier.widthIn(max = 672.dp).fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("Search movies, TV shows and anime") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            )
            LaunchedEffect(Unit) { if (query.isBlank()) focusRequester.requestFocus() }
            Text(
                if (global) "Searching all installed providers" else "Searching ${selectedProvider?.name ?: "your selected provider"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (query.isNotBlank()) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                    Text("Results for: “${query.trim()}”", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        if (results.isNotEmpty()) {
                            Text("${results.size} results found (loading more...)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (grouped != null) {
                    Text(
                        if (global && cleanGrouped.size > 1) {
                            "${results.size} results found across ${cleanGrouped.size} providers"
                        } else {
                            "${results.size} results found"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val errorContent = @Composable {
        if (error != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::search) { Text("Try again") }
            }
        }
    }

    val emptyContent = @Composable {
        if (results.isEmpty() && !loading) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    when {
                        providers.isEmpty() -> "Install a provider to search for titles."
                        !global && selectedProvider == null -> "Choose a provider from the header to search."
                        query.isBlank() -> "Find your next movie, TV show or anime."
                        error != null -> "Try again or choose a different provider."
                        else -> "No matches found. Try another title or provider."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (providers.isEmpty()) {
                    OutlinedButton(onClick = { navController.navigate(Screen.Extensions) }) {
                        Text("Open Extensions")
                    }
                }
            }
        }
    }

    if (global) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                searchHeader()
            }
            if (error != null) {
                item(key = "error") {
                    errorContent()
                }
            }
            if (results.isEmpty() && !loading) {
                item(key = "empty") {
                    emptyContent()
                }
            }
            items(cleanGrouped, key = { (provider, _) -> provider.name }) { (provider, items) ->
                CategoryRowWithHeader(
                    title = provider.name,
                    itemCount = items.size,
                    isInfinite = false,
                    rowContentPadding = PaddingValues(start = 4.dp, end = 4.dp),
                    onViewAll = {
                        navController.navigate(Screen.CategoryGrid(provider, "${provider.name} - ${query.trim()}", items))
                    },
                    trailingHeaderExtra = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Text(
                                "${items.size} ${if (items.size == 1) "result" else "results"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    },
                ) {
                    items(items, key = { item -> "${provider.name}:${item.url}" }) { item ->
                        PosterCard(
                            item = item,
                            provider = provider,
                            showTypeBadge = true,
                            onClick = {
                                navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl))
                            },
                        )
                    }
                }
            }
            if (loadingMore) {
                item(key = "loading_more") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
            } else if (canLoadMore && results.isNotEmpty() && !loading) {
                item(key = "load_more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = viewModel::loadNextPage) {
                            Text("Load more results")
                        }
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(
                when (scale) {
                    "Compact" -> 140.dp
                    "Large" -> 210.dp
                    else -> 170.dp
                },
            ),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                searchHeader()
            }
            if (error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    errorContent()
                }
            }
            if (results.isEmpty() && !loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    emptyContent()
                }
            }
            items(results, key = { (provider, item) -> "${provider.name}:${item.url}" }) { (provider, item) ->
                Column {
                    PosterCard(item, provider, showTypeBadge = true, modifier = Modifier.fillMaxWidth()) {
                        navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl))
                    }
                    if (global) Text(provider.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
            } else if (canLoadMore && results.isNotEmpty() && !loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        OutlinedButton(onClick = viewModel::loadNextPage) {
                            Text("Load more results")
                        }
                    }
                }
            }
        }
    }
}
