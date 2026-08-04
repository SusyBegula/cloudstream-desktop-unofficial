package com.lagradost.common.storage

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.common.platform.PlatformPaths
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import com.lagradost.common.logging.AppLogger

data class DesktopBookmark(
    // E.g., "${apiName}_${url.hashCode()}"
    val id: String,
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
)

data class WatchHistory(
    // E.g., "${apiName}_${showUrl.hashCode()}"
    val parentId: String,
    val showName: String,
    val showUrl: String,
    val apiName: String,
    val posterUrl: String?,
    // Episode number, or null if it's a movie
    val episode: Int?,
    // Season number, or null if it's a movie
    val season: Int?,
    // The specific URL/ID of the episode, useful for resuming
    val episodeId: String?,
    // watched time in seconds
    val position: Long,
    // total duration in seconds
    val duration: Long,
    val updateTime: Long = System.currentTimeMillis(),
)

data class PreferredSource(
    val provider: String,
    // The show/movie's own page URL — same identity key used for watch history.
    val showUrl: String,
    // Matched against ExtractorLink.source when picking the initial link for an episode.
    val sourceName: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class PluginUpdateRecord(
    val pluginName: String,
    val version: Int,
    val iconUrl: String?,
    val timestamp: Long = System.currentTimeMillis()
)

object DesktopDataStore {
    @PublishedApi internal val mapper: ObjectMapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val appDataDir = PlatformPaths.appDataDir
    private val dataFile = File(PlatformPaths.dataDir, "datastore.json")

    @PublishedApi internal val cache = ConcurrentHashMap<String, String>()

    /** Call this early in app lifecycle to safely trigger <clinit> outside the plugin sandbox */
    fun init() {}

    val historyUpdates = MutableStateFlow(0)
    val pluginUpdatesFlow = MutableStateFlow(0)

    init {
        if (!dataFile.exists()) {
            dataFile.writeText("{}")
        } else {
            try {
                val map: Map<String, String> = mapper.readValue(dataFile)
                cache.putAll(map)
            } catch (e: Exception) {
                AppLogger.e("Failed to load datastore.json", e)
            }
        }
    }

    private fun saveToFile() {
        try {
            mapper.writeValue(dataFile, cache.toMap())
        } catch (e: Exception) {
            AppLogger.e("Failed to save datastore.json", e)
        }
    }

    fun <T> setKey(
        key: String,
        value: T,
    ) {
        try {
            val json = mapper.writeValueAsString(value)
            cache[key] = json
            saveToFile()
        } catch (e: Exception) {
            AppLogger.e("Failed to serialize key $key", e)
        }
    }

    fun <T> getKey(
        key: String,
        clazz: Class<T>,
    ): T? {
        val json = cache[key] ?: return null
        return try {
            mapper.readValue(json, clazz)
        } catch (e: Exception) {
            null
        }
    }

    inline fun <reified T> getKey(key: String): T? {
        val json = cache[key] ?: return null
        return try {
            mapper.readValue(json)
        } catch (e: Exception) {
            null
        }
    }

    fun removeKey(key: String) {
        if (cache.remove(key) != null) {
            saveToFile()
        }
    }



    private const val BOOKMARKS_KEY = "user_bookmarks"

    fun getBookmarks(): List<DesktopBookmark> {
        val json = cache[BOOKMARKS_KEY] ?: return emptyList()
        return try {
            mapper.readValue(json, object : TypeReference<List<DesktopBookmark>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addBookmark(bookmark: DesktopBookmark) {
        val current = getBookmarks().toMutableList()
        // Remove if already exists to update it
        current.removeAll { it.id == bookmark.id }
        current.add(bookmark)
        setKey(BOOKMARKS_KEY, current)
    }

    fun removeBookmark(id: String) {
        val current = getBookmarks().toMutableList()
        current.removeAll { it.id == id }
        setKey(BOOKMARKS_KEY, current)
    }

    fun isBookmarked(id: String): Boolean {
        return getBookmarks().any { it.id == id }
    }



    private const val WATCH_HISTORY_KEY = "user_watch_history"

    fun getAllWatchHistory(): List<WatchHistory> {
        val json = cache[WATCH_HISTORY_KEY] ?: return emptyList()
        return try {
            mapper.readValue(json, object : TypeReference<List<WatchHistory>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAllWatchHistory() {
        removeKey(WATCH_HISTORY_KEY)
        historyUpdates.value++
    }

    fun removeWatchHistory(parentId: String) {
        val current = getAllWatchHistory().toMutableList()
        val removed = current.removeIf { it.parentId == parentId }
        if (removed) {
            setKey(WATCH_HISTORY_KEY, current)
            historyUpdates.value++
        }
    }

    /**
     * Unique resume key per show/episode (Android stores position per episode id).
     */
    fun watchHistoryId(
        apiName: String,
        showUrl: String,
        season: Int? = null,
        episode: Int? = null,
        episodeData: String? = null,
    ): String {
        val base = "${apiName}_${showUrl.hashCode()}"
        return if (season != null || episode != null || !episodeData.isNullOrBlank()) {
            "${base}_s${season ?: 0}_e${episode ?: 0}_${episodeData?.hashCode() ?: 0}"
        } else {
            base
        }
    }

    fun setLastWatched(history: WatchHistory) {
        // Store the real progress; resume logic handles completion thresholds.
        val normalizedDuration = history.duration.coerceAtLeast(0)
        val normalizedPosition = if (normalizedDuration > 0) {
            history.position.coerceIn(0, normalizedDuration)
        } else {
            history.position.coerceAtLeast(0)
        }

        val newHistory = history.copy(
            position = normalizedPosition,
            duration = normalizedDuration,
            updateTime = System.currentTimeMillis(),
        )

        val current = getAllWatchHistory().toMutableList()
        current.removeAll { it.parentId == newHistory.parentId && it.episodeId == newHistory.episodeId }
        current.add(newHistory)
        setKey(WATCH_HISTORY_KEY, current)
        historyUpdates.value++
    }

    fun getLastWatched(parentId: String): WatchHistory? {
        return getAllWatchHistory()
            .filter { it.parentId == parentId }
            .maxByOrNull { it.updateTime }
    }

    fun getLatestWatchHistoryForShow(showUrl: String): WatchHistory? {
        return getAllWatchHistory()
            .filter { it.showUrl == showUrl }
            .maxByOrNull { it.updateTime }
    }

    fun getEpisodeWatched(
        parentId: String,
        episodeId: String?,
    ): WatchHistory? {
        return getAllWatchHistory().find { it.parentId == parentId && it.episodeId == episodeId }
    }



    private const val PREFERRED_SOURCES_KEY = "user_preferred_sources"

    fun getAllPreferredSources(): List<PreferredSource> {
        val json = cache[PREFERRED_SOURCES_KEY] ?: return emptyList()
        return try {
            mapper.readValue(json, object : TypeReference<List<PreferredSource>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getPreferredSource(
        provider: String,
        showUrl: String,
    ): PreferredSource? {
        return getAllPreferredSources().find { it.provider == provider && it.showUrl == showUrl }
    }

    fun setPreferredSource(pref: PreferredSource) {
        val current = getAllPreferredSources().toMutableList()
        current.removeAll { it.provider == pref.provider && it.showUrl == pref.showUrl }
        current.add(pref.copy(updatedAt = System.currentTimeMillis()))
        setKey(PREFERRED_SOURCES_KEY, current)
    }

    fun removePreferredSource(
        provider: String,
        showUrl: String,
    ) {
        val current = getAllPreferredSources().toMutableList()
        if (current.removeAll { it.provider == provider && it.showUrl == showUrl }) {
            setKey(PREFERRED_SOURCES_KEY, current)
        }
    }



    private const val UPDATES_HISTORY_KEY = "plugin_updates_history_v2"
    private const val UNREAD_UPDATES_KEY = "unread_plugin_updates"

    fun getUpdatesHistory(): List<PluginUpdateRecord> {
        val json = cache[UPDATES_HISTORY_KEY] ?: return emptyList()
        return try {
            mapper.readValue(json, object : TypeReference<List<PluginUpdateRecord>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addUpdateHistory(history: List<PluginUpdateRecord>) {
        if (history.isEmpty()) return
        
        val consolidatedHistory = history.sortedByDescending { it.timestamp }
            .distinctBy { it.pluginName }
            
        val current = getUpdatesHistory().toMutableList()
        val incomingNames = consolidatedHistory.map { it.pluginName }.toSet()
        current.removeAll { it.pluginName in incomingNames }
        
        current.addAll(0, consolidatedHistory) // add to top
        current.sortByDescending { it.timestamp }
        if (current.size > 50) {
            current.subList(50, current.size).clear()
        }
        setKey(UPDATES_HISTORY_KEY, current)
        pluginUpdatesFlow.value++
    }

    fun hasUnreadUpdates(): Boolean {
        return getKey<Boolean>(UNREAD_UPDATES_KEY) ?: false
    }

    fun setUnreadUpdates(hasUnread: Boolean) {
        setKey(UNREAD_UPDATES_KEY, hasUnread)
        pluginUpdatesFlow.value++
    }
}
