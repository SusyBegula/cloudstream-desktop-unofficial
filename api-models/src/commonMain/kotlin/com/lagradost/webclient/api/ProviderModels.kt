package com.lagradost.webclient.api

import kotlinx.serialization.Serializable

/**
 * Wire-format mirror of MainAPI's provider metadata.
 * [providerType]/[supportedTypes] carry the enum's `.name` values (e.g. "DirectProvider", "Movie")
 * rather than a mirrored enum, so this DTO doesn't need to track the submodule's enums 1:1.
 */
@Serializable
data class ProviderDto(
    val name: String,
    val mainUrl: String,
    val lang: String,
    val hasMainPage: Boolean,
    val hasQuickSearch: Boolean,
    val providerType: String,
    val supportedTypes: List<String>,
)

@Serializable
data class ProviderListResponse(
    val providers: List<ProviderDto>,
)

@Serializable
data class MainPageRowDto(
    val name: String,
    val items: List<SearchResultDto>,
    val isHorizontalImages: Boolean = false,
)

@Serializable
data class HomePageResponseDto(
    val rows: List<MainPageRowDto>,
    val hasNext: Boolean,
)

/** A single homepage category's metadata, without fetching its items (no network call). */
@Serializable
data class MainPageCategoryDto(
    val index: Int,
    val name: String,
    val isHorizontalImages: Boolean = false,
)

@Serializable
data class MainPageCategoriesResponse(
    val categories: List<MainPageCategoryDto>,
)
