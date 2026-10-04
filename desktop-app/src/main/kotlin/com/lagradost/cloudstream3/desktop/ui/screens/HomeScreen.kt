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

@Composable
fun ComposeHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel,
) {
    val coroutineScope = rememberCoroutineScope()

    val providers by viewModel.providers.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

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

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedProvider != null && selectedProvider!!.hasMainPage && selectedProvider!!.mainPage.isNotEmpty()) {
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (selectedProvider == null) "Choose a provider to get started" else "No home page available for this provider.")
                    Text("Use the provider menu above, or explore the catalog.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { navController.navigate(Screen.Browse) }) { Text("Open Browser") }
                    if (providers.isEmpty()) {
                        TextButton(onClick = { navController.navigate(Screen.Extensions) }) { Text("Install extensions") }
                    }
                }
            }
        }
    }
}
