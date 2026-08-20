package com.lagradost.cloudstream3.desktop.player

import com.lagradost.common.storage.DesktopDataStore
import com.sun.jna.Pointer

object PlayerConfig {
    const val PREF_HWDEC = "player_hwdec"
    const val PREF_SUB_SIZE = "player_sub_size"
    const val PREF_SUB_COLOR = "player_sub_color"
    const val PREF_SUB_BG = "player_sub_bg"
    const val PREF_YTDL_FORMAT = "player_ytdl_format"
    const val PREF_AUTO_PLAY = "player_auto_play"
    const val PREF_AUTO_PLAY_TIMEOUT = "player_auto_play_timeout"
    const val PREF_AUTO_PLAY_NEXT_EPISODE = "player_auto_play_next_episode"
    const val PREF_AUTO_PLAY_NEXT_EPISODE_SECONDS = "player_auto_play_next_episode_seconds"
    const val PREF_SPEED = "player_speed"

    fun applyMpvSettings(handle: Pointer, lib: MpvLibrary) {
        // Hardware Acceleration (Default: auto-safe)
        val hwdec = DesktopDataStore.getKey<String>(PREF_HWDEC) ?: "auto-safe"
        lib.mpv_set_option_string(handle, "hwdec", hwdec)

        // Subtitles Size (Default: 45)
        val subSize = DesktopDataStore.getKey<String>(PREF_SUB_SIZE) ?: "45"
        lib.mpv_set_option_string(handle, "sub-font-size", subSize)

        // Subtitle Color (Default: #FFFFFF)
        val subColor = DesktopDataStore.getKey<String>(PREF_SUB_COLOR) ?: "#FFFFFF"
        lib.mpv_set_option_string(handle, "sub-color", subColor)

        // Subtitle Background (Default: None/Transparent -> #00000000)
        val subBg = DesktopDataStore.getKey<String>(PREF_SUB_BG) ?: "#00000000"
        lib.mpv_set_option_string(handle, "sub-bg-color", subBg)

        // YTDL Format / Quality Selection
        val ytdlFormat = DesktopDataStore.getKey<String>(PREF_YTDL_FORMAT) ?: "bestvideo[height<=?1080]+bestaudio/best"
        lib.mpv_set_option_string(handle, "ytdl-format", ytdlFormat)

        // Playback Speed (Default: 1.0), persisted from the last session
        val speed = DesktopDataStore.getKey<String>(PREF_SPEED) ?: "1.0"
        lib.mpv_set_option_string(handle, "speed", speed)

        // Verbose Logging for Dev Console
        lib.mpv_set_option_string(handle, "msg-level", "all=v")
        lib.mpv_set_option_string(handle, "terminal", "yes")

        // Fast Startup Optimizations
        lib.mpv_set_option_string(handle, "cache", "yes")
        lib.mpv_set_option_string(handle, "demuxer-max-bytes", "150M") // Generous buffer
        lib.mpv_set_option_string(handle, "demuxer-max-back-bytes", "50M")
        lib.mpv_set_option_string(handle, "cache-pause", "no") // Start playing IMMEDIATELY without waiting to fill the buffer
    }
}
