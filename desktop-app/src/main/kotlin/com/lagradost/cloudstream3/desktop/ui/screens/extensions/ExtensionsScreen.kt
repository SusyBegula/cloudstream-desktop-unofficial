package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ComposeExtensionScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Browse", "Installed", "Repositories")
    val syncGen by DesktopRepositoryManager.syncGeneration.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { ExtensionsViewModel(coroutineScope) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            DesktopRepositoryManager.syncAll()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.widthIn(max = 400.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxHeight()) {
                when (selectedTab) {
                    0 -> BrowseTab(viewModel = viewModel, syncGeneration = syncGen)
                    1 -> InstalledTab(viewModel = viewModel, syncGeneration = syncGen)
                    2 -> RepositoriesTab(viewModel = viewModel)
                }
            }
        }
    }
}
