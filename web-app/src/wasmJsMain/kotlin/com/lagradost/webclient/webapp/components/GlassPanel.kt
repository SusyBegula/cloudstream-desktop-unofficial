package com.lagradost.webclient.webapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Web-only stand-in for desktop-app's haze-based frosted-glass panel (DetailsHeader.kt's
 * `.hazeChild(...)`). True blur is a confirmed no-op on this Compose-for-Web target (see plan's
 * spike findings), so this is a plain semi-transparent tinted surface instead — same call-site
 * shape, no real blur.
 */
fun Modifier.glassPanel(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = Color(0xFF0C0C14).copy(alpha = 0.72f),
): Modifier = this.clip(shape).background(tint)
