package com.lagradost.webclient.webapp.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Web-client port of desktop-app's ui/theme/DesktopTheme.kt — pure Compose, ported verbatim. */
fun accentColorFromName(name: String): Color = when (name) {
    "Blue" -> Color(0xFF3B82F6)
    "Green" -> Color(0xFF10B981)
    "Red" -> Color(0xFFEF4444)
    "Orange" -> Color(0xFFF59E0B)
    else -> Color(0xFF7C6BFF) // Purple
}

data class DesktopThemeColors(
    val Accent: Color,
    val AccentSoft: Color,
    val Background: Color,
    val SurfaceCard: Color,
    val SurfaceElevated: Color,
    val TextPrimary: Color,
    val TextMuted: Color,
    val Divider: Color,
)

fun darkDesktopColors(accent: Color, amoled: Boolean) = DesktopThemeColors(
    Accent = accent,
    AccentSoft = accent.copy(alpha = 0.22f),
    Background = if (amoled) Color.Black else Color(0xFF0C0C16),
    SurfaceCard = if (amoled) Color.Black else Color(0xFF161624),
    SurfaceElevated = if (amoled) Color(0xFF0A0A0A) else Color(0xFF20202E),
    TextPrimary = Color(0xFFF3F4F6),
    TextMuted = Color(0xFF9CA3AF),
    Divider = Color(0xFF2A2A38),
)

fun lightDesktopColors(accent: Color) = DesktopThemeColors(
    Accent = accent,
    AccentSoft = accent.copy(alpha = 0.15f),
    Background = Color(0xFFF8FAFC),
    SurfaceCard = Color(0xFFFFFFFF),
    SurfaceElevated = Color(0xFFF1F5F9),
    TextPrimary = Color(0xFF0F172A),
    TextMuted = Color(0xFF64748B),
    Divider = Color(0xFFE2E8F0),
)

fun buildDesktopColors(primaryColor: Color, isLightMode: Boolean, amoledMode: Boolean): DesktopThemeColors {
    return if (isLightMode) lightDesktopColors(primaryColor) else darkDesktopColors(primaryColor, amoledMode)
}

fun buildColorScheme(primaryColor: Color, desktopColors: DesktopThemeColors, isLightMode: Boolean): ColorScheme {
    return if (isLightMode) {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            surface = desktopColors.SurfaceCard,
            onSurface = desktopColors.TextPrimary,
            surfaceVariant = desktopColors.SurfaceElevated,
            onSurfaceVariant = desktopColors.TextMuted,
            background = desktopColors.Background,
            onBackground = desktopColors.TextPrimary,
            error = Color(0xFFEF4444),
            onError = Color.White,
        )
    } else {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            surface = desktopColors.SurfaceCard,
            onSurface = desktopColors.TextPrimary,
            surfaceVariant = desktopColors.SurfaceElevated,
            onSurfaceVariant = desktopColors.TextMuted,
            background = desktopColors.Background,
            onBackground = desktopColors.TextPrimary,
            error = Color(0xFFEF4444),
            onError = Color.White,
        )
    }
}

val LocalDesktopTheme = staticCompositionLocalOf<DesktopThemeColors> { error("No DesktopTheme provided") }

object DesktopUi {
    val Accent: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.Accent
    val AccentSoft: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.AccentSoft
    val Background: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.Background
    val SurfaceCard: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.SurfaceCard
    val SurfaceElevated: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.SurfaceElevated
    val TextPrimary: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.TextPrimary
    val TextMuted: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.TextMuted
    val Divider: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.Divider
}
