package com.lagradost.webclient.webapp.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.webapp.theme.DesktopUi

private val LANGUAGE_TO_COUNTRY = mapOf(
    "en" to "gb", "zh" to "cn", "ml" to "in", "ta" to "in", "te" to "in",
    "kn" to "in", "mr" to "in", "gu" to "in", "pa" to "in", "hi" to "in",
    "ja" to "jp", "ko" to "kr", "fr" to "fr", "de" to "de", "es" to "es",
    "it" to "it", "pt" to "pt", "ru" to "ru", "ar" to "sa", "th" to "th",
    "vi" to "vn", "id" to "id", "tr" to "tr",
)

/** Web-client port of desktop-app's ui/components/FlagImage.kt. */
@Composable
fun FlagImage(languageCode: String, modifier: Modifier = Modifier) {
    val countryCode = LANGUAGE_TO_COUNTRY[languageCode.lowercase()]
    if (countryCode != null) {
        com.lagradost.webclient.webapp.components.WebAsyncImage(
            model = "https://flagcdn.com/w40/$countryCode.png",
            contentDescription = languageCode,
            contentScale = ContentScale.Crop,
            modifier = modifier.heightIn(max = 16.dp).widthIn(max = 24.dp).clip(RoundedCornerShape(2.dp)),
        )
    } else {
        Icon(Icons.Default.Public, contentDescription = languageCode, tint = DesktopUi.TextMuted, modifier = modifier.heightIn(max = 16.dp))
    }
}
