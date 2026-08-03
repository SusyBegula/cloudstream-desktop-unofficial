package com.lagradost.webclient.webapp.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.webapp.theme.DesktopUi

/** Web-client port of desktop-app's ui/components/WatchProgressIndicator.kt. */
@Composable
fun WatchProgressIndicator(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    if (durationMs <= 0) return
    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(4.dp),
            color = DesktopUi.Accent,
            trackColor = DesktopUi.SurfaceElevated,
        )
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = DesktopUi.TextMuted,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
