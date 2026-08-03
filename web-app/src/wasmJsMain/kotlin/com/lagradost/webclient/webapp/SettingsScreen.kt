package com.lagradost.webclient.webapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.client.ApiClient
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.webapp.components.SectionHeader
import com.lagradost.webclient.webapp.theme.AppearanceConfig
import com.lagradost.webclient.webapp.theme.accentColorFromName

private val ACCENTS = listOf("Purple", "Blue", "Green", "Red", "Orange")
private val TABS = listOf("Appearance", "About")

/**
 * Web-client port of desktop-app's Settings screen. Appearance is fully real (localStorage-backed,
 * same as desktop's AppearanceConfig). Player tab is dropped entirely (no player-choice concept on
 * web — always <video>+hls.js). Network/Advanced (DoH, cloned sites, global-search) are deferred.
 */
@Composable
fun SettingsScreen(api: ApiClient, nav: NavController) {
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        SectionHeader("Settings")
        TabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> AppearanceTab()
            else -> AboutTab()
        }
    }
}

@Composable
private fun AppearanceTab() {
    val accent by AppearanceConfig.themeAccent.collectAsState()
    val amoled by AppearanceConfig.amoledMode.collectAsState()
    val lightMode by AppearanceConfig.isLightMode.collectAsState()
    val gridScale by AppearanceConfig.gridScale.collectAsState()

    Column(Modifier.padding(16.dp)) {
        Text("Accent Color", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(vertical = 8.dp)) {
            ACCENTS.forEach { name ->
                val color = accentColorFromName(name)
                Row(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { AppearanceConfig.setThemeAccent(name) },
                ) {
                    if (accent == name) {
                        Text("✓", modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }

        SettingSwitchRow("Light Theme", lightMode) { AppearanceConfig.setLightMode(it) }
        SettingSwitchRow("AMOLED Mode", amoled) { AppearanceConfig.setAmoledMode(it) }

        Text("Grid Size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.padding(vertical = 8.dp)) {
            listOf("Compact", "Normal", "Large").forEach { size ->
                Text(
                    size,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clickable { AppearanceConfig.setGridScale(size) },
                    style = if (gridScale == size) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutTab() {
    Column(Modifier.padding(16.dp)) {
        Text("CloudStream Web (Unofficial)", style = MaterialTheme.typography.titleLarge)
        Text(
            "An unofficial browser client for the CloudStream desktop server. Pre-alpha. " +
                "No media or plugins are shipped with this project — installed plugins scrape " +
                "third-party sites at your own risk.",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
