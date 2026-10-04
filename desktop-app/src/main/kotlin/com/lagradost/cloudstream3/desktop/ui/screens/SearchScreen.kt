package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewModel.refreshSearchPreference() }
    var previousContext by remember { mutableStateOf(Triple(query, selectedProvider, global)) }
    LaunchedEffect(query, selectedProvider, global) {
        val context = Triple(query, selectedProvider, global)
        if (previousContext != context) gridState.scrollToItem(0)
        previousContext = context
    }

    val shouldLoadMore by remember(gridState, results.size, canLoadMore, loadingMore, loading) {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !loading && !loadingMore && canLoadMore && totalItems > 0 && lastVisibleItemIndex >= totalItems - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadNextPage()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(when (scale) { "Compact" -> 140.dp; "Large" -> 210.dp; else -> 170.dp }),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f), Color.Transparent)),
                ).padding(top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
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
                        if (query.isNotEmpty()) IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
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
                        Text("${results.size} results found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (error != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::search) { Text("Try again") }
            }
        }
        if (results.isEmpty() && !loading) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                if (providers.isEmpty()) OutlinedButton(onClick = { navController.navigate(Screen.Extensions) }) { Text("Open Extensions") }
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
