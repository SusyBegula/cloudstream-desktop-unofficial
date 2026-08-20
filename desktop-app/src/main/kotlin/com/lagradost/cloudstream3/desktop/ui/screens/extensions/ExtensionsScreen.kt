package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.FlagImage
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.screens.PluginSettingsDialog
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ComposeExtensionScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { ExtensionsViewModel(coroutineScope) }
    val syncGeneration by DesktopRepositoryManager.syncGeneration.collectAsState()

    var selectedRepo by remember { mutableStateOf<RepositoryData?>(null) }
    var showLocalPlugins by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<RepositoryData?>(null) }
    val pluginRequiringBypass by viewModel.pluginRequiringBypass.collectAsState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            DesktopRepositoryManager.syncAll()
        }
        viewModel.fetchPlugins()
    }

    LaunchedEffect(syncGeneration) {
        viewModel.loadPluginsFromManager()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedRepo == null && !showLocalPlugins) {
            ExtensionsListView(
                viewModel = viewModel,
                syncGeneration = syncGeneration,
                onSelectRepo = { repo -> selectedRepo = repo },
                onSelectLocal = { showLocalPlugins = true },
                onAddClick = { showAddDialog = true },
                onDeleteRepo = { repo -> repoToDelete = repo },
            )
        } else if (selectedRepo != null) {
            ExtensionDetailView(
                repo = selectedRepo!!,
                viewModel = viewModel,
                syncGeneration = syncGeneration,
                onBack = { selectedRepo = null },
            )
        } else {
            LocalPluginsDetailView(
                viewModel = viewModel,
                syncGeneration = syncGeneration,
                onBack = { showLocalPlugins = false },
            )
        }

        // Add Repository Dialog
        if (showAddDialog) {
            AddExtensionDialog(
                viewModel = viewModel,
                onDismiss = { showAddDialog = false },
            )
        }

        // Confirm Delete Repository Dialog
        repoToDelete?.let { repo ->
            AlertDialog(
                onDismissRequest = { repoToDelete = null },
                title = { Text("Remove Extension") },
                text = { Text("Are you sure you want to remove \"${repo.name}\"? Installed plugins from this extension will not be uninstalled automatically.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.removeRepository(repo.url)
                            repoToDelete = null
                        },
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { repoToDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Security Sandbox Bypass Warning Dialog
        pluginRequiringBypass?.let { (bypassRepo, bypassPlugin) ->
            AlertDialog(
                onDismissRequest = { viewModel.clearBypass() },
                title = { Text("Security Sandbox Warning") },
                text = {
                    Text("Potentially unsafe code was detected in ${bypassPlugin.name}.\n\nThis plugin requested permissions outside the standard sandbox. Installing this is NOT recommended unless you trust the author.\n\nDo you want to bypass the sandbox and install it anyway?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.bypassSecurityAndInstall(bypassRepo, bypassPlugin)
                        },
                    ) {
                        Text("Install Anyway", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearBypass() }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

/**
 * Default View: Lists all installed Extensions (Repositories) and Local Plugins.
 */
@Composable
private fun ExtensionsListView(
    viewModel: ExtensionsViewModel,
    syncGeneration: Int,
    onSelectRepo: (RepositoryData) -> Unit,
    onSelectLocal: () -> Unit,
    onAddClick: () -> Unit,
    onDeleteRepo: (RepositoryData) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val savedRepos by viewModel.savedRepositories.collectAsState()
    val installedPlugins by viewModel.installedPlugins.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    val statusText by viewModel.statusText.collectAsState()

    val localPlugins = remember(installedPlugins, savedRepos) {
        viewModel.getLocalPluginsNotFromSavedRepos()
    }

    val filteredRepos = remember(savedRepos, searchQuery) {
        if (searchQuery.isBlank()) savedRepos
        else savedRepos.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val extMinSize = when (gridScale) {
        "Compact" -> 300.dp
        "Large" -> 440.dp
        else -> 360.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Search field
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search installed extensions...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            // Add Extension Button
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.Accent),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Extension", fontWeight = FontWeight.Bold)
            }

            // Sync Repos Button
            FilledTonalButton(
                onClick = { viewModel.fetchPlugins() },
                enabled = !isFetching,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.height(48.dp),
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Repos")
                }
            }

            // Load Local Button
            OutlinedButton(
                onClick = {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Load Local Plugin (.cs3 / .jar)", java.awt.FileDialog.LOAD)
                    dialog.file = "*.cs3;*.jar"
                    dialog.isVisible = true
                    if (dialog.file != null) {
                        val sourceFile = java.io.File(dialog.directory, dialog.file)
                        viewModel.loadLocalPlugin(sourceFile)
                    }
                },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load Local")
            }
        }

        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = DesktopUi.TextMuted,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
            )
        }

        if (savedRepos.isEmpty() && localPlugins.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = DesktopUi.SurfaceElevated,
                        modifier = Modifier.size(80.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                tint = DesktopUi.Accent,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "No Extensions Installed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DesktopUi.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add a repository URL or shortcode to browse and install providers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DesktopUi.TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.Accent),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Extension", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = extMinSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Sideloaded / Local plugins card if any exist
                if (localPlugins.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clickable { onSelectLocal() },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.3f)),
                            colors = CardDefaults.cardColors(containerColor = DesktopUi.SurfaceElevated.copy(alpha = 0.85f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = DesktopUi.Accent.copy(alpha = 0.15f),
                                        modifier = Modifier.size(52.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.FolderZip,
                                                contentDescription = null,
                                                tint = DesktopUi.Accent,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            "Local & Custom Plugins",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = DesktopUi.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${localPlugins.size} provider(s) loaded manually",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DesktopUi.TextMuted,
                                        )
                                    }
                                }

                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "View Providers",
                                    tint = DesktopUi.TextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // Extension Repositories
                items(filteredRepos, key = { it.url }) { repo ->
                    val repoPlugins = remember(repo, syncGeneration) {
                        viewModel.getRemotePluginsForRepo(repo)
                    }
                    val installedInRepo = remember(repo, installedPlugins, syncGeneration) {
                        viewModel.getInstalledPluginsForRepo(repo)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clickable { onSelectRepo(repo) },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        colors = CardDefaults.cardColors(containerColor = DesktopUi.SurfaceElevated.copy(alpha = 0.85f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!repo.iconUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = repo.iconUrl,
                                        contentDescription = repo.name,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.05f)),
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = DesktopUi.SurfaceElevated,
                                        border = BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.3f)),
                                        modifier = Modifier.size(52.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Extension,
                                                contentDescription = null,
                                                tint = DesktopUi.Accent,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = repo.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DesktopUi.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = repo.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DesktopUi.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (installedInRepo.isNotEmpty()) DesktopUi.Accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f),
                                        ) {
                                            Text(
                                                text = if (repoPlugins.isNotEmpty()) {
                                                    "${installedInRepo.size} / ${repoPlugins.size} installed"
                                                } else {
                                                    "${installedInRepo.size} installed"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (installedInRepo.isNotEmpty()) DesktopUi.Accent else DesktopUi.TextMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onDeleteRepo(repo) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Remove Extension",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Open Extension",
                                    tint = DesktopUi.TextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Detail View: Shows all providers inside a selected Extension (Repository) with option to install/uninstall.
 */
@Composable
private fun ExtensionDetailView(
    repo: RepositoryData,
    viewModel: ExtensionsViewModel,
    syncGeneration: Int,
    onBack: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var languageFilter by remember { mutableStateOf("All") }
    var categoryFilter by remember { mutableStateOf("All") }
    var statusFilter by remember { mutableStateOf("All") } // All, Installed, Not Installed

    var showLangDropdown by remember { mutableStateOf(false) }
    var showCatDropdown by remember { mutableStateOf(false) }
    var showStatusDropdown by remember { mutableStateOf(false) }

    val installedPlugins by viewModel.installedPlugins.collectAsState()
    val remoteIcons by DesktopRepositoryManager.remotePluginIcons.collectAsState()

    var repoPlugins by remember { mutableStateOf<List<SitePlugin>>(emptyList()) }
    var isLoadingPlugins by remember { mutableStateOf(true) }

    LaunchedEffect(repo, syncGeneration) {
        isLoadingPlugins = true
        withContext(Dispatchers.IO) {
            repoPlugins = DesktopRepositoryManager.fetchPluginsForRepository(repo.url)
        }
        isLoadingPlugins = false
    }

    val languages = remember(repoPlugins) {
        listOf("All") + repoPlugins.mapNotNull { it.language?.takeIf { l -> l.isNotBlank() } }.distinct().sorted()
    }
    val categories = remember(repoPlugins) {
        listOf("All") + repoPlugins.flatMap { it.tvTypes ?: emptyList() }.distinct().sorted()
    }

    val filteredPlugins = remember(repoPlugins, searchQuery, languageFilter, categoryFilter, statusFilter, installedPlugins) {
        val installedSet = installedPlugins.map { it.internalName }.toSet()
        repoPlugins.filter { plugin ->
            val matchesSearch = searchQuery.isBlank() ||
                plugin.name.contains(searchQuery, ignoreCase = true) ||
                plugin.internalName.contains(searchQuery, ignoreCase = true)

            val matchesLang = languageFilter == "All" || plugin.language.equals(languageFilter, ignoreCase = true)
            val matchesCat = categoryFilter == "All" || (plugin.tvTypes?.contains(categoryFilter) == true)

            val isInstalled = installedSet.contains(plugin.internalName)
            val matchesStatus = when (statusFilter) {
                "Installed" -> isInstalled
                "Not Installed" -> !isInstalled
                else -> true
            }

            matchesSearch && matchesLang && matchesCat && matchesStatus
        }.sortedWith(
            compareByDescending<SitePlugin> { installedSet.contains(it.internalName) }
                .thenBy { it.name.lowercase() }
        )
    }

    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val extMinSize = when (gridScale) {
        "Compact" -> 280.dp
        "Large" -> 400.dp
        else -> 340.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.SurfaceElevated),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = DesktopUi.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back to Extensions", color = DesktopUi.TextPrimary, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (!repo.iconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = repo.iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DesktopUi.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${repoPlugins.size} providers available in this extension",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopUi.TextMuted,
                )
            }
        }

        // Search & Filters Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search providers in ${repo.name}...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            // Language Filter
            Box {
                FilledTonalButton(
                    onClick = { showLangDropdown = true },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    if (languageFilter == "All") {
                        Text("All Languages")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagImage(languageFilter, modifier = Modifier.padding(end = 6.dp))
                            Text(languageFilter.uppercase())
                        }
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = showLangDropdown, onDismissRequest = { showLangDropdown = false }) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                if (lang == "All") {
                                    Text("All Languages")
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FlagImage(lang, modifier = Modifier.padding(end = 8.dp))
                                        Text(lang.uppercase())
                                    }
                                }
                            },
                            onClick = {
                                languageFilter = lang
                                showLangDropdown = false
                            },
                        )
                    }
                }
            }

            // Category Filter
            Box {
                FilledTonalButton(
                    onClick = { showCatDropdown = true },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(if (categoryFilter == "All") "All Categories" else categoryFilter)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = showCatDropdown, onDismissRequest = { showCatDropdown = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(if (cat == "All") "All Categories" else cat) },
                            onClick = {
                                categoryFilter = cat
                                showCatDropdown = false
                            },
                        )
                    }
                }
            }

            // Status Filter (All, Installed, Not Installed)
            Box {
                FilledTonalButton(
                    onClick = { showStatusDropdown = true },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(if (statusFilter == "All") "All Status" else statusFilter)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = showStatusDropdown, onDismissRequest = { showStatusDropdown = false }) {
                    listOf("All", "Installed", "Not Installed").forEach { st ->
                        DropdownMenuItem(
                            text = { Text(st) },
                            onClick = {
                                statusFilter = st
                                showStatusDropdown = false
                            },
                        )
                    }
                }
            }
        }

        if (isLoadingPlugins) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DesktopUi.Accent)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading providers from repository...", color = DesktopUi.TextMuted)
                }
            }
        } else if (filteredPlugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (repoPlugins.isEmpty()) "No providers found in this repository." else "No providers matching current filters.",
                    color = DesktopUi.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = extMinSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(filteredPlugins, key = { it.internalName }) { plugin ->
                    val isPluginInstalled = installedPlugins.any { it.internalName == plugin.internalName }
                    val localPlugin = installedPlugins.find { it.internalName == plugin.internalName }
                    val isUpdateAvailable = localPlugin != null && plugin.version > localPlugin.version

                    val iconUrl = plugin.iconUrl
                        ?: remoteIcons[plugin.internalName]
                        ?: remoteIcons[plugin.name]
                        ?: repo.iconUrl

                    var isInstalling by remember { mutableStateOf(false) }
                    var installStatus by remember { mutableStateOf("") }
                    var showSettingsDialog by remember { mutableStateOf(false) }

                    val prefName = plugin.internalName + "_"
                    val hasSchemaSettings = remember(plugin) {
                        com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(prefName)
                    }

                    ProviderItemCard(
                        name = plugin.name,
                        version = plugin.version,
                        installedVersion = localPlugin?.version,
                        language = plugin.language,
                        tvTypes = plugin.tvTypes,
                        iconUrl = iconUrl,
                        isInstalled = isPluginInstalled,
                        isUpdateAvailable = isUpdateAvailable,
                        isInstalling = isInstalling,
                        installStatus = installStatus,
                        hasSettings = hasSchemaSettings,
                        onInstall = {
                            isInstalling = true
                            installStatus = "Installing..."
                            viewModel.installPlugin(repo.name, plugin) { result ->
                                isInstalling = false
                                installStatus = result
                            }
                        },
                        onUninstall = {
                            viewModel.uninstallPlugin(plugin.internalName)
                        },
                        onSettings = {
                            showSettingsDialog = true
                        },
                    )

                    if (showSettingsDialog) {
                        PluginSettingsDialog(
                            pluginName = plugin.name,
                            prefName = prefName,
                            onDismiss = { showSettingsDialog = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detail View: Shows locally loaded plugins (from Local_Sandbox or disk).
 */
@Composable
private fun LocalPluginsDetailView(
    viewModel: ExtensionsViewModel,
    syncGeneration: Int,
    onBack: () -> Unit,
) {
    val installedPlugins by viewModel.installedPlugins.collectAsState()
    val savedRepos by viewModel.savedRepositories.collectAsState()
    val remoteIcons by DesktopRepositoryManager.remotePluginIcons.collectAsState()

    val localPlugins = remember(installedPlugins, savedRepos, syncGeneration) {
        viewModel.getLocalPluginsNotFromSavedRepos()
    }

    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val extMinSize = when (gridScale) {
        "Compact" -> 280.dp
        "Large" -> 400.dp
        else -> 340.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.SurfaceElevated),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = DesktopUi.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back to Extensions", color = DesktopUi.TextPrimary, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Local & Custom Plugins",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DesktopUi.TextPrimary,
                )
                Text(
                    text = "${localPlugins.size} locally sideloaded provider(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopUi.TextMuted,
                )
            }
        }

        if (localPlugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("No local plugins found.", color = DesktopUi.TextMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = extMinSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(localPlugins, key = { it.file.absolutePath }) { plugin ->
                    val finalIcon = plugin.iconUrl ?: remoteIcons[plugin.internalName] ?: remoteIcons[plugin.name]
                    val prefName = plugin.internalName + "_"
                    var showSettingsDialog by remember { mutableStateOf(false) }

                    val hasSchemaSettings = remember(plugin) {
                        com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(prefName)
                    }

                    ProviderItemCard(
                        name = plugin.name,
                        version = plugin.version,
                        installedVersion = plugin.version,
                        language = plugin.language,
                        tvTypes = plugin.tvTypes,
                        iconUrl = finalIcon,
                        isInstalled = true,
                        isUpdateAvailable = false,
                        isInstalling = false,
                        installStatus = "",
                        hasSettings = hasSchemaSettings,
                        onInstall = {},
                        onUninstall = {
                            viewModel.uninstallPlugins(listOf(plugin))
                        },
                        onSettings = {
                            showSettingsDialog = true
                        },
                    )

                    if (showSettingsDialog) {
                        PluginSettingsDialog(
                            pluginName = plugin.name,
                            prefName = prefName,
                            onDismiss = { showSettingsDialog = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Card for an individual Provider / Plugin inside an Extension.
 */
@Composable
private fun ProviderItemCard(
    name: String,
    version: Int,
    installedVersion: Int?,
    language: String?,
    tvTypes: List<String>?,
    iconUrl: String?,
    isInstalled: Boolean,
    isUpdateAvailable: Boolean,
    isInstalling: Boolean,
    installStatus: String,
    hasSettings: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = DesktopUi.SurfaceElevated.copy(alpha = 0.85f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            if (!iconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = name,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f)),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DesktopUi.SurfaceElevated,
                    border = BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.3f)),
                    modifier = Modifier.size(50.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint = DesktopUi.Accent,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = DesktopUi.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FlagImage(language)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isUpdateAvailable) "v$installedVersion → v$version (Update)" else "v$version",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUpdateAvailable) DesktopUi.Accent else DesktopUi.TextMuted,
                )

                if (!tvTypes.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tvTypes.take(2).forEach { type ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White.copy(alpha = 0.06f),
                            ) {
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = DesktopUi.TextMuted,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = DesktopUi.Accent,
                        strokeWidth = 2.dp,
                    )
                } else if (isUpdateAvailable) {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.Accent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("Update", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else if (isInstalled) {
                    if (hasSettings) {
                        IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = DesktopUi.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Installed indicator + Delete button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DesktopUi.Accent.copy(alpha = 0.15f),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = DesktopUi.Accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Installed",
                                color = DesktopUi.Accent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    IconButton(
                        onClick = onUninstall,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Uninstall",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onInstall,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = DesktopUi.Accent.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(
                            "Install",
                            color = DesktopUi.Accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Modal Dialog to Add an Extension Repository by URL or Shortcode.
 */
@Composable
private fun AddExtensionDialog(
    viewModel: ExtensionsViewModel,
    onDismiss: () -> Unit,
) {
    var inputUrl by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = DesktopUi.Accent)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Add Extension Repository", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Enter a repository URL or short code to add extensions to your app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopUi.TextMuted,
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("e.g. phisher, hexated, or https://.../repo.json") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAdding,
                )
                if (statusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusText.startsWith("Error") || statusText.startsWith("Failed")) MaterialTheme.colorScheme.error else DesktopUi.Accent,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputUrl.isNotBlank()) {
                        isAdding = true
                        statusText = "Adding repository and fetching catalog..."
                        viewModel.addRepository(inputUrl) { success, msg ->
                            isAdding = false
                            if (success) {
                                onDismiss()
                            } else {
                                statusText = msg
                            }
                        }
                    }
                },
                enabled = inputUrl.isNotBlank() && !isAdding,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.Accent),
            ) {
                if (isAdding) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Add Repository", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAdding) {
                Text("Cancel")
            }
        },
    )
}
