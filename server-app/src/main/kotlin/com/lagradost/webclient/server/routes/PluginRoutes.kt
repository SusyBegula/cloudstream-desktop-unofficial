package com.lagradost.webclient.server.routes

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.webclient.api.ActionResult
import com.lagradost.webclient.api.InstallPluginRequest
import com.lagradost.webclient.api.InstalledPluginsResponse
import com.lagradost.webclient.api.PluginCatalogResponse
import com.lagradost.webclient.api.RepositoryDto
import com.lagradost.webclient.api.SitePluginDto
import com.lagradost.webclient.api.UninstallPluginRequest
import com.lagradost.webclient.server.plugins.SavedRepo
import com.lagradost.webclient.server.plugins.ServerPluginManager
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.toByteArray

private val jacksonMapper = ObjectMapper()
    .registerModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun Route.pluginRoutes() {
    get("/api/plugins/repositories") {
        val repos = ServerPluginManager.getSavedRepos()
            .filter { it.url.isNotBlank() }
            .map { RepositoryDto(it.name.ifBlank { "Repository" }, it.url) }
        call.respond(repos)
    }

    post("/api/plugins/repositories") {
        try {
            val body = call.receiveText()
            val node = jacksonMapper.readTree(body)
            val url = node.get("url")?.asText()?.trim() ?: ""
            val name = node.get("name")?.asText()?.trim() ?: ""
            if (url.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ActionResult(false, "Repository URL cannot be empty"))
                return@post
            }
            ServerPluginManager.addRepo(SavedRepo(name.ifBlank { "Repository" }, url))
            call.respond(ActionResult(true))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ActionResult(false, e.message ?: "Invalid request"))
        }
    }

    delete("/api/plugins/repositories") {
        try {
            val url = call.request.queryParameters["url"]
            if (url.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ActionResult(false, "Missing url parameter"))
                return@delete
            }
            ServerPluginManager.removeRepo(url)
            call.respond(ActionResult(true))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ActionResult(false, e.message))
        }
    }

    get("/api/plugins/catalog") {
        try {
            val repoUrl = call.request.queryParameters["repo"]
            if (repoUrl.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ActionResult(false, "Missing repo parameter"))
                return@get
            }
            val installedNames = ServerPluginManager.listInstalled().mapNotNull { it["sourcePlugin"] as? String }
            val plugins = ServerPluginManager.fetchCatalog(repoUrl).map { plugin ->
                SitePluginDto(
                    internalName = plugin.internalName,
                    name = plugin.name,
                    version = plugin.version,
                    fileName = "${plugin.internalName}.cs3",
                    url = plugin.jarUrl ?: plugin.url,
                    repositoryUrl = repoUrl,
                    isInstalled = installedNames.any { it.contains(plugin.internalName) },
                )
            }
            call.respond(PluginCatalogResponse(repoUrl, plugins))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ActionResult(false, e.message ?: "Failed to fetch catalog"))
        }
    }

    get("/api/plugins/installed") {
        val installed = ServerPluginManager.listInstalled().map {
            SitePluginDto(
                internalName = (it["name"] as? String).orEmpty(),
                name = (it["name"] as? String).orEmpty(),
                version = 0,
                fileName = (it["sourcePlugin"] as? String)?.substringAfterLast('/').orEmpty(),
                url = "",
                repositoryUrl = "",
                isInstalled = true,
            )
        }
        call.respond(InstalledPluginsResponse(installed))
    }

    post("/api/plugins/install") {
        try {
            val body = call.receiveText()
            val node = jacksonMapper.readTree(body)
            val repositoryUrl = node.get("repositoryUrl")?.asText()?.trim() ?: ""
            val internalName = node.get("internalName")?.asText()?.trim() ?: ""
            if (repositoryUrl.isBlank() || internalName.isBlank()) {
                call.respond(ActionResult(false, "repositoryUrl and internalName are required"))
                return@post
            }
            val catalog = ServerPluginManager.fetchCatalog(repositoryUrl)
            val plugin = catalog.firstOrNull { it.internalName == internalName }
            if (plugin == null) {
                call.respond(ActionResult(false, "Plugin not found in repository"))
                return@post
            }
            val result = ServerPluginManager.install(plugin)
            call.respond(result.fold({ ActionResult(true) }, { ActionResult(false, it.message) }))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ActionResult(false, e.message ?: "Install failed"))
        }
    }

    post("/api/plugins/uninstall") {
        try {
            val body = call.receiveText()
            val node = jacksonMapper.readTree(body)
            val internalName = node.get("internalName")?.asText()?.trim() ?: ""
            if (internalName.isBlank()) {
                call.respond(ActionResult(false, "internalName is required"))
                return@post
            }
            val result = ServerPluginManager.uninstall(internalName)
            call.respond(result.fold({ ActionResult(true) }, { ActionResult(false, it.message) }))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, ActionResult(false, e.message ?: "Uninstall failed"))
        }
    }

    // Web equivalent of desktop's native "Load Local Plugin" file picker.
    post("/api/plugins/upload") {
        var fileName: String? = null
        var bytes: ByteArray? = null
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem) {
                fileName = part.originalFileName ?: "upload.cs3"
                bytes = part.provider().toByteArray()
            }
            part.dispose()
        }
        val name = fileName
        val data = bytes
        if (name == null || data == null) {
            call.respond(ActionResult(false, "No file uploaded"))
            return@post
        }
        val result = ServerPluginManager.uploadInstall(name, data)
        call.respond(result.fold({ ActionResult(true) }, { ActionResult(false, it.message) }))
    }
}
