package com.lagradost.cloudstream3.desktop.ui.navigation

import com.lagradost.cloudstream3.desktop.ui.screens.browse.BrowseCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HeaderNavigationTest {
    @Test
    fun searchReturnsToTheCatalogThatOpenedIt() {
        val nav = NavController()
        val catalog = Screen.Catalog(BrowseCategory.ANIME)
        nav.navigate(catalog)
        nav.navigate(Screen.Search)
        nav.navigate(Screen.Search)
        nav.goBack()
        assertEquals(catalog, nav.currentScreen)
        nav.goBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canGoBack())
    }
}
