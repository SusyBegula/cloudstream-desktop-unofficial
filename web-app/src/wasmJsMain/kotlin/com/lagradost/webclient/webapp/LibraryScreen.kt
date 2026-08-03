package com.lagradost.webclient.webapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.client.ApiClient
import com.lagradost.webclient.client.LibraryViewModel
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.client.Screen
import com.lagradost.webclient.webapp.components.SectionHeader
import com.lagradost.webclient.webapp.components.WebBookmarkCard

/** Web-client port of desktop-app's LibraryScreen.kt — bookmarks grid. */
@Composable
fun LibraryScreen(api: ApiClient, nav: NavController) {
    val scope = rememberCoroutineScope()
    val vm = remember { LibraryViewModel(api, scope) }
    val bookmarks by vm.bookmarks.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    Box(Modifier.fillMaxSize()) {
        SectionHeader("Library")
        when {
            isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            bookmarks.isEmpty() -> Text("No bookmarks yet — bookmark a title from its details page.", modifier = Modifier.align(Alignment.Center))
            else -> LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 150.dp), modifier = Modifier.fillMaxSize()) {
                items(bookmarks) { bookmark ->
                    WebBookmarkCard(
                        bookmark = bookmark,
                        modifier = Modifier,
                        onRemove = { vm.remove(bookmark) },
                        onClick = { nav.navigate(Screen.Details(bookmark.provider, bookmark.url)) },
                    )
                }
            }
        }
    }
}
