package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.download.DownloadStatus
import com.lagradost.cloudstream3.desktop.download.FfmpegDownloadManager
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseCategory
import com.lagradost.cloudstream3.desktop.ui.screens.browse.headerTitle
import com.lagradost.cloudstream3.desktop.ui.screens.home.HomeViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.ProviderSelector
import com.lagradost.cloudstream3.desktop.updater.AppUpdateDialog
import com.lagradost.cloudstream3.desktop.updater.AppUpdateNotificationBanner
import com.lagradost.cloudstream3.desktop.updater.DesktopAppUpdater
import kotlinx.coroutines.delay

val LocalAppHeaderHeight = staticCompositionLocalOf { 80.dp }

@Composable
fun DesktopAppShell(
    navController: NavController,
    homeViewModel: HomeViewModel,
    showBack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val current = navController.currentScreen
    LaunchedEffect(Unit) {
        delay(2000L)
        DesktopAppUpdater.checkForUpdate(isAutomatic = true, scope = this)

        while (true) {
            delay(30 * 60 * 1000L)
            DesktopRepositoryManager.autoUpdatePlugins()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 1240.dp
            val headerHeight = if (compact) 120.dp else 80.dp
            val fullBleed = current is Screen.Home || current is Screen.Details
            CompositionLocalProvider(LocalAppHeaderHeight provides headerHeight) {
                Box(
                    Modifier.fillMaxSize().padding(
                        if (fullBleed) {
                            PaddingValues(0.dp)
                        } else {
                            PaddingValues(top = headerHeight + 20.dp, start = 32.dp, end = 32.dp, bottom = 12.dp)
                        },
                    ),
                ) { content() }
                Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    AppHeader(navController, homeViewModel, compact, fullBleed, showBack)
                    AppUpdateNotificationBanner()
                }
                AppUpdateDialog()
            }
        }
    }
}

@Composable
private fun AppHeader(
    navController: NavController,
    homeViewModel: HomeViewModel,
    compact: Boolean,
    fullBleed: Boolean,
    showBack: Boolean,
) {
    val current = navController.currentScreen
    val foreground = if (fullBleed) Color.White else MaterialTheme.colorScheme.onSurface
    val background = if (fullBleed) Color(0xFF090910) else MaterialTheme.colorScheme.surface
    val providers by homeViewModel.providers.collectAsState()
    val selectedProvider by homeViewModel.selectedProvider.collectAsState()
    val icons by homeViewModel.mergedPluginIcons.collectAsState()
    val repos by DesktopRepositoryManager.savedRepositories.collectAsState()
    val downloads by FfmpegDownloadManager.downloadsFlow.collectAsState()
    val activeDownloads = downloads.count { it.status == DownloadStatus.Downloading }

    CompositionLocalProvider(LocalContentColor provides foreground) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(
                    listOf(background.copy(alpha = 0.97f), background.copy(alpha = 0.82f), background.copy(alpha = if (fullBleed) 0f else 1f)),
                ),
            ).padding(horizontal = if (compact) 20.dp else 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(if (compact) 68.dp else 80.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (showBack) {
                        HeaderAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = navController::goBack)
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { navController.navigate(Screen.Home) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(painterResource("logo_ui.png"), contentDescription = null, modifier = Modifier.size(34.dp))
                        Text("Cloudstream", fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                if (!compact) HeaderNavigation(current) { navController.navigate(it) }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderAction(Icons.Default.Search, "Search", current is Screen.Search) { navController.navigate(Screen.Search) }
                    ProviderSelector(providers, selectedProvider, homeViewModel::selectProvider, icons)
                    HeaderAction(Icons.Default.Extension, "Extensions", current is Screen.Extensions, repos.size.takeIf { it > 0 }) {
                        navController.navigate(Screen.Extensions)
                    }
                    HeaderAction(Icons.Default.Download, "Downloads", current is Screen.Downloads, activeDownloads.takeIf { it > 0 }) {
                        navController.navigate(Screen.Downloads)
                    }
                    HeaderAction(Icons.Default.Settings, "Settings", current is Screen.Settings) { navController.navigate(Screen.Settings) }
                }
            }
            if (compact) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    HeaderNavigation(current) { navController.navigate(it) }
                }
            }
        }
    }
}

@Composable
private fun HeaderNavigation(current: Screen, onNavigate: (Screen) -> Unit) {
    val destinations = listOf("Home" to Screen.Home) +
        BrowseCategory.entries.map { it.headerTitle to Screen.Catalog(it) } +
        listOf("Favourites" to Screen.Library)
    Row(
        Modifier.selectableGroup().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        destinations.forEach { (label, destination) ->
            val selected = current == destination
            Column(
                Modifier.clip(RoundedCornerShape(8.dp)).selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = { onNavigate(destination) },
                ).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = LocalContentColor.current.copy(alpha = if (selected) 1f else 0.7f),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(18.dp, 2.dp).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderAction(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    badge: Int? = null,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            BadgedBox(badge = {
                if (badge != null) Badge { Text(badge.toString(), fontSize = 9.sp) }
            }) {
                Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current, modifier = Modifier.size(21.dp))
            }
        }
    }
}
