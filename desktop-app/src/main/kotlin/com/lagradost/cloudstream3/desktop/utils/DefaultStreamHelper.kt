package com.lagradost.cloudstream3.desktop.utils

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

object DefaultStreamHelper {
    private const val DELIMITER = "::"

    fun buildKey(providerName: String, showUrl: String): String =
        "default_stream_${providerName}_$showUrl"

    fun buildIdentifier(link: ExtractorLink): String =
        "${link.name}$DELIMITER${link.quality}"

    fun matches(link: ExtractorLink, savedPref: String?): Boolean {
        if (savedPref.isNullOrBlank()) return false
        val delimiterIndex = savedPref.lastIndexOf(DELIMITER)
        return if (delimiterIndex != -1) {
            val savedName = savedPref.substring(0, delimiterIndex)
            val savedQuality = savedPref.substring(delimiterIndex + DELIMITER.length).toIntOrNull()
            link.name.equals(savedName, ignoreCase = true) && (savedQuality == null || link.quality == savedQuality)
        } else {
            // Legacy format where only name was stored
            link.name.equals(savedPref, ignoreCase = true)
        }
    }

    fun formatDisplayName(savedPref: String?): String {
        if (savedPref.isNullOrBlank()) return ""
        val delimiterIndex = savedPref.lastIndexOf(DELIMITER)
        if (delimiterIndex != -1) {
            val name = savedPref.substring(0, delimiterIndex)
            val quality = savedPref.substring(delimiterIndex + DELIMITER.length).toIntOrNull()
            return if (quality != null && quality > 0 && quality != Qualities.Unknown.value) {
                "$name (${quality}p)"
            } else {
                name
            }
        }
        return savedPref
    }
}
