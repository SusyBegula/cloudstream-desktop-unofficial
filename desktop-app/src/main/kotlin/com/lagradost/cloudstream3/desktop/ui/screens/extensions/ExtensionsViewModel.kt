package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LocalPlugin(
    val file: File,
    val name: String,
    val internalName: String,
    val version: Int,
    val iconUrl: String?,
    val repoName: String,
    val language: String?,
    val tvTypes: List<String>?,
)

class ExtensionsViewModel(private val coroutineScope: CoroutineScope) {
    val savedRepositories = DesktopRepositoryManager.savedRepositories

    private val _isFetching = MutableStateFlow(false)
    val isFetching = _isFetching.asStateFlow()

    private val _statusText = MutableStateFlow("Press Sync (sidebar) or Fetch below to load plugins from your repositories.")
    val statusText = _statusText.asStateFlow()

    private val _plugins = MutableStateFlow<List<Pair<String, SitePlugin>>>(emptyList())
    val plugins = _plugins.asStateFlow()

    private val _installedPlugins = MutableStateFlow<List<LocalPlugin>>(emptyList())
    val installedPlugins = _installedPlugins.asStateFlow()

    private val _pluginRequiringBypass = MutableStateFlow<Pair<String, SitePlugin>?>(null)
    val pluginRequiringBypass = _pluginRequiringBypass.asStateFlow()

