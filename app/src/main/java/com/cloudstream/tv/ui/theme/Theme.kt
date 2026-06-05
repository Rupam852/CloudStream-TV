package com.cloudstream.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

// --- PlayStation-inspired harmonious color palettes ---
val DarkBackground = Color(0xFF070710)
val DarkSurface = Color(0xFF121225)
val DarkSurfaceVariant = Color(0xFF1B1B36)
val DarkPrimary = Color(0xFF00E5FF)       // Glowing Neon Cyan
val DarkSecondary = Color(0xFF8F5CFF)     // PlayStation Purple
val DarkBorder = Color(0xFF2E2E52)
val DarkOnBackground = Color(0xFFF1F5F9)
val DarkOnSurface = Color(0xFFFFFFFF)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)

val LightBackground = Color(0xFFEFE9D9)    // Warm parchment paper background (eye-comfort)
val LightSurface = Color(0xFFE4DEC9)       // Soft warm parchment surface
val LightSurfaceVariant = Color(0xFFD9D3BE) // Muted warm sand variant
val LightPrimary = Color(0xFF0F2C59)       // Deep navy blue (highly legible without blue-light glare)
val LightSecondary = Color(0xFF5C3D2E)     // Muted terracotta/coffee brown
val LightBorder = Color(0xFFC7C1AC)        // Soft muted border
val LightOnBackground = Color(0xFF2C2720)  // Soft dark coffee for main text (low contrast strain)
val LightOnSurface = Color(0xFF2C2720)     // Soft dark coffee for surfaces
val LightOnSurfaceVariant = Color(0xFF5A5448) // Muted warm subtext
val LightError = Color(0xFF9E2A2B)         // Soft warm brick red
val LightErrorContainer = Color(0xFFF5E1DA) // Softer terracotta pink-beige container
val LightOnError = Color(0xFFFFFFFF)
val LightOnErrorContainer = Color(0xFF4A1516)

val NeonCyanGlow = Color(0xFF00F5FF)
val AccentPurpleGlow = Color(0xFF9F75FF)

private val DarkTvColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant
)

private val LightTvColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError,
    errorContainer = LightErrorContainer,
    onError = LightOnError,
    onErrorContainer = LightOnErrorContainer
)

// Custom attributes
data class ExtraColors(
    val focusGlow: Color = NeonCyanGlow,
    val focusBorder: Color = DarkPrimary,
    val backgroundBlurTint: Color = Color(0x33000000),
    val textMuted: Color = Color(0xFF64748B)
)

val LocalExtraColors = compositionLocalOf { ExtraColors() }

object CloudStreamTheme {
    val extraColors: ExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtraColors.current
}

@Composable
fun CloudStreamTVTheme(
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkTheme) DarkTvColorScheme else LightTvColorScheme
    val extraColors = if (isDarkTheme) {
        ExtraColors(
            focusGlow = NeonCyanGlow.copy(alpha = 0.5f),
            focusBorder = DarkPrimary,
            backgroundBlurTint = Color(0x99070710),
            textMuted = DarkOnSurfaceVariant
        )
    } else {
        ExtraColors(
            focusGlow = LightPrimary.copy(alpha = 0.3f),
            focusBorder = LightPrimary,
            backgroundBlurTint = Color(0x33000000),
            textMuted = LightOnSurfaceVariant
        )
    }

    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    )

    val shapes = Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(18.dp)
    )

    CompositionLocalProvider(LocalExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
