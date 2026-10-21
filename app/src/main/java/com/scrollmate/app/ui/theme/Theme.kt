package com.scrollmate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.scrollmate.core.model.ThemeMode
import com.scrollmate.core.progress.LimitZone

// Light: real cream and soft-grey surfaces with dark, readable text – no dark areas.
private val Light = lightColorScheme(
    primary = Color(0xFF3F6B52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E8DA),
    onPrimaryContainer = Color(0xFF0E2618),
    secondary = Color(0xFF5B6470),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E7EB),
    onSecondaryContainer = Color(0xFF1F2937),
    tertiary = Color(0xFF8A5A12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE9C8),
    onTertiaryContainer = Color(0xFF2E1E03),
    background = Color(0xFFFAF7F0),
    onBackground = Color(0xFF1F2937),
    surface = Color(0xFFFAF7F0),
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFE7E5E0),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceTint = Color(0xFF3F6B52),
    outline = Color(0xFF8B8F96),
    outlineVariant = Color(0xFFD6D3CC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F6),
    surfaceContainer = Color(0xFFEEEFF1),
    surfaceContainerHigh = Color(0xFFE8E9EC),
    surfaceContainerHighest = Color(0xFFE2E4E8),
    surfaceBright = Color(0xFFFAF7F0),
    surfaceDim = Color(0xFFE2DED5),
)

// Dark: deep slate/anthracite (#0f172a) with dark cards and high contrast.
private val Dark = darkColorScheme(
    primary = Color(0xFF9DD4AE),
    onPrimary = Color(0xFF0B2A17),
    primaryContainer = Color(0xFF1F4430),
    onPrimaryContainer = Color(0xFFD4E8DA),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF4A3A12),
    onTertiaryContainer = Color(0xFFFDE8B0),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceTint = Color(0xFF9DD4AE),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF1E293B),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF273449),
    surfaceContainerHighest = Color(0xFF334155),
    surfaceBright = Color(0xFF334155),
    surfaceDim = Color(0xFF0F172A),
)

/** Status glow colours for the remaining time limit. Used for borders, rings and glows, never for body text. */
object ZoneColors {
    val Safe = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Exceeded = Color(0xFFEF4444)

    fun of(zone: LimitZone): Color = when (zone) {
        LimitZone.SAFE -> Safe
        LimitZone.WARNING -> Warning
        LimitZone.EXCEEDED -> Exceeded
    }
}

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ScrollMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
