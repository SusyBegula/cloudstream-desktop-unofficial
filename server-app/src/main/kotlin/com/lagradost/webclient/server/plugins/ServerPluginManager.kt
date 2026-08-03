package com.lagradost.webclient.server.plugins

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class RepoManifest(
    @JsonProperty("name") val name: String,
    @JsonProperty("pluginLists") val pluginLists: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SitePlugin(
    @JsonProperty("name") val name: String,
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("jarUrl") val jarUrl: String? = null,
    @JsonProperty("version") val version: Int = 1,
)

data class SavedRepo(val name: String, val url: String)

/**
 * Server-side, simplified port of desktop-app's DesktopRepositoryManager — repo/plugin catalog
 * fetch + install/uninstall only (no icon caching, sync reports, or legacy-format migration).
 * Duplicated rather than shared, per the "leave desktop-app untouched" decision.
 */
object ServerPluginManager {
    private val client = OkHttpClient()
    private val mapper = ObjectMapper().registerModule(kotlinModule())
    private val reposFile by lazy { File(PlatformPaths.extensionsDir.also { it.mkdirs() }, "repos.json") }

    fun getSavedRepos(): List<SavedRepo> {
        if (!reposFile.exists()) return emptyList()
        return try {
            mapper.readValue(reposFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRepo(repo: SavedRepo) {
        val current = getSavedRepos().filterNot { it.url == repo.url } + repo
        reposFile.writeText(mapper.writeValueAsString(current))
    }

    fun removeRepo(url: String) {
        reposFile.writeText(mapper.writeValueAsString(getSavedRepos().filterNot { it.url == url }))
    }

    suspend fun fetchCatalog(repoUrl: String): List<SitePlugin> = withContext(Dispatchers.IO) {
        val manifest = fetchJson<RepoManifest>(repoUrl) ?: return@withContext emptyList()
        manifest.pluginLists.flatMap { listUrl -> fetchJson<List<SitePlugin>>(listUrl) ?: emptyList() }
    }

    private inline fun <reified T> fetchJson(url: String): T? {
        return try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) return null
                mapper.readValue(it.body?.string() ?: return null)
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to fetch $url", e)
            null
        }
    }

    fun listInstalled(): List<Map<String, Any?>> {
        return synchronized(APIHolder.allProviders) {
            APIHolder.allProviders
                .filter { it.sourcePlugin != null && it.sourcePlugin != "built-in" }
                .map { mapOf("name" to it.name, "sourcePlugin" to it.sourcePlugin) }
        }
    }

    suspend fun install(plugin: SitePlugin): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = plugin.jarUrl ?: plugin.url
            val response = client.newCall(Request.Builder().url(downloadUrl).build()).execute()
            response.use {
                if (!it.isSuccessful) return@withContext Result.failure(Exception("Download failed: ${it.code}"))
                val bytes = it.body?.bytes() ?: return@withContext Result.failure(Exception("Empty response"))
                val extensionsDir = PlatformPaths.extensionsDir.also { dir -> dir.mkdirs() }
                val file = File(extensionsDir, "${plugin.internalName}.cs3")
                file.writeBytes(bytes)
                ExtensionLoader.loadAndInit(file)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun uninstall(internalName: String): Result<Unit> {
        return try {
            val extensionsDir = PlatformPaths.extensionsDir
            val file = extensionsDir.walkTopDown().firstOrNull { it.nameWithoutExtension == internalName && (it.extension == "cs3" || it.extension == "jar") }
            if (file != null) {
                ExtensionLoader.unloadPlugin(file.absolutePath)
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadInstall(fileName: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val extensionsDir = PlatformPaths.extensionsDir.also { it.mkdirs() }
            val file = File(extensionsDir, fileName)
            file.writeBytes(bytes)
            ExtensionLoader.loadAndInit(file)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
