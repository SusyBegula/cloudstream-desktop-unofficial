package com.lagradost.webclient.api

import kotlinx.serialization.Serializable

@Serializable
data class SitePluginDto(
    val internalName: String,
    val name: String,
    val version: Int,
    val fileName: String,
    val url: String,
    val repositoryUrl: String,
    val isInstalled: Boolean,
)

@Serializable
data class RepositoryDto(
    val name: String,
    val url: String,
)

@Serializable
data class InstalledPluginsResponse(
    val plugins: List<SitePluginDto>,
)

@Serializable
data class PluginCatalogResponse(
    val repository: String,
    val plugins: List<SitePluginDto>,
)

@Serializable
data class InstallPluginRequest(
    val repositoryUrl: String,
    val internalName: String,
)

@Serializable
data class UninstallPluginRequest(
    val internalName: String,
)

@Serializable
data class ActionResult(
    val success: Boolean,
    val message: String? = null,
)
