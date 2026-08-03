package com.lagradost.webclient.client

import com.lagradost.webclient.api.InstallPluginRequest
import com.lagradost.webclient.api.RepositoryDto
import com.lagradost.webclient.api.SitePluginDto
import com.lagradost.webclient.api.UninstallPluginRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Web-client counterpart of desktop-app's ExtensionsViewModel — repositories/catalog/installed plugins, backed by ApiClient. */
class ExtensionsViewModel(private val api: ApiClient, private val scope: CoroutineScope) {
    private val _repositories = MutableStateFlow<List<RepositoryDto>>(emptyList())
    val repositories: StateFlow<List<RepositoryDto>> = _repositories.asStateFlow()

    private val _catalog = MutableStateFlow<List<SitePluginDto>>(emptyList())
    val catalog: StateFlow<List<SitePluginDto>> = _catalog.asStateFlow()

    private val _installed = MutableStateFlow<List<SitePluginDto>>(emptyList())
    val installed: StateFlow<List<SitePluginDto>> = _installed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refreshRepositories() {
        scope.launch(Dispatchers.Default) {
            _repositories.value = runCatching { api.getRepositories() }.getOrDefault(emptyList())
        }
    }

    fun refreshInstalled() {
        scope.launch(Dispatchers.Default) {
            _installed.value = runCatching { api.getInstalledPlugins().plugins }.getOrDefault(emptyList())
        }
    }

    fun addRepository(name: String, url: String) {
        scope.launch(Dispatchers.Default) {
            runCatching { api.addRepository(RepositoryDto(name, url)) }
            refreshRepositories()
        }
    }

    fun removeRepository(url: String) {
        scope.launch(Dispatchers.Default) {
            runCatching { api.removeRepository(url) }
            refreshRepositories()
        }
    }

    fun loadCatalog(repoUrl: String) {
        scope.launch(Dispatchers.Default) {
            _isLoading.value = true
            _catalog.value = runCatching { api.getPluginCatalog(repoUrl).plugins }.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }

    fun install(plugin: SitePluginDto) {
        scope.launch(Dispatchers.Default) {
            val result = runCatching { api.installPlugin(InstallPluginRequest(plugin.repositoryUrl, plugin.internalName)) }
            _message.value = result.getOrNull()?.message ?: if (result.isSuccess) "Installed ${plugin.name}" else "Failed to install ${plugin.name}"
            refreshInstalled()
        }
    }

    fun uninstall(plugin: SitePluginDto) {
        scope.launch(Dispatchers.Default) {
            runCatching { api.uninstallPlugin(UninstallPluginRequest(plugin.internalName)) }
            refreshInstalled()
        }
    }
}
