package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.controller.GamepadButton
import com.lagradost.cloudstream3.desktop.controller.GamepadManager
import com.lagradost.cloudstream3.desktop.controller.PlayerControllerConfig
import com.lagradost.cloudstream3.desktop.controller.UiControllerConfig
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi

@Composable
fun SettingsController() {
    val controllers by GamepadManager.controllers.collectAsState()
    val uiConfig by GamepadManager.uiConfig.collectAsState()
    val playerConfig by GamepadManager.playerConfig.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // --- 1. Controller Hardware Status & Interactive Tester ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DesktopUi.SurfaceCard),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = if (controllers.isNotEmpty()) DesktopUi.Accent else DesktopUi.TextMuted,
                            modifier = Modifier.size(28.dp),
                        )
                        Column {
                            Text(
                                "Connected Controllers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DesktopUi.TextPrimary,
                            )
                            Text(
                                if (controllers.isNotEmpty()) {
                                    "${controllers.size} device(s) active · Live input testing enabled"
                                } else {
                                    "No controller detected — Plug in an Xbox or Bluetooth gamepad"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopUi.TextMuted,
                            )
                        }
                    }

                    if (controllers.isNotEmpty()) {
                        Button(
                            onClick = { GamepadManager.vibrate(durationMs = 250) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test Rumble")
                        }
                    }
                }

                if (controllers.isNotEmpty()) {
                    HorizontalDivider(color = DesktopUi.Divider)

                    controllers.forEach { ctrl ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                ctrl.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopUi.Accent,
                            )

                            // Visual Controller Button / Stick Status Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DesktopUi.SurfaceElevated)
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Face Buttons
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ControllerButtonIndicator("A", ctrl.buttonStates[GamepadButton.A] == true, Color(0xFF22C55E))
                                    ControllerButtonIndicator("B", ctrl.buttonStates[GamepadButton.B] == true, Color(0xFFEF4444))
                                    ControllerButtonIndicator("X", ctrl.buttonStates[GamepadButton.X] == true, Color(0xFF3B82F6))
                                    ControllerButtonIndicator("Y", ctrl.buttonStates[GamepadButton.Y] == true, Color(0xFFEAB308))
                                }

                                // D-Pad
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ControllerButtonIndicator("↑", ctrl.buttonStates[GamepadButton.DPAD_UP] == true)
                                    ControllerButtonIndicator("↓", ctrl.buttonStates[GamepadButton.DPAD_DOWN] == true)
                                    ControllerButtonIndicator("←", ctrl.buttonStates[GamepadButton.DPAD_LEFT] == true)
                                    ControllerButtonIndicator("→", ctrl.buttonStates[GamepadButton.DPAD_RIGHT] == true)
                                }

                                // Bumpers & Triggers
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ControllerButtonIndicator("LB", ctrl.buttonStates[GamepadButton.LB] == true)
                                    ControllerButtonIndicator("RB", ctrl.buttonStates[GamepadButton.RB] == true)
                                    ControllerButtonIndicator("LT ${(ctrl.leftTrigger * 100).toInt()}%", ctrl.leftTrigger > 0.1f)
                                    ControllerButtonIndicator("RT ${(ctrl.rightTrigger * 100).toInt()}%", ctrl.rightTrigger > 0.1f)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. CloudStream UI Controller Navigation Settings ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DesktopUi.SurfaceCard),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = DesktopUi.Accent, modifier = Modifier.size(24.dp))
                    Column {
                        Text(
                            "CloudStream UI Navigation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DesktopUi.TextPrimary,
                        )
                        Text(
                            "Configure controller behavior for browsing home, search, details, and library",
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopUi.TextMuted,
                        )
                    }
                }

                HorizontalDivider(color = DesktopUi.Divider)

                // Enable UI Controller
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Enable UI Gamepad Navigation", fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary)
                        Text("Use D-pad and Left Stick for TV-style spatial navigation", style = MaterialTheme.typography.bodySmall, color = DesktopUi.TextMuted)
                    }
                    Switch(
                        checked = uiConfig.enabled,
                        onCheckedChange = { GamepadManager.updateUiConfig(uiConfig.copy(enabled = it)) },
                    )
                }

                // TV Mode Focus Glow & Zoom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("TV Mode Focus Glow & Scale", fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary)
                        Text("Slightly zoom (1.05x) and highlight posters when focused via controller", style = MaterialTheme.typography.bodySmall, color = DesktopUi.TextMuted)
                    }
                    Switch(
                        checked = uiConfig.tvFocusEffect,
                        onCheckedChange = { GamepadManager.updateUiConfig(uiConfig.copy(tvFocusEffect = it)) },
                    )
                }

                // Stick Deadzone Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Analog Stick Deadzone", fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary)
                        Text("${(uiConfig.deadzone * 100).toInt()}%", fontWeight = FontWeight.Bold, color = DesktopUi.Accent)
                    }
                    Slider(
                        value = uiConfig.deadzone,
                        onValueChange = { GamepadManager.updateUiConfig(uiConfig.copy(deadzone = it)) },
                        valueRange = 0.10f..0.35f,
                        steps = 5,
                    )
                }

                // UI Button Cheat Sheet
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DesktopUi.SurfaceElevated,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("UI Controller Shortcuts:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DesktopUi.TextPrimary)
                        Text("• D-Pad / Left Stick: Move focus across posters, buttons, and content inside the current tab", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• Right Stick (Up/Down): Smooth analog scrolling up and down", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• A Button: Open show details / Click focused item", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• B Button: Go back to previous screen or close dialog", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• LB / RB Bumpers: Switch tabs (Home ↔ Favorites ↔ Downloads ↔ Extensions ↔ Settings)", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• Y Button: Focus top-bar search bar immediately", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• LT / RT Triggers: Fast page up / page down scroll", fontSize = 12.sp, color = DesktopUi.TextMuted)
                    }
                }
            }
        }

        // --- 3. MPV Video Player Controller Settings ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DesktopUi.SurfaceCard),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = DesktopUi.Accent, modifier = Modifier.size(24.dp))
                    Column {
                        Text(
                            "MPV Video Player Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DesktopUi.TextPrimary,
                        )
                        Text(
                            "Configure playback buttons, seek steps, volume, and vibration feedback",
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopUi.TextMuted,
                        )
                    }
                }

                HorizontalDivider(color = DesktopUi.Divider)

                // Enable Player Controller
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Enable Player Gamepad Controls", fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary)
                        Text("Allow controlling video playback using the controller", style = MaterialTheme.typography.bodySmall, color = DesktopUi.TextMuted)
                    }
                    Switch(
                        checked = playerConfig.enabled,
                        onCheckedChange = { GamepadManager.updatePlayerConfig(playerConfig.copy(enabled = it)) },
                    )
                }

                // Short Seek Step
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Short Seek Duration (D-Pad Left/Right)", fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary)
                        Text("Jump forward or backward when pressing D-pad Left / Right", style = MaterialTheme.typography.bodySmall, color = DesktopUi.TextMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 30).forEach { sec ->
                            FilterChip(
                                selected = playerConfig.shortSeekSeconds == sec,
                                onClick = { GamepadManager.updatePlayerConfig(playerConfig.copy(shortSeekSeconds = sec)) },
                                label = { Text("${sec}s") },
                            )
                        }
                    }
                }

                // Volume Step Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Volume Step Size (D-Pad Up/Down)", fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary)
                        Text("Volume change per D-Pad Up / Down press", style = MaterialTheme.typography.bodySmall, color = DesktopUi.TextMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 5, 10).forEach { pct ->
                            FilterChip(
                                selected = playerConfig.volumeStepPercent == pct,
                                onClick = { GamepadManager.updatePlayerConfig(playerConfig.copy(volumeStepPercent = pct)) },
                                label = { Text("$pct%") },
                            )
                        }
                    }
                }

                // Right Trigger Fast Forward
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Right Trigger (RT) 2.5x Fast Forward", fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary)
                        Text("Hold RT trigger during playback for smooth 2.5x high-speed forward", style = MaterialTheme.typography.bodySmall, color = DesktopUi.TextMuted)
                    }
                    Switch(
                        checked = playerConfig.triggerFastForward,
                        onCheckedChange = { GamepadManager.updatePlayerConfig(playerConfig.copy(triggerFastForward = it)) },
                    )
                }

                // Rumble on Episode Finish
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Vibration on Episode End", fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary)
                        Text("Gentle rumble haptic feedback when reaching the next episode countdown", style = MaterialTheme.typography.bodySmall, color = DesktopUi.TextMuted)
                    }
                    Switch(
                        checked = playerConfig.rumbleOnFinish,
                        onCheckedChange = { GamepadManager.updatePlayerConfig(playerConfig.copy(rumbleOnFinish = it)) },
                    )
                }

                // Player Button Cheat Sheet
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DesktopUi.SurfaceElevated,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Player Controller Shortcuts:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DesktopUi.TextPrimary)
                        Text("• A Button: Play / Pause toggle", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• B Button: Exit video player & save history", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• X Button: Toggle Fullscreen / Windowed", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• Y Button: Cycle playback speed (1.0x → 1.25x → 1.5x → 1.75x → 2.0x → 1.0x)", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• D-Pad Left / Right: Seek backward / forward by ${playerConfig.shortSeekSeconds}s", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• D-Pad Up / Down: Volume up / down by ${playerConfig.volumeStepPercent}%", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• LB / RB Bumpers: Seek to start (0:00) / Seek to end", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• L3 (Left Stick Press): Cycle audio tracks", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• R3 (Right Stick Press): Cycle subtitle tracks", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• Back / View: Cycle aspect ratio (Fit, Zoom/Fill, 16:9, 4:3, 2.35:1)", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• Start / Menu: Toggle MPV controls & OSD", fontSize = 12.sp, color = DesktopUi.TextMuted)
                        Text("• RT Trigger (Hold): Smooth 2.5x fast-forward", fontSize = 12.sp, color = DesktopUi.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControllerButtonIndicator(
    label: String,
    isPressed: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) activeColor else Color.Transparent,
        label = "btnIndicatorBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isPressed) Color.White else DesktopUi.TextMuted,
        label = "btnIndicatorText",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) activeColor else DesktopUi.Divider,
        label = "btnIndicatorBorder",
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isPressed) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
