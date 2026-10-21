package com.scrollmate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Calm palette: cream, muted green, anthracite. Over-limit states use a warm sand tone (tertiary),
// never alarm red. Text/background pairs were chosen for WCAG AA (>= 4.5:1).

private val Light = lightColorScheme(
    primary = Color(0xFF4E6E58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E5D8),
    onPrimaryContainer = Color(0xFF10261A),
    secondary = Color(0xFF5E6A60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E6DD),
    onSecondaryContainer = Color(0xFF1B231D),
    tertiary = Color(0xFF7D5F33),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E2C8),
    onTertiaryContainer = Color(0xFF2C1E05),
    background = Color(0xFFF7F3EA),
    onBackground = Color(0xFF2F3437),
    surface = Color(0xFFF7F3EA),
    onSurface = Color(0xFF2F3437),
    surfaceVariant = Color(0xFFE7E2D6),
    onSurfaceVariant = Color(0xFF4A4F4C),
    surfaceTint = Color(0xFF4E6E58),
    outline = Color(0xFF77736A),
    outlineVariant = Color(0xFFCFC9BC),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF3EEE3),
    surfaceContainer = Color(0xFFEDE8DC),
    surfaceContainerHigh = Color(0xFFE8E2D5),
    surfaceContainerHighest = Color(0xFFE2DCCF),
    surfaceBright = Color(0xFFF7F3EA),
    surfaceDim = Color(0xFFDDD8CC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA8C9AF),
    onPrimary = Color(0xFF13301F),
    primaryContainer = Color(0xFF364F3E),
    onPrimaryContainer = Color(0xFFD6E5D8),
    secondary = Color(0xFFC2CABF),
    onSecondary = Color(0xFF2C332D),
    secondaryContainer = Color(0xFF3F4840),
    onSecondaryContainer = Color(0xFFDEE5DA),
    tertiary = Color(0xFFE1C38F),
    onTertiary = Color(0xFF402D0B),
    tertiaryContainer = Color(0xFF5A4421),
    onTertiaryContainer = Color(0xFFF6E3C2),
    background = Color(0xFF1B1E1F),
    onBackground = Color(0xFFE6E3DA),
    surface = Color(0xFF1B1E1F),
    onSurface = Color(0xFFE6E3DA),
    surfaceVariant = Color(0xFF42463F),
    onSurfaceVariant = Color(0xFFC6C8BF),
    surfaceTint = Color(0xFFA8C9AF),
    outline = Color(0xFF90928A),
    outlineVariant = Color(0xFF42463F),
    surfaceContainerLowest = Color(0xFF151819),
    surfaceContainerLow = Color(0xFF1F2223),
    surfaceContainer = Color(0xFF232627),
    surfaceContainerHigh = Color(0xFF2D3031),
    surfaceContainerHighest = Color(0xFF383B3C),
    surfaceBright = Color(0xFF3A3D3E),
    surfaceDim = Color(0xFF1B1E1F),
)

@Composable
fun ScrollMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
