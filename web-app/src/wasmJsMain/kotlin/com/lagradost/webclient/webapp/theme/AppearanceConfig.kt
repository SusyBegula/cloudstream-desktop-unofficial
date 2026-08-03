package com.lagradost.webclient.webapp.theme

import kotlinx.coroutines.flow.MutableStateFlow

private const val KEY_ACCENT = "cs_theme_accent"
private const val KEY_AMOLED = "cs_amoled_mode"
private const val KEY_LIGHT = "cs_light_mode"
private const val KEY_GRID_SCALE = "cs_grid_scale"

/** Web-client counterpart of desktop-app's ui/theme/AppearanceConfig.kt — localStorage-backed instead of DesktopDataStore. */
object AppearanceConfig {
    val themeAccent = MutableStateFlow(LocalPrefs.get(KEY_ACCENT) ?: "Purple")
    val amoledMode = MutableStateFlow(LocalPrefs.get(KEY_AMOLED) == "true")
    val isLightMode = MutableStateFlow(LocalPrefs.get(KEY_LIGHT) == "true")
    val gridScale = MutableStateFlow(LocalPrefs.get(KEY_GRID_SCALE) ?: "Normal")

    fun setThemeAccent(value: String) {
        themeAccent.value = value
        LocalPrefs.set(KEY_ACCENT, value)
    }

    fun setAmoledMode(value: Boolean) {
        amoledMode.value = value
        LocalPrefs.set(KEY_AMOLED, value.toString())
    }

    fun setLightMode(value: Boolean) {
        isLightMode.value = value
        LocalPrefs.set(KEY_LIGHT, value.toString())
    }

    fun setGridScale(value: String) {
        gridScale.value = value
        LocalPrefs.set(KEY_GRID_SCALE, value)
    }
}
