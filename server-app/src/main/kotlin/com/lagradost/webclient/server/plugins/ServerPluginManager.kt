package com.lagradost.webclient.server.plugins

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
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
import java.net.URI

@JsonIgnoreProperties(ignoreUnknown = true)
data class SitePlugin(
    @JsonProperty("name") val name: String,
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("jarUrl") val jarUrl: String? = null,
    @JsonProperty("version") val version: Int = 1,
    @JsonProperty("status") val status: Int = 1,
)

data class SavedRepo(val name: String, val url: String)

object ServerPluginManager {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .build()
            chain.proceed(req)
        }
        .build()

    private val redirectClient = OkHttpClient.Builder()
        .followRedirects(false)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .build()
            chain.proceed(req)
        }
        .build()

    private val mapper = ObjectMapper().registerModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

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
        val cleanUrl = repo.url.trim()
        val current = getSavedRepos().filterNot { it.url == cleanUrl } + SavedRepo(repo.name, cleanUrl)
        reposFile.writeText(mapper.writeValueAsString(current))
    }

    fun removeRepo(url: String) {
        reposFile.writeText(mapper.writeValueAsString(getSavedRepos().filterNot { it.url == url }))
    }

    suspend fun parseRepoUrl(url: String): String? = withContext(Dispatchers.IO) {
        val fixedUrl = url.trim()
        if (fixedUrl.matches(Regex("^[a-zA-Z0-9!_-]+$"))) {
            try {
                val req = Request.Builder().url("https://cutt.ly/$fixedUrl").build()
                redirectClient.newCall(req).execute().use { resp ->
                    val loc = resp.header("Location")
                    if (loc != null && !loc.startsWith("https://cutt.ly/404")) {
                        return@withContext loc
                    }
                }
            } catch (e: Exception) {
                AppLogger.i("Shortlink resolution failed for $fixedUrl")
            }
            return@withContext null
        }
        if (fixedUrl.contains(Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)"))) {
            val stripped = fixedUrl.replace(Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)"), "")
            return@withContext if (!stripped.startsWith("http")) "https://$stripped" else stripped
        }
        if (!fixedUrl.matches(Regex("^https?://.*"))) {
            return@withContext null
        }
        return@withContext fixedUrl
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                URI.create(baseUrl).resolve(relativeUrl).toString()
            }
        } catch (e: Exception) {
            relativeUrl
        }
    }

    suspend fun fetchCatalog(repoUrl: String): List<SitePlugin> = withContext(Dispatchers.IO) {
        val cleanUrl = parseRepoUrl(repoUrl) ?: repoUrl
        AppLogger.i("ServerPluginManager: Fetching catalog for $cleanUrl (original: $repoUrl)")
        val bodyText = fetchString(cleanUrl)
        if (bodyText == null) {
            AppLogger.e("ServerPluginManager: Empty response when fetching $cleanUrl")
            return@withContext emptyList()
        }

        if (bodyText.trimStart().startsWith("<")) {
            AppLogger.e("ServerPluginManager: Received HTML instead of JSON from $cleanUrl")
            return@withContext emptyList()
        }

        val plugins = mutableListOf<SitePlugin>()

        try {
            val rootNode = mapper.readTree(bodyText)

            if (rootNode.isObject && rootNode.has("pluginLists")) {
                val pluginLists = rootNode.get("pluginLists")
                if (pluginLists.isArray) {
                    for (node in pluginLists) {
                        val listUrl = node.asText()
                        val fullListUrl = resolveUrl(cleanUrl, listUrl)
                        val listBody = fetchString(fullListUrl) ?: continue
                        if (listBody.trimStart().startsWith("<")) continue
                        plugins.addAll(parsePluginsFromJson(listBody))
                    }
                }
            } else {
                plugins.addAll(parsePluginsFromJson(bodyText))
            }
        } catch (e: Exception) {
            AppLogger.e("ServerPluginManager: Error parsing repository JSON from $cleanUrl", e)
        }

        val result = plugins.filter { it.status != 0 }.distinctBy { it.internalName }
        AppLogger.i("ServerPluginManager: Successfully loaded ${result.size} plugins from $cleanUrl")
        result
    }

    private fun parsePluginsFromJson(json: String): List<SitePlugin> {
        val list = mutableListOf<SitePlugin>()
        try {
            val tree = mapper.readTree(json)
            val nodes = when {
                tree.isArray -> tree.asSequence().toList()
                tree.isObject && tree.has("plugins") && tree.get("plugins").isArray -> tree.get("plugins").asSequence().toList()
                tree.isObject -> listOf(tree)
                else -> emptyList()
            }
            for (node in nodes) {
                val name = node.get("name")?.asText() ?: node.get("pluginName")?.asText() ?: node.get("internalName")?.asText() ?: continue
                val internalName = node.get("internalName")?.asText() ?: name
                val url = node.get("url")?.asText() ?: node.get("jarUrl")?.asText() ?: ""
                val jarUrl = node.get("jarUrl")?.asText()
                val version = node.get("version")?.asInt(1) ?: 1
                val status = node.get("status")?.asInt(1) ?: 1
                if (url.isNotEmpty() || jarUrl != null) {
                    list.add(SitePlugin(name, internalName, url, jarUrl, version, status))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to parse plugins JSON node", e)
        }
        return list
    }

    private fun fetchString(url: String): String? {
        return try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) null else it.body.string()
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
                val bytes = it.body.bytes()
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
            // Installed filenames rarely match the provider's internalName (e.g. "MovieBoxProvider.jar"
            // vs internalName "MovieBox"), so look up the exact file via the provider's sourcePlugin,
            // which ExtensionLoader sets to the jar's absolute path at load time and keys unloadPlugin by.
            val sourcePath = synchronized(APIHolder.allProviders) {
                APIHolder.allProviders.firstOrNull { it.name == internalName }?.sourcePlugin
            }
            val file = sourcePath?.let { File(it) }?.takeIf { it.exists() }
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
