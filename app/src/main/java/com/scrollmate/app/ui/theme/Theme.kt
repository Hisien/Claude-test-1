package com.scrollmate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scrollmate.core.model.ThemeMode
import com.scrollmate.core.progress.LimitZone

/**
 * "Glass & dark" design: a deep night background with soft coloured light spots, translucent
 * glass cards with a thin bright edge and luminous accents. The light theme is the same idea on
 * a bright, warm ground with milky glass.
 */
@Immutable
data class GlassTokens(
    val ground: Color,
    /** Three light spots: top left, right, bottom. */
    val spots: List<Color>,
    val fill: Color,
    val fillStrong: Color,
    val edge: Color,
    val textMuted: Color,
    val nav: Color,
    val accentGradient: List<Color>,
    val isDark: Boolean,
)

private val DarkGlass = GlassTokens(
    ground = Color(0xFF0B1020),
    spots = listOf(Color(0x6138BDF8), Color(0x66A78BFA), Color(0x4D34D399)),
    fill = Color(0x12FFFFFF),
    fillStrong = Color(0x1FFFFFFF),
    edge = Color(0x21FFFFFF),
    textMuted = Color(0xB8F1F5F9),
    nav = Color(0xB30F172A),
    accentGradient = listOf(Color(0xFF6EE7B7), Color(0xFF34D399)),
    isDark = true,
)

private val LightGlass = GlassTokens(
    ground = Color(0xFFF5F2EC),
    spots = listOf(Color(0x5C7DD3FC), Color(0x55C4B5FD), Color(0x4D6EE7B7)),
    fill = Color(0x9EFFFFFF),
    fillStrong = Color(0xD9FFFFFF),
    edge = Color(0xE6FFFFFF),
    textMuted = Color(0xFF475569),
    nav = Color(0xD9FFFFFF),
    accentGradient = listOf(Color(0xFF34D399), Color(0xFF10B981)),
    isDark = false,
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

private val Dark = darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF052E1B),
    primaryContainer = Color(0xFF134E3A),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFFC4B5FD),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF2E2A5C),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF4A3A12),
    onTertiaryContainer = Color(0xFFFDE8B0),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF141B33),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF263052),
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceTint = Color(0xFF6EE7B7),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    surfaceContainerLowest = Color(0xFF0B1020),
    surfaceContainerLow = Color(0xFF161E3A),
    surfaceContainer = Color(0xFF1A2342),
    surfaceContainerHigh = Color(0xFF1F2A4D),
    surfaceContainerHighest = Color(0xFF263257),
    surfaceBright = Color(0xFF2A355C),
    surfaceDim = Color(0xFF0B1020),
)

private val Light = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF022C22),
    secondary = Color(0xFF5B21B6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Color(0xFF92400E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFF5F2EC),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFE7E3DB),
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = Color(0xFF047857),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF6F4EF),
    surfaceContainerHigh = Color(0xFFF0EDE6),
    surfaceContainerHighest = Color(0xFFEAE6DE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE7E3DB),
)

private val Family = FontFamily.SansSerif

private val AppTypography = Typography().let { t ->
    t.copy(
        displayMedium = t.displayMedium.copy(fontFamily = Family, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04).em),
        displaySmall = t.displaySmall.copy(fontFamily = Family, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
        headlineLarge = t.headlineLarge.copy(fontFamily = Family, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
        headlineMedium = t.headlineMedium.copy(fontFamily = Family, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
        headlineSmall = t.headlineSmall.copy(fontFamily = Family, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
        titleLarge = t.titleLarge.copy(fontFamily = Family, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.01).em),
        titleMedium = t.titleMedium.copy(fontFamily = Family, fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontFamily = Family, fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.copy(fontFamily = Family, fontWeight = FontWeight.Medium),
        bodyMedium = t.bodyMedium.copy(fontFamily = Family),
        bodySmall = t.bodySmall.copy(fontFamily = Family),
        labelLarge = t.labelLarge.copy(fontFamily = Family, fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.copy(fontFamily = Family, fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontFamily = Family, fontWeight = FontWeight.SemiBold),
    )
}

/** Big numeric style for the ring (e.g. "1:27"). */
val HeroNumber = TextStyle(fontFamily = Family, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, letterSpacing = (-0.04).em)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
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
    CompositionLocalProvider(LocalGlass provides if (darkTheme) DarkGlass else LightGlass) {
        MaterialTheme(
            colorScheme = if (darkTheme) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
