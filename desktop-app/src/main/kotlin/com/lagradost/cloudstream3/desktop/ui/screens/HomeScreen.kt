package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache
import com.lagradost.cloudstream3.desktop.ui.screens.home.*
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.map
// haze imports removed

@Composable
fun ComposeHomeScreen(
    navController: NavController,
    showErrorsDialog: Boolean = false,
    onDismissErrors: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { HomeViewModel(coroutineScope) }

    val providers by viewModel.providers.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResultsGrouped by viewModel.searchResultsGrouped.collectAsState()
    val isLoadingSearch by viewModel.isLoadingSearch.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val mergedPluginIcons by viewModel.mergedPluginIcons.collectAsState()
    val errorSnapshot by viewModel.errorSnapshot.collectAsState()

    val hasUnreadUpdates by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.hasUnreadUpdates() }
        .collectAsState(initial = DesktopDataStore.hasUnreadUpdates())

    val updatesHistory by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.getUpdatesHistory() }
        .collectAsState(initial = DesktopDataStore.getUpdatesHistory())

    LaunchedEffect(historyList) {
        val topHistory = historyList.take(3)
        for (history in topHistory) {
            val provider = providers.find { it.name == history.apiName }
            if (provider != null && !GlobalDetailsCache.cache.containsKey(history.showUrl)) {
                try {
                    val raw = GlobalDetailsCache.fetchRaw(provider, history.showUrl)
                    if (raw != null) {
                        GlobalDetailsCache.enrich(raw, history.showUrl)
                    }
                } catch (e: Exception) {
                    com.lagradost.common.logging.AppLogger.e("Failed to background fetch/enrich history item", e)
                }
            }
        }
    }

    LaunchedEffect(showErrorsDialog) {
        if (showErrorsDialog) {
            viewModel.refreshErrorSnapshot()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showErrorsDialog) {
            AlertDialog(
                onDismissRequest = onDismissErrors,
                title = { Text("Error Logs") },
                text = {
                    OutlinedTextField(
                        value = errorSnapshot,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(400.dp),
                    )
                },
                confirmButton = {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Row {
                        Button(onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(errorSnapshot))
                        }) {
                            Text("Copy")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.refreshErrorSnapshot()
                            onDismissErrors()
                        }) {
                            Text("Close")
                        }
                    }
                },
            )
        }

        if (searchQuery.isNotBlank() || searchResultsGrouped != null) {
            HomeSearchResults(
                searchResultsGrouped = searchResultsGrouped,
                isLoadingSearch = isLoadingSearch,
                onViewAll = { provider, title, items ->
                    navController.navigate(Screen.CategoryGrid(provider, title, items))
                },
                onItemClick = { provider, item, backdrop ->
                    navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl, backdrop))
                },
            )
        } else if (selectedProvider != null && selectedProvider!!.hasMainPage && selectedProvider!!.mainPage.isNotEmpty()) {
            val currentProvider = selectedProvider!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                if (currentProvider.mainPage.isNotEmpty()) {
                    item {
                        HomeCategorySection(
                            pageData = currentProvider.mainPage[0],
                            provider = currentProvider,
                            isFirstPage = true,
                            parentScope = coroutineScope,
                            afterHeroContent = {
                                HomeHistoryRow(
                                    historyList = historyList,
                                    providers = providers,
                                    onClearHistory = { viewModel.clearHistory() },
                                    onRemoveHistoryItem = { viewModel.removeHistoryItem(it) },
                                    onItemClick = { prov, hist ->
                                        navController.navigate(Screen.Details(prov, hist.showUrl, hist.showName, hist.posterUrl, null))
                                    },
                                )
                            },
                            onViewAll = { provider, title, items ->
                                navController.navigate(Screen.CategoryGrid(provider, title, items))
                            },
                            onItemClick = { provider, item, backdrop ->
                                navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl, backdrop))
                            },
                        )
                    }
                }

                if (currentProvider.mainPage.size > 1) {
                    items(currentProvider.mainPage.size - 1) { index ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            HomeCategorySection(
                                pageData = currentProvider.mainPage[index + 1],
                                provider = currentProvider,
                                isFirstPage = false,
                                parentScope = coroutineScope,
                                onViewAll = { provider, title, items ->
                                    navController.navigate(Screen.CategoryGrid(provider, title, items))
                                },
                                onItemClick = { provider, item, backdrop ->
                                    navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl, backdrop))
                                },
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No content available for this provider.")
            }
        }
    }

    HomeTopBar(
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.searchQuery.value = it },
        onSearch = { viewModel.search() },
        providers = providers,
        selectedProvider = selectedProvider,
        onProviderSelected = {
            viewModel.selectedProviderName.value = it
            viewModel.searchResultsGrouped.value = null
        },
        mergedPluginIcons = mergedPluginIcons,
        hasUnreadUpdates = hasUnreadUpdates,
        updatesHistory = updatesHistory,
        onMarkUpdatesRead = { DesktopDataStore.setUnreadUpdates(false) },
    )
}
