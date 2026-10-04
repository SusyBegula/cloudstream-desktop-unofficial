package com.lagradost.cloudstream3.desktop.updater

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRelease(
    @param:JsonProperty("tag_name") val tagName: String = "",
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("body") val body: String? = null,
    @param:JsonProperty("html_url") val htmlUrl: String? = null,
    @param:JsonProperty("published_at") val publishedAt: String? = null,
    @param:JsonProperty("assets") val assets: List<GitHubReleaseAsset> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubReleaseAsset(
    @param:JsonProperty("name") val name: String = "",
    @param:JsonProperty("browser_download_url") val downloadUrl: String = "",
    @param:JsonProperty("size") val size: Long = 0L,
    @param:JsonProperty("content_type") val contentType: String? = null,
)

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpdateAvailable(
        val release: GitHubRelease,
        val asset: GitHubReleaseAsset?,
        val isAutomatic: Boolean,
    ) : UpdateUiState()
    data class UpToDate(val currentVersion: String) : UpdateUiState()
    data class Downloading(
        val release: GitHubRelease,
        val asset: GitHubReleaseAsset,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateUiState()
    data class ReadyToInstall(
        val installerFile: File,
        val release: GitHubRelease,
        val isAppImage: Boolean,
    ) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
