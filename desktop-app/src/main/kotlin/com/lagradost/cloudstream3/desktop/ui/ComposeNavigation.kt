package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeDetailsScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeDownloadsScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeExtensionScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeHomeScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeLibraryScreen
import com.lagradost.common.storage.WatchHistory

data class VideoLaunchData(
    val links: List<com.lagradost.cloudstream3.utils.ExtractorLink>,
    val initialIndex: Int,
    val title: String?,
    val subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    val startPositionMs: Long,
    val history: WatchHistory,
    val onError: ((String) -> Unit)? = null,
    val onClosed: (() -> Unit)? = null,
    val onPlayNext: (() -> Unit)? = null,
    val nextEpisode: NextEpisodeData? = null,
)

val LocalVideoPlayer = androidx.compose.runtime.staticCompositionLocalOf<(VideoLaunchData?) -> Unit> { { } }
val LocalWindowState = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.window.WindowState?> { null }

@Composable
fun CloudstreamApp() {
    val navController = remember { NavController() }
    val browseScope = androidx.compose.runtime.rememberCoroutineScope()
    val browseViewModel = remember { com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseViewModel(browseScope) }
    val homeViewModel = remember { com.lagradost.cloudstream3.desktop.ui.screens.home.HomeViewModel(browseScope) }
    val catalogViewModels = remember {
        com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseCategory.entries.associateWith { category ->
            com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseViewModel(
                browseScope,
                initialFilters = com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseFilters(category = category),
            )
        }
    }
    val catalogGridStates = com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseCategory.entries.associateWith {
        androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    }
    val searchGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val browseGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var showErrorsDialog by remember { mutableStateOf(false) }
    var currentVideo by remember { mutableStateOf<VideoLaunchData?>(null) }
    val screen = navController.currentScreen

    val isLightMode by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.isLightMode.collectAsState()
    val themeAccent by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.themeAccent.collectAsState()
    val amoledMode by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.amoledMode.collectAsState()
    val primaryColor = com.lagradost.cloudstream3.desktop.ui.theme.accentColorFromName(themeAccent)
    val desktopColors = com.lagradost.cloudstream3.desktop.ui.theme.buildDesktopColors(primaryColor, isLightMode, amoledMode)

    androidx.compose.runtime.CompositionLocalProvider(
        LocalVideoPlayer provides { currentVideo = it },
        com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme provides desktopColors,
    ) {
        val appColorScheme = com.lagradost.cloudstream3.desktop.ui.theme.buildColorScheme(primaryColor, desktopColors, isLightMode)

        androidx.compose.material3.MaterialTheme(colorScheme = appColorScheme) {
            androidx.compose.material3.Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
            ) {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    DesktopAppShell(
                        navController = navController,
                        homeViewModel = homeViewModel,
                        showBack = screen is Screen.Details || screen is Screen.CategoryGrid || screen is Screen.Search,
                    ) {
                        androidx.compose.animation.Crossfade(
                            targetState = screen,
                            animationSpec = androidx.compose.animation.core.tween(300),
                        ) { targetScreen ->
                            when (targetScreen) {
                                is Screen.Details -> ComposeDetailsScreen(
                                    navController = navController,
                                    provider = targetScreen.provider,
                                    url = targetScreen.url,
                                    preloadedName = targetScreen.preloadedName,
                                    preloadedPoster = targetScreen.preloadedPoster,
                                    preloadedBg = targetScreen.preloadedBg,
                                )
                                is Screen.Home -> ComposeHomeScreen(navController, homeViewModel)
                                is Screen.Search -> com.lagradost.cloudstream3.desktop.ui.screens.SearchScreen(
                                    navController, homeViewModel, searchGridState,
                                )
                                is Screen.Catalog -> com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseScreen(
                                    navController,
                                    catalogViewModels.getValue(targetScreen.category),
                                    catalogGridStates.getValue(targetScreen.category),
                                    categoryPage = targetScreen.category,
                                    homeViewModel = homeViewModel,
                                )
                                is Screen.Browse -> com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseScreen(
                                    navController,
                                    browseViewModel,
                                    browseGridState,
                                    homeViewModel = homeViewModel,
                                )
                                is Screen.Extensions -> ComposeExtensionScreen(navController)
                                is Screen.Library -> ComposeLibraryScreen(navController)
                                is Screen.Downloads -> ComposeDownloadsScreen(navController)
                                is Screen.Settings -> com.lagradost.cloudstream3.desktop.ui.screens.settings.ComposeSettingsScreen(
                                    navController, onErrorLogs = { showErrorsDialog = true },
                                )
                                is Screen.CategoryGrid -> com.lagradost.cloudstream3.desktop.ui.screens.ComposeCategoryGridScreen(
                                    navController, targetScreen.provider, targetScreen.title, targetScreen.items,
                                )
                            }
                        }
                    }
                    if (showErrorsDialog) {
                        val snapshot = remember { com.lagradost.cloudstream3.desktop.DesktopErrorReporter.getSnapshot() }
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showErrorsDialog = false },
                            title = { androidx.compose.material3.Text("Error Logs") },
                            text = {
                                androidx.compose.material3.OutlinedTextField(value = snapshot, onValueChange = {}, readOnly = true)
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    com.lagradost.cloudstream3.desktop.utils.DesktopClipboard.copyText(snapshot, clipboard)
                                }) { androidx.compose.material3.Text("Copy") }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { showErrorsDialog = false }) {
                                    androidx.compose.material3.Text("Close")
                                }
                            },
                        )
                    }

                    // The Embedded Video Player Overlay
                    if (currentVideo != null) {
                        com.lagradost.cloudstream3.desktop.ui.screens.player.EmbeddedVideoPlayer(
                            launchData = currentVideo!!,
                            onClose = { currentVideo = null },
                        )
                    }
                }
            }
        }
    }
}
