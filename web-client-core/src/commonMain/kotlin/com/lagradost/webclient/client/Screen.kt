package com.lagradost.webclient.client

import com.lagradost.webclient.api.SearchResultDto

/**
 * Web-client counterpart of desktop-app's ui/navigation/Screen.kt. Unlike the desktop version
 * (which embeds live MainAPI/SearchResponse objects), this holds only serializable identifiers
 * — the browser never has a MainAPI instance, it only knows provider names and URLs.
 */
sealed class Screen {
    object Home : Screen()
    object Extensions : Screen()
    object Library : Screen()
    object Settings : Screen()
    data class Details(val providerName: String, val url: String, val preloaded: SearchResultDto? = null) : Screen()
    data class CategoryGrid(val providerName: String, val title: String, val items: List<SearchResultDto>) : Screen()
    data class Player(val providerName: String, val dataUrl: String, val title: String) : Screen()
}
