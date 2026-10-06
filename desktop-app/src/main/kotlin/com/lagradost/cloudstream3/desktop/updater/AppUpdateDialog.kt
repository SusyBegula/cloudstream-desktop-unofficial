package com.lagradost.cloudstream3.desktop.updater

import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

@Composable
fun AppUpdateNotificationBanner(
    modifier: Modifier = Modifier,
) {
    val showBanner by DesktopAppUpdater.showBanner.collectAsState()
    val uiState by DesktopAppUpdater.uiState.collectAsState()

    AnimatedVisibility(
        visible = showBanner && uiState is UpdateUiState.UpdateAvailable,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val update = uiState as? UpdateUiState.UpdateAvailable ?: return@AnimatedVisibility

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = DesktopUi.SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.5f)),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = DesktopUi.Accent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Update Available: ${update.release.name ?: update.release.tagName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DesktopUi.TextPrimary,
                    )
                    Text(
                        "A new version of CloudStream Desktop is ready to install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopUi.TextMuted,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { DesktopAppUpdater.openDialog() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopUi.Accent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("Update Now", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { DesktopAppUpdater.dismissBanner() },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = DesktopUi.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog() {
    val showDialog by DesktopAppUpdater.showDialog.collectAsState()
    val uiState by DesktopAppUpdater.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    if (!showDialog) return

    AlertDialog(
        onDismissRequest = { DesktopAppUpdater.dismissDialog() },
        modifier = Modifier.widthIn(max = 520.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = DesktopUi.Accent,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (uiState) {
                        is UpdateUiState.Checking -> "Checking for Updates"
                        is UpdateUiState.UpToDate -> "Up to Date"
                        is UpdateUiState.UpdateAvailable -> "New Update Available"
                        is UpdateUiState.Downloading -> "Downloading Update"
                        is UpdateUiState.ReadyToInstall -> "Update Ready to Install"
                        is UpdateUiState.Error -> "Update Check Failed"
                        else -> "Software Update"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when (val state = uiState) {
                    is UpdateUiState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 16.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = DesktopUi.Accent,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Checking GitHub Releases for new updates...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DesktopUi.TextMuted,
                            )
                        }
                    }
                    is UpdateUiState.UpToDate -> {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "You are running the latest version!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesktopUi.TextPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "CloudStream Desktop v${state.currentVersion} is up to date.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DesktopUi.TextMuted,
                            )
                        }
                    }
                    is UpdateUiState.UpdateAvailable -> {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DesktopUi.AccentSoft.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            state.release.name ?: state.release.tagName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = DesktopUi.TextPrimary,
                                        )
                                        Text(
                                            "Version ${state.release.tagName} · Current: v${DesktopAppUpdater.CURRENT_VERSION}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DesktopUi.Accent,
                                        )
                                    }
                                }
                            }

                            if (!state.release.body.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Release Notes:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesktopUi.TextMuted,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DesktopUi.SurfaceElevated,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp),
                                ) {
                                    Text(
                                        text = state.release.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DesktopUi.TextMuted,
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .verticalScroll(rememberScrollState()),
                                    )
                                }
                            }

                            if (state.asset != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val mb = state.asset.size / (1024 * 1024)
                                Text(
                                    "Package: ${state.asset.name} (${if (mb > 0) "$mb MB" else "Ready"})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DesktopUi.TextMuted,
                                )
                            }
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Downloading ${state.asset.name}...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopUi.TextPrimary,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = DesktopUi.Accent,
                                trackColor = DesktopUi.SurfaceElevated,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                val dlMb = state.downloadedBytes / (1024 * 1024)
                                val totMb = state.totalBytes / (1024 * 1024)
                                Text(
                                    text = if (totMb > 0) "$dlMb MB / $totMb MB" else "$dlMb MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DesktopUi.TextMuted,
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DesktopUi.Accent,
                                )
                            }
                        }
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Download Complete!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DesktopUi.TextPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Click below to install and restart CloudStream Desktop.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DesktopUi.TextMuted,
                            )
                        }
                    }
                    is UpdateUiState.Error -> {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Could Not Check For Updates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopUi.TextMuted,
                            )
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (val state = uiState) {
                is UpdateUiState.UpdateAvailable -> {
                    if (state.asset != null) {
                        Button(
                            onClick = {
                                DesktopAppUpdater.startDownload(state.release, state.asset, coroutineScope)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesktopUi.Accent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download & Install")
                        }
                    } else {
                        Button(
                            onClick = {
                                openBrowserUrl(state.release.htmlUrl ?: "https://github.com/SusyBegula/cloudstream-desktop-unofficial/releases")
                                DesktopAppUpdater.dismissDialog()
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Open in Browser")
                        }
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                DesktopAppUpdater.applyUpdate(state.installerFile, state.isAppImage)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restart & Install")
                    }
                }
                is UpdateUiState.UpToDate -> {
                    Button(
                        onClick = { DesktopAppUpdater.dismissDialog() },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("OK")
                    }
                }
                is UpdateUiState.Error -> {
                    Button(
                        onClick = {
                            openBrowserUrl("https://github.com/SusyBegula/cloudstream-desktop-unofficial/releases")
                            DesktopAppUpdater.dismissDialog()
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("View Releases")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            when (val state = uiState) {
                is UpdateUiState.UpdateAvailable -> {
                    Row {
                        TextButton(
                            onClick = {
                                DesktopAppUpdater.ignoreCurrentUpdate(state.release.tagName)
                            },
                        ) {
                            Text("Skip This Version", color = DesktopUi.TextMuted)
                        }
                        TextButton(
                            onClick = { DesktopAppUpdater.dismissDialog() },
                        ) {
                            Text("Later")
                        }
                    }
                }
                is UpdateUiState.Downloading -> {
                    // Cannot dismiss easily while downloading or let them close modal
                    TextButton(onClick = { DesktopAppUpdater.dismissDialog() }) {
                        Text("Hide")
                    }
                }
                is UpdateUiState.Error -> {
                    TextButton(onClick = { DesktopAppUpdater.dismissDialog() }) {
                        Text("Close")
                    }
                }
                else -> {}
            }
        },
    )
}

private fun openBrowserUrl(url: String) {
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }
}
