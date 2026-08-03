package com.lagradost.webclient.webapp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lagradost.webclient.client.NavController
import com.lagradost.webclient.client.Screen
import com.lagradost.webclient.webapp.theme.DesktopUi

/** Web-client port of desktop-app's DesktopAppShell.kt — icon-rail nav + top bar + Crossfade content. */
@Composable
fun AppShell(nav: NavController, content: @Composable () -> Unit) {
    val current by nav.currentScreen.collectAsState()

    Row(Modifier.fillMaxSize()) {
        NavigationDock(current = current, onNavigate = { nav.navigate(it) })

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            val contentPadding = if (current is Screen.Home) {
                PaddingValues(0.dp)
            } else {
                PaddingValues(top = 66.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)
            }

            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                Crossfade(targetState = current, animationSpec = tween(300)) { _ ->
                    content()
                }
            }

            TopBar(
                showBack = current !is Screen.Home,
                onBack = { nav.back() },
                isHome = current is Screen.Home,
            )
        }
    }
}

@Composable
private fun NavigationDock(current: Screen, onNavigate: (Screen) -> Unit) {
    Surface(
        modifier = Modifier.width(72.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Text("CS", color = DesktopUi.Accent, style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                DockItem(Icons.Default.Home, "Home", current is Screen.Home) { onNavigate(Screen.Home) }
                DockItem(Icons.Default.FavoriteBorder, "Library", current is Screen.Library) { onNavigate(Screen.Library) }
                DockItem(Icons.Default.Extension, "Extensions", current is Screen.Extensions) { onNavigate(Screen.Extensions) }
                DockItem(Icons.Default.Settings, "Settings", current is Screen.Settings) { onNavigate(Screen.Settings) }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val textMuted = DesktopUi.TextMuted
    val textPrimary = DesktopUi.TextPrimary
    val surfaceElevated = DesktopUi.SurfaceElevated
    val primary = MaterialTheme.colorScheme.primary

    val iconTint = when {
        selected -> primary
        hovered -> textPrimary
        else -> textMuted
    }
    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> primary.copy(alpha = 0.20f)
            hovered -> surfaceElevated
            else -> Color.Transparent
        },
        label = "dockItemBg",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TopBar(showBack: Boolean, onBack: () -> Unit, isHome: Boolean) {
    val bg = if (isHome) Color.Transparent else MaterialTheme.colorScheme.surface
    Column(modifier = Modifier.fillMaxWidth().background(bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                val surfaceElevated = DesktopUi.SurfaceElevated
                val textPrimary = DesktopUi.TextPrimary
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(surfaceElevated.copy(alpha = 0.5f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (!isHome) {
            HorizontalDivider(color = DesktopUi.Divider, thickness = 0.5.dp)
        }
    }
}
