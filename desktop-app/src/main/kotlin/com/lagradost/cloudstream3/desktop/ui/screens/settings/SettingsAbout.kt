package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.updater.DesktopAppUpdater
import com.lagradost.cloudstream3.desktop.updater.UpdateUiState

@Composable
fun SettingsAbout() {
    val coroutineScope = rememberCoroutineScope()
    var autoCheckEnabled by remember { mutableStateOf(DesktopAppUpdater.isAutoCheckEnabled) }
    val uiState by DesktopAppUpdater.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Software Updates",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Current Version: v${DesktopAppUpdater.CURRENT_VERSION}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Button(
                        onClick = {
                            DesktopAppUpdater.checkForUpdate(isAutomatic = false, scope = coroutineScope)
                        },
                        enabled = uiState !is UpdateUiState.Checking && uiState !is UpdateUiState.Downloading,
                    ) {
                        if (uiState is UpdateUiState.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check for Updates")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Check for updates on startup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Automatically notify when a new release is available on GitHub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoCheckEnabled,
                        onCheckedChange = { checked ->
                            autoCheckEnabled = checked
                            DesktopAppUpdater.setAutoCheckEnabled(checked)
                        },
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About CloudStream Desktop", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This is an UNOFFICIAL Desktop client. Please use the official CloudStream Android app for the best experience. " +
                        "Follow their socials below for more information.\n\n" +
                        "PRE-ALPHA BUILD: This software is provided 'as is'. We do not guarantee that any features will work correctly, " +
                        "and there is no guarantee of future updates or ongoing maintenance.\n\n" +
                        "This application does not ship with any plugins or media content. By using this software, you agree that you are solely " +
                        "responsible for the third-party plugins you choose to install and the networks you connect to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { openUrl("https://discord.gg/5Hus6fM") }) {
                        Text("Join Discord")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { openUrl("https://recloudstream.github.io/csdocs/") }) {
                        Text("CloudStream Wiki")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedButton(onClick = { openUrl("https://github.com/recloudstream/cloudstream") }) {
                        Text("Official Android Repo")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("TMDB API Attribution", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Legal & DMCA Disclaimer", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "CloudStream Desktop acts strictly as a neutral web-scraping client and video player framework. " +
                        "The developers of this application do NOT host, index, upload, distribute, or control any media files or streams. " +
                        "We hold zero liability for the actions of users or the capabilities of user-installed third-party plugins. " +
                        "All DMCA takedown requests must be directed to the actual third-party websites and servers hosting the copyrighted material. " +
                        "This software is provided 'as is', without warranty of any kind.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun openUrl(url: String) {
    try {
        val uri = java.net.URI(url)
        val desktop = java.awt.Desktop.getDesktop()
        desktop.browse(uri)
    } catch (e: Exception) {
        com.lagradost.common.logging.AppLogger.e("Error opening link $url", e)
    }
}
