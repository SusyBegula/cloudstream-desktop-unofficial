package com.lagradost.cloudstream3

import android.content.Context
import android.content.DesktopContextProvider
import java.lang.ref.WeakReference

/** Minimal stub of CloudStreamApp used by CloudStream plugins. Provides access to a global context
 * and helpers for storing simple keys in SharedPreferences. */
class CloudStreamApp {
    companion object {
        private var _context: WeakReference<Context>? = null

        @JvmStatic
        var context: Context?
            get() = _context?.get() ?: DesktopContextProvider.context
            set(value) {
                _context = if (value == null) null else WeakReference(value)
            }

        /** Provide activity retrieval similar to Android implementation */
        @JvmStatic
        tailrec fun Context?.getActivity(): android.app.Activity? {
            val ctx = this ?: return null
            return when (ctx) {
                is android.app.Activity -> ctx
                is android.content.ContextWrapper -> ctx.baseContext.getActivity()
                else -> null
            }
        }

        // Simple key/value helpers using DesktopDataStore
        @JvmStatic
        fun <T> setKey(folder: String, path: String, value: T) {
            if (value == null) {
                com.lagradost.common.storage.DesktopDataStore.removeKey(path)
            } else {
                com.lagradost.common.storage.DesktopDataStore.setKey(path, value)
            }
        }

        @JvmStatic
        fun <T> setKey(path: String, value: T) {
            if (value == null) {
                com.lagradost.common.storage.DesktopDataStore.removeKey(path)
            } else {
                com.lagradost.common.storage.DesktopDataStore.setKey(path, value)
            }
        }

        @JvmStatic
        fun removeKey(folder: String, path: String) {
            com.lagradost.common.storage.DesktopDataStore.removeKey(path)
        }

        @JvmStatic
        inline fun <reified T> getKey(path: String, defVal: T?): T? {
            val type = when (defVal) {
                is Boolean -> "Boolean"
                is Int -> "Int"
                is Long -> "Long"
                is Float -> "Float"
                is String -> "String"
                else -> "String"
            }
            val prefName = if (path.contains("_")) path.substringBefore("_") + "_" else "global_"
            val keyWithoutPrefix = if (path.contains("_")) path.substringAfter("_") else path

            com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(prefName, keyWithoutPrefix, type, defVal, false)

            return com.lagradost.common.storage.DesktopDataStore.getKey<T>(path) ?: defVal
        }

        @JvmStatic
        fun getKey(path: String): Any? {
            return com.lagradost.common.storage.DesktopDataStore.getKey<Any>(path)
        }
    }
}
