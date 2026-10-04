package com.lagradost.cloudstream3.desktop.ui.theme

import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow

object AppearanceConfig {
    private const val PREF_THEME_ACCENT = "pref_theme_accent"
    private const val PREF_AMOLED_MODE = "pref_amoled_mode"
    private const val PREF_LIGHT_MODE = "pref_light_mode"
    private const val PREF_GRID_SCALE = "pref_grid_scale"

    val themeAccent = MutableStateFlow("Red")
    val amoledMode = MutableStateFlow(true)
    val isLightMode = MutableStateFlow(false)
    val gridScale = MutableStateFlow("Normal")

    init {
        DesktopDataStore.setKey(PREF_THEME_ACCENT, "Red")
        DesktopDataStore.setKey(PREF_AMOLED_MODE, true)
        DesktopDataStore.setKey(PREF_LIGHT_MODE, false)
        DesktopDataStore.setKey(PREF_GRID_SCALE, "Normal")
    }

    fun setThemeAccent(colorName: String) {
        themeAccent.value = "Red"
        DesktopDataStore.setKey(PREF_THEME_ACCENT, "Red")
    }

    fun setAmoledMode(enabled: Boolean) {
        amoledMode.value = true
        DesktopDataStore.setKey(PREF_AMOLED_MODE, true)
    }

    fun setLightMode(enabled: Boolean) {
        isLightMode.value = false
        DesktopDataStore.setKey(PREF_LIGHT_MODE, false)
    }

    fun setGridScale(scale: String) {
        gridScale.value = "Normal"
        DesktopDataStore.setKey(PREF_GRID_SCALE, "Normal")
    }
}
