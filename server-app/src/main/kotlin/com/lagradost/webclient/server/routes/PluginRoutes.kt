package com.lagradost.webclient.server.routes

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
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.toByteArray

fun Route.pluginRoutes() {
    get("/api/plugins/repositories") {
        call.respond(ServerPluginManager.getSavedRepos().map { RepositoryDto(it.name, it.url) })
    }

    post("/api/plugins/repositories") {
        val repo = call.receive<RepositoryDto>()
        ServerPluginManager.addRepo(SavedRepo(repo.name, repo.url))
        call.respond(ActionResult(true))
    }

    delete("/api/plugins/repositories") {
        val url = call.request.queryParameters["url"]
        if (url == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@delete
        }
        ServerPluginManager.removeRepo(url)
        call.respond(ActionResult(true))
    }

    get("/api/plugins/catalog") {
        val repoUrl = call.request.queryParameters["repo"]
        if (repoUrl == null) {
            call.respond(HttpStatusCode.BadRequest)
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
        val request = call.receive<InstallPluginRequest>()
        val catalog = ServerPluginManager.fetchCatalog(request.repositoryUrl)
        val plugin = catalog.firstOrNull { it.internalName == request.internalName }
        if (plugin == null) {
            call.respond(ActionResult(false, "Plugin not found in repository"))
            return@post
        }
        val result = ServerPluginManager.install(plugin)
        call.respond(result.fold({ ActionResult(true) }, { ActionResult(false, it.message) }))
    }

    post("/api/plugins/uninstall") {
        val request = call.receive<UninstallPluginRequest>()
        val result = ServerPluginManager.uninstall(request.internalName)
        call.respond(result.fold({ ActionResult(true) }, { ActionResult(false, it.message) }))
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
