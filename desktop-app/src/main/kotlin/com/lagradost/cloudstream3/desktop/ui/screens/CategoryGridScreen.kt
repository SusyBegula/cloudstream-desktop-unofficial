package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.controller.GamepadActionBridge
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ComposeCategoryGridScreen(
    navController: NavController,
    provider: MainAPI,
    title: String,
    items: List<SearchResponse>,
) {
    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val minSize = when (gridScale) {
        "Compact" -> 110.dp
        "Large" -> 170.dp
        else -> 140.dp
    }

    val gridState = rememberLazyGridState()
    val rightStickY by GamepadActionBridge.rightStickY.collectAsState()

    LaunchedEffect(rightStickY) {
        if (kotlin.math.abs(rightStickY) > 0.05f) {
            while (isActive && kotlin.math.abs(GamepadActionBridge.rightStickY.value) > 0.05f) {
                val y = GamepadActionBridge.rightStickY.value
                val scrollDelta = y * 28f
                gridState.dispatchRawDelta(scrollDelta)
                delay(16)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        state = gridState,
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items.size) { index ->
            val item = items[index]
            PosterCard(
                item = item,
                provider = provider,
                onClick = {
                    navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl, null))
                },
            )
        }
    }
}
