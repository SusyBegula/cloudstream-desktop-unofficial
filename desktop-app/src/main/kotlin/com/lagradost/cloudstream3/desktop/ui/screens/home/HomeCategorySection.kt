package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, HomePageResponse>()
private val categoryMutex = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

@Composable
fun HomeCategorySection(
    pageData: MainPageData,
    provider: MainAPI,
    isFirstPage: Boolean = false,
    parentScope: CoroutineScope,
    afterHeroContent: @Composable () -> Unit = {},
    onViewAll: (MainAPI, String, List<SearchResponse>) -> Unit,
    onItemClick: (MainAPI, SearchResponse, String?) -> Unit,
) {
    val cacheKey = "${provider.name}_${pageData.name}"
    var homePage by remember(cacheKey) { mutableStateOf<HomePageResponse?>(categoryCache[cacheKey]) }
    var isLoading by remember(cacheKey) { mutableStateOf(homePage == null) }
    var visible by remember { mutableStateOf(false) }

    var errorMessage by remember(cacheKey) { mutableStateOf<String?>(null) }

    val fetchPage = {
        parentScope.launch {
            val mutex = categoryMutex.getOrPut(cacheKey) { kotlinx.coroutines.sync.Mutex() }
            isLoading = true
            mutex.withLock {
                if (categoryCache[cacheKey] == null) {
                    errorMessage = null
                    try {
                        val request = MainPageRequest(pageData.name, pageData.data, pageData.horizontalImages)
                        val response = withContext(Dispatchers.IO) { provider.getMainPage(1, request) }
                        if (response != null && response.items.isNotEmpty()) {
                            categoryCache[cacheKey] = response
                        } else {
                            errorMessage = "No items found."
                        }
                    } catch (e: Throwable) {
                        DesktopErrorReporter.report("getMainPage failed for ${pageData.name}", e)
                        errorMessage = e.localizedMessage ?: "Connection error"
                    }
                }
                homePage = categoryCache[cacheKey]
            }
            isLoading = false
        }
    }

    LaunchedEffect(pageData, provider) {
        visible = true
        if (homePage == null) {
            fetchPage()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "alpha",
    )
    val offsetX by animateDpAsState(
        targetValue = if (visible) 0.dp else (-50).dp,
        animationSpec = tween(500),
        label = "offset",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .offset(x = offsetX),
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        } else if (homePage != null && homePage!!.items.isNotEmpty()) {
            homePage!!.items.forEachIndexed { sectionIndex, section ->
                if (isFirstPage && sectionIndex == 0 && section.list.size >= 3) {
                    HomeHeroCarousel(
                        items = section.list,
                        provider = provider,
                        onItemClick = { item, backdrop -> onItemClick(provider, item, backdrop) },
                    )
                    afterHeroContent()
                } else {
                    val isLoop = section.list.size >= 4
                    val categoryTitle = section.name.ifBlank { pageData.name }
                    CategoryRowWithHeader(
                        title = categoryTitle,
                        itemCount = section.list.size,
                        isInfinite = isLoop,
                        onViewAll = { onViewAll(provider, categoryTitle, section.list) },
                    ) {
                        items(if (isLoop) Int.MAX_VALUE else section.list.size) { index ->
                            val itemIndex = if (isLoop) index % section.list.size else index
                            val posterItem = section.list[itemIndex]
                            PosterCard(
                                item = posterItem,
                                provider = provider,
                                onClick = { onItemClick(provider, posterItem, null) },
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        errorMessage ?: "Failed to load category.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(onClick = { fetchPage() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
