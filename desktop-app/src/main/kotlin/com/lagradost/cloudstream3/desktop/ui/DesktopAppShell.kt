package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.darkDesktopColors
import com.lagradost.cloudstream3.desktop.ui.components.lightDesktopColors
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.utils.PlaywrightManager
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import coil3.compose.AsyncImage

@Composable
fun DesktopAppShell(
    navController: NavController,
    title: String,
    showBack: Boolean = false,
    onErrorLogs: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val current = navController.currentScreen
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val showBrowserPrompt by PlaywrightManager.showPrompt.collectAsState()
    val isBrowserDownloading by PlaywrightManager.isDownloading.collectAsState()


    val hasUnreadUpdates by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.hasUnreadUpdates() }
        .collectAsState(initial = DesktopDataStore.hasUnreadUpdates())
    
    val updatesHistory by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.getUpdatesHistory() }
        .collectAsState(initial = DesktopDataStore.getUpdatesHistory())

    LaunchedEffect(Unit) {
        while (true) {
            delay(30 * 60 * 1000L) // 30 minutes
            DesktopRepositoryManager.autoUpdatePlugins()
        }
    }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxSize()) {
                    // Navigation Dock
                    NavigationDock(
                        current = current,
                        isSyncing = isSyncing,
                        onNavigate = { navController.navigate(it) },
                        onSync = {
                            scope.launch {
                                if (isSyncing) return@launch
                                isSyncing = true
                                try {
                                    val report = DesktopRepositoryManager.syncAll()
                                    snackbarHostState.showSnackbar(
                                        message = report.summary,
                                        duration = SnackbarDuration.Short,
                                        withDismissAction = true,
                                    )
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        message = "Sync failed: ${e.message}",
                                        duration = SnackbarDuration.Short,
                                        withDismissAction = true,
                                    )
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        onErrorLogs = onErrorLogs,
                    )

                    // Main content Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        val contentPadding = if (current is Screen.Home) {
                            PaddingValues(0.dp)
                        } else {
                            PaddingValues(top = 66.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        ) {
                            content()
                        }

                        TopBar(
                            showBack = showBack,
                            onBack = { navController.goBack() },
                            isHome = current is Screen.Home,
                        )

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                        )
                    }
                }
            }
}

@Composable
private fun NavigationDock(
    current: Screen,
    isSyncing: Boolean,
    onNavigate: (Screen) -> Unit,
    onSync: () -> Unit,
    onErrorLogs: () -> Unit,
) {
    val savedRepos by DesktopRepositoryManager.savedRepositories.collectAsState()

    Surface(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon at the top
            Spacer(Modifier.height(20.dp))
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource("logo_ui.png"),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )

            // Main nav items, centered vertically
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                DockItem(icon = Icons.Default.Home, label = "Board", selected = current is Screen.Home, onClick = { onNavigate(Screen.Home) })
                DockItem(icon = Icons.Default.FavoriteBorder, label = "Library", selected = current is Screen.Library, onClick = { onNavigate(Screen.Library) })
                DockItem(
                    icon = Icons.Default.Extension,
                    label = "Extensions",
                    selected = current is Screen.Extensions,
                    badge = savedRepos.size.takeIf { it > 0 }?.toString(),
                    onClick = { onNavigate(Screen.Extensions) },
                )
                DockItem(icon = Icons.Default.Settings, label = "Settings", selected = current is Screen.Settings, onClick = { onNavigate(Screen.Settings) })
            }

            // Bottom actions, pushed to bottom
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                DockItem(
                    icon = Icons.Default.Sync,
                    label = "Sync",
                    selected = false,
                    onClick = onSync,
                )
                DockItem(icon = Icons.Default.BugReport, label = "Logs", selected = false, onClick = onErrorLogs)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: String? = null,

    onClick: () -> Unit,
) {
    val itemInteraction = remember { MutableInteractionSource() }
    val isHovered by itemInteraction.collectIsHoveredAsState()

    val theme = LocalDesktopTheme.current
    val iconTint = when {
        selected -> MaterialTheme.colorScheme.primary
        isHovered -> theme.TextPrimary
        else -> theme.TextMuted
    }
    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            isHovered -> theme.SurfaceElevated
            else -> Color.Transparent
        },
        label = "dockItemBg",
    )



    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .hoverable(itemInteraction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
    }
}

// Top Navigation Bar
@Composable
private fun TopBar(
    showBack: Boolean,
    onBack: () -> Unit,
    isHome: Boolean,
) {
    val bg = if (isHome) Color.Transparent else MaterialTheme.colorScheme.surface
    Column(modifier = Modifier.fillMaxWidth().background(bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                val theme = LocalDesktopTheme.current
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(theme.SurfaceElevated.copy(alpha = 0.5f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
        }
        if (!isHome) {
            HorizontalDivider(color = LocalDesktopTheme.current.Divider, thickness = 0.5.dp)
        }
    }
}
