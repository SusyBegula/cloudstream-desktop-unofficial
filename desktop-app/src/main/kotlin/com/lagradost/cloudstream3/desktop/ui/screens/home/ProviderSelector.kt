package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI

@Composable
fun ProviderSelector(
    providers: List<MainAPI>,
    selectedProvider: MainAPI?,
    onProviderSelected: (String) -> Unit,
    mergedPluginIcons: Map<String, String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIcon = selectedProvider?.let { mergedPluginIcons[it.name] }
    Box {
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.widthIn(max = 166.dp),
        ) {
            if (selectedIcon != null) {
                AsyncImage(selectedIcon, contentDescription = null, modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.White))
                Spacer(Modifier.width(6.dp))
            }
            Text(selectedProvider?.name ?: "Provider", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Default.ExpandMore, contentDescription = "Select provider", modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(280.dp).heightIn(max = 420.dp)) {
            Text("Select provider", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            if (providers.isEmpty()) {
                Text("Install a provider from Extensions.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            providers.sortedBy { it.name.lowercase() }.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        if (provider == selectedProvider) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        expanded = false
                        onProviderSelected(provider.name)
                    },
                )
            }
        }
    }
}
