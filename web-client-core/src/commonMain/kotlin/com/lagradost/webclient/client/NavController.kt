package com.lagradost.webclient.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Web-client counterpart of desktop-app's ui/navigation/NavController.kt. Uses a plain
 * StateFlow instead of Compose's mutableStateOf so this module has no Compose dependency —
 * :web-app's UI layer collects it as state when rendering.
 */
class NavController(startScreen: Screen = Screen.Home) {
    private val backStack = mutableListOf<Screen>()

    private val _currentScreen = MutableStateFlow(startScreen)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigate(screen: Screen) {
        backStack.add(_currentScreen.value)
        _currentScreen.value = screen
    }

    fun back(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        _currentScreen.value = previous
        return true
    }

    fun canGoBack(): Boolean = backStack.isNotEmpty()
}
