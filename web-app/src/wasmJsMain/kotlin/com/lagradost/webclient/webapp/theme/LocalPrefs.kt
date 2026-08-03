@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.lagradost.webclient.webapp.theme

/** Thin localStorage wrapper — cosmetic per-browser prefs only (see plan: theme accent/light-dark/AMOLED/grid-scale). */
object LocalPrefs {
    fun get(key: String): String? = jsGetLocalStorageItem(key)
    fun set(key: String, value: String) = jsSetLocalStorageItem(key, value)
}

@JsFun(
    """
    (key) => {
        try { return window.localStorage.getItem(key); } catch (e) { return null; }
    }
    """,
)
private external fun jsGetLocalStorageItem(key: String): String?

@JsFun(
    """
    (key, value) => {
        try { window.localStorage.setItem(key, value); } catch (e) {}
    }
    """,
)
private external fun jsSetLocalStorageItem(key: String, value: String)
