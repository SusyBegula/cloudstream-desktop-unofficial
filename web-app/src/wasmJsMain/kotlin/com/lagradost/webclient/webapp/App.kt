package com.lagradost.webclient.webapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.lagradost.webclient.client.ApiClient
import com.lagradost.webclient.client.DetailsViewModel
import com.lagradost.webclient.client.HomeViewModel
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.client.Screen
import com.lagradost.webclient.webapp.theme.AppearanceConfig
import com.lagradost.webclient.webapp.theme.LocalDesktopTheme
import com.lagradost.webclient.webapp.theme.accentColorFromName
import com.lagradost.webclient.webapp.theme.buildColorScheme
import com.lagradost.webclient.webapp.theme.buildDesktopColors

/** Same-origin API base — the browser hits the server that served this page. */
private fun apiBaseUrl(): String = kotlinx.browser.window.location.origin

@Composable
fun App() {
    val accentName by AppearanceConfig.themeAccent.collectAsState()
    val amoled by AppearanceConfig.amoledMode.collectAsState()
    val lightMode by AppearanceConfig.isLightMode.collectAsState()

    val primaryColor = accentColorFromName(accentName)
    val desktopColors = buildDesktopColors(primaryColor, lightMode, amoled)
    val colorScheme = buildColorScheme(primaryColor, desktopColors, lightMode)

    CompositionLocalProvider(LocalDesktopTheme provides desktopColors) {
        MaterialTheme(colorScheme = colorScheme) {
            Surface(Modifier.fillMaxSize(), color = colorScheme.background) {
                val scope = rememberCoroutineScope()
                val api = remember { ApiClient(apiBaseUrl()) }
                val nav = remember { NavController() }
                val homeViewModel = remember { HomeViewModel(api, scope) }
                val detailsViewModel = remember { DetailsViewModel(api, scope) }

                val screen by nav.currentScreen.collectAsState()

                AppShell(nav) {
                    when (val current = screen) {
                        is Screen.Home -> HomeScreen(homeViewModel, nav)
                        is Screen.Details -> {
                            LaunchedEffect(current) { detailsViewModel.load(current.providerName, current.url) }
                            DetailsScreen(detailsViewModel, current, nav, api)
                        }
                        is Screen.Player -> PlayerScreen(api, current, nav)
                        is Screen.Library -> LibraryScreen(api, nav)
                        is Screen.Extensions -> ExtensionsScreen(api, nav)
                        is Screen.Settings -> SettingsScreen(api, nav)
                        is Screen.CategoryGrid -> CategoryGridScreen(current, nav)
                    }
                }
            }
        }
    }
}
