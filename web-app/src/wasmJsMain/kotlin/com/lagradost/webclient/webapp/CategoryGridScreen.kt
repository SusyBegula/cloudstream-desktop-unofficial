package com.lagradost.webclient.webapp

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.client.Screen
import com.lagradost.webclient.webapp.components.PosterCard

/** Web-client port of desktop-app's CategoryGridScreen.kt — plain adaptive "View All" grid. */
@Composable
fun CategoryGridScreen(screen: Screen.CategoryGrid, nav: NavController) {
    Text(screen.title, modifier = Modifier.padding(bottom = 12.dp), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(screen.items) { result ->
            PosterCard(
                item = result,
                modifier = Modifier.padding(6.dp),
                onClick = { nav.navigate(Screen.Details(result.apiName, result.url, result)) },
            )
        }
    }
}
