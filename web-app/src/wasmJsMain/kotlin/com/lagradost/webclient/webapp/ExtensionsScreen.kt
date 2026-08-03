package com.lagradost.webclient.webapp

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.client.ApiClient
import com.lagradost.webclient.client.ExtensionsViewModel
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.webapp.components.ExtensionCard
import com.lagradost.webclient.webapp.components.SectionHeader

private val TABS = listOf("Browse", "Installed", "Repositories")

/** Web-client port of desktop-app's Extensions screen (simplified: single file, no per-language/category filters). */
@Composable
fun ExtensionsScreen(api: ApiClient, nav: NavController) {
    val scope = rememberCoroutineScope()
    val vm = remember { ExtensionsViewModel(api, scope) }
    var tab by remember { mutableStateOf(0) }
    val repos by vm.repositories.collectAsState()
    val catalog by vm.catalog.collectAsState()
    val installed by vm.installed.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        vm.refreshRepositories()
        vm.refreshInstalled()
    }

    Column(Modifier.fillMaxSize()) {
        SectionHeader("Extensions")
        TabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> BrowseTab(repos, catalog, isLoading, onLoadCatalog = { vm.loadCatalog(it) }, onInstall = { vm.install(it) })
            1 -> InstalledTab(installed, onUninstall = { vm.uninstall(it) })
            else -> RepositoriesTab(repos, onAdd = { name, url -> vm.addRepository(name, url) }, onRemove = { vm.removeRepository(it) })
        }
    }
}

@Composable
private fun BrowseTab(
    repos: List<com.lagradost.webclient.api.RepositoryDto>,
    catalog: List<com.lagradost.webclient.api.SitePluginDto>,
    isLoading: Boolean,
    onLoadCatalog: (String) -> Unit,
    onInstall: (com.lagradost.webclient.api.SitePluginDto) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row {
            repos.forEach { repo ->
                Button(onClick = { onLoadCatalog(repo.url) }, modifier = Modifier.padding(end = 8.dp)) {
                    Text(repo.name)
                }
            }
        }
        if (isLoading) CircularProgressIndicator(Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(catalog) { plugin ->
                ExtensionCard(plugin, modifier = Modifier.padding(vertical = 4.dp), onInstall = { onInstall(plugin) })
            }
        }
    }
}

@Composable
private fun InstalledTab(installed: List<com.lagradost.webclient.api.SitePluginDto>, onUninstall: (com.lagradost.webclient.api.SitePluginDto) -> Unit) {
    if (installed.isEmpty()) {
        Text("No plugins installed.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(installed) { plugin ->
            ExtensionCard(plugin, modifier = Modifier.padding(vertical = 4.dp), onUninstall = { onUninstall(plugin) })
        }
    }
}

@Composable
private fun RepositoriesTab(repos: List<com.lagradost.webclient.api.RepositoryDto>, onAdd: (String, String) -> Unit, onRemove: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.padding(end = 8.dp))
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Repository URL") }, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = { if (url.isNotBlank()) { onAdd(name.ifBlank { url }, url); name = ""; url = "" } }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Add Repository")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(repos) { repo ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(repo.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { onRemove(repo.url) }) { Text("Remove") }
                }
            }
        }
    }
}