    fun fetchPlugins() {
        _isFetching.value = true
        _statusText.value = "Fetching plugins from repositories..."
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.syncAll()
                }
                _plugins.value = DesktopRepositoryManager.getAllPlugins()
                _statusText.value = "Fetched ${_plugins.value.size} plugins from ${DesktopRepositoryManager.getSavedRepositories().size} repositories."
                refreshInstalled()
            } catch (e: Throwable) {
                _statusText.value = "Error: ${e.message}"
            } finally {
                _isFetching.value = false
            }
        }
    }

    fun loadPluginsFromManager() {
        _plugins.value = DesktopRepositoryManager.getAllPlugins()
        _statusText.value = "Showing ${_plugins.value.size} plugins from ${DesktopRepositoryManager.getSavedRepositories().size} repositories."
        refreshInstalled()
    }

    fun refreshInstalled() {
        val list = mutableListOf<LocalPlugin>()
        val extensionsDir = DesktopRepositoryManager.getExtensionsDir()
        val allRemote = DesktopRepositoryManager.getAllPlugins()
        if (extensionsDir.exists()) {
            extensionsDir.walkTopDown()
                .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
                .filter { !it.name.endsWith("-jvm.jar") }
                .forEach { jar ->
                    val manifest = DesktopRepositoryManager.readPluginManifest(jar)
                    val name = manifest?.get("name") as? String ?: jar.nameWithoutExtension
                    val internalName = manifest?.get("internalName") as? String ?: name
                    val version = manifest?.get("version")?.toString()?.toIntOrNull() ?: 0
                    val iconUrl = manifest?.get("iconUrl") as? String

                    val remoteMatch = allRemote.find { it.second.internalName == internalName }
                    val repoName = remoteMatch?.first ?: jar.parentFile.name.replace("_", " ")

                    val rawTvTypes = manifest?.get("tvTypes")
                    val tvTypes = when (rawTvTypes) {
                        is List<*> -> rawTvTypes.filterIsInstance<String>()
                        is String -> listOf(rawTvTypes)
                        else -> remoteMatch?.second?.tvTypes ?: emptyList()
                    }
                    val language = manifest?.get("language") as? String ?: remoteMatch?.second?.language

                    list.add(LocalPlugin(jar, name, internalName, version, iconUrl, repoName, language, tvTypes))
                }
        }
        _installedPlugins.value = list
    }

    fun getRemotePluginsForRepo(repo: RepositoryData): List<SitePlugin> {
        val direct = DesktopRepositoryManager.getPluginsForRepository(repo.url)
        if (direct.isNotEmpty()) return direct
        return _plugins.value.filter { it.first.equals(repo.name, ignoreCase = true) }.map { it.second }
    }

    fun getInstalledPluginsForRepo(repo: RepositoryData): List<LocalPlugin> {
        val repoPlugins = getRemotePluginsForRepo(repo)
        val internalNames = repoPlugins.map { it.internalName }.toSet()
        return _installedPlugins.value.filter { installed ->
            internalNames.contains(installed.internalName) || installed.repoName.equals(repo.name, ignoreCase = true)
        }
    }

    fun getLocalPluginsNotFromSavedRepos(): List<LocalPlugin> {
        val savedRepos = DesktopRepositoryManager.getSavedRepositories()
        val allRemoteNames = savedRepos.flatMap { repo ->
            getRemotePluginsForRepo(repo).map { it.internalName }
        }.toSet()
        return _installedPlugins.value.filter { !allRemoteNames.contains(it.internalName) }
    }

    fun addRepository(url: String, onResult: (Boolean, String) -> Unit) {
        if (url.isBlank()) return
        coroutineScope.launch {
            try {
                val addedRepos = withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.addRepositoryFromInput(url)
                }
                if (addedRepos != null && addedRepos.isNotEmpty()) {
                    val repoNames = addedRepos.take(2).joinToString { it.name } + if (addedRepos.size > 2) " and ${addedRepos.size - 2} more" else ""
                    fetchPlugins()
                    onResult(true, "Added: $repoNames")
                } else {
                    onResult(false, "Failed to load repository. Check the URL or shortcode.")
                }
            } catch (e: Throwable) {
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    fun removeRepository(url: String) {
        DesktopRepositoryManager.removeRepository(url)
        _plugins.value = DesktopRepositoryManager.getAllPlugins()
        refreshInstalled()
    }

    fun installPlugin(repoName: String, plugin: SitePlugin, onResult: (String) -> Unit) {
        coroutineScope.launch {
            try {
                val jarFile = withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.downloadPlugin(repoName, plugin)
                }
                if (jarFile != null) {
                    withContext(Dispatchers.IO) {
                        ExtensionLoader.unloadPlugin(jarFile.absolutePath)
                        ExtensionLoader.loadAndInit(jarFile)
                    }
                    onResult("Installed")
                    refreshInstalled()
                } else {
                    onResult("Failed")
                }
            } catch (e: java.lang.SecurityException) {
                com.lagradost.common.logging.AppLogger.e("Security exception removing plugin", e)
                _pluginRequiringBypass.value = Pair(repoName, plugin)
                onResult("Blocked (Security)")
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading plugin", e)
                onResult("Error")
            }
        }
    }

    fun bypassSecurityAndInstall(repoName: String, plugin: SitePlugin) {
        _pluginRequiringBypass.value = null
        coroutineScope.launch {
            try {
                val jarFile = withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.downloadPlugin(repoName, plugin)
                }
                if (jarFile != null) {
                    withContext(Dispatchers.IO) {
                        ExtensionLoader.unloadPlugin(jarFile.absolutePath)
                        ExtensionLoader.loadAndInit(jarFile, forceBypassSecurity = true)
                    }
                    refreshInstalled()
                    val currentSync = DesktopRepositoryManager.syncGeneration.value
                    DesktopRepositoryManager.syncGeneration.value = currentSync + 1
                }
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading plugin", e)
            }
        }
    }

    fun clearBypass() {
        _pluginRequiringBypass.value = null
    }

    fun uninstallPlugin(internalName: String) {
        val local = _installedPlugins.value.find { it.internalName == internalName }
        if (local != null) {
            uninstallPlugins(listOf(local))
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                val extensionsDir = DesktopRepositoryManager.getExtensionsDir()
                extensionsDir.walkTopDown().filter { it.isFile && (it.name == "$internalName.jar" || it.name == "$internalName.cs3") }.forEach { file ->
                    ExtensionLoader.unloadPlugin(file.absolutePath)
                    file.delete()
                    File(file.parentFile, "${file.nameWithoutExtension}-jvm.jar").delete()
                }
                refreshInstalled()
            }
        }
    }

    fun uninstallPlugins(plugins: List<LocalPlugin>) {
        coroutineScope.launch(Dispatchers.IO) {
            for (plugin in plugins) {
                try {
                    ExtensionLoader.unloadPlugin(plugin.file.absolutePath)
                    val parentDir = plugin.file.parentFile
                    plugin.file.delete()
                    File(parentDir, plugin.file.nameWithoutExtension + "-jvm.jar").delete()
                    if (parentDir != null && parentDir.listFiles()?.isEmpty() == true) {
                        parentDir.delete()
                    }
                } catch (e: Throwable) {
                    com.lagradost.common.logging.AppLogger.e("Error uninstalling plugin", e)
                }
            }
            refreshInstalled()
        }
    }

    fun loadLocalPlugin(file: File) {
        coroutineScope.launch(Dispatchers.IO) {
            val targetDir = File(DesktopRepositoryManager.getExtensionsDir(), "Local_Sandbox")
            targetDir.mkdirs()
            val targetFile = File(targetDir, file.name)
            file.copyTo(targetFile, overwrite = true)
            try {
                ExtensionLoader.loadAndInit(targetFile)
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("Error loading local plugin", e)
            }
            refreshInstalled()
        }
    }
}

