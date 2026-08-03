package com.lagradost.webclient.webapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.client.HomeViewModel
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.client.Screen
import com.lagradost.webclient.webapp.components.CategoryRowWithHeader
import com.lagradost.webclient.webapp.components.PosterCard
import com.lagradost.webclient.webapp.components.WatchHistoryCard
import com.lagradost.webclient.webapp.theme.DesktopUi

/** Web-client port of desktop-app's HomeScreen.kt (simplified: no hero carousel/TMDB enrichment). */
@Composable
fun HomeScreen(vm: HomeViewModel, nav: NavController) {
    val providers by vm.providers.collectAsState()
    val selectedProvider by vm.selectedProviderName.collectAsState()
    val homePage by vm.homePage.collectAsState()
    val history by vm.history.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refreshHistory() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("CloudStream Web", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))

                Row {
                    providers.forEach { p ->
                        val isSelected = p.name == selectedProvider
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { vm.selectProvider(p.name) }
                                .background(
                                    if (isSelected) DesktopUi.Accent.copy(alpha = 0.2f) else DesktopUi.SurfaceElevated,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(p.name, color = if (isSelected) DesktopUi.Accent else DesktopUi.TextPrimary)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; vm.search(it) },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (isLoading) {
            item { CircularProgressIndicator(Modifier.padding(20.dp)) }
        }

        if (query.isNotBlank()) {
            item {
                CategoryRowWithHeader(title = "Search Results", itemCount = searchResults.size) {
                    items(searchResults) { result ->
                        PosterCard(item = result, onClick = { nav.navigate(Screen.Details(result.apiName, result.url, result)) })
                    }
                }
            }
        } else {
            if (history.isNotEmpty()) {
                item {
                    CategoryRowWithHeader(title = "Continue Watching", itemCount = history.size) {
                        items(history) { entry ->
                            WatchHistoryCard(
                                history = entry,
                                onRemove = { vm.removeHistory(entry) },
                                onClick = { nav.navigate(Screen.Details(entry.provider, entry.url)) },
                            )
                        }
                    }
                }
            }
            homePage?.rows?.forEach { row ->
                item {
                    CategoryRowWithHeader(
                        title = row.name,
                        itemCount = row.items.size,
                        isInfinite = row.items.size >= 4,
                        onViewAll = { selectedProvider?.let { nav.navigate(Screen.CategoryGrid(it, row.name, row.items)) } },
                    ) {
                        items(row.items) { result ->
                            PosterCard(item = result, onClick = { nav.navigate(Screen.Details(result.apiName, result.url, result)) })
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}
