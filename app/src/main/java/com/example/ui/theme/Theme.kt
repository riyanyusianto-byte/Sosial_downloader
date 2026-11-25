package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = VividCoral,
    onSecondary = OnVividCoral,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = OnCoralContainer,
    tertiary = AmberAccent,
    onTertiary = OnElectricCyan,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = StudioMidnight,
    onBackground = StudioTextPrimary,
    surface = StudioSlate,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioCard,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLow = StudioSlate,
    surfaceContainer = StudioCard,
    surfaceContainerHigh = StudioCardElevated,
    outline = StudioOutline,
    outlineVariant = StudioOutline.copy(alpha = 0.6f),
    error = ErrorCrimson,
    onError = OnVividCoral
)

private val LightColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = PorcelainSurface,
    primaryContainer = LightCyanContainer,
    onPrimaryContainer = OnLightCyanContainer,
    secondary = LightCoralSecondary,
    onSecondary = PorcelainSurface,
    secondaryContainer = LightCoralContainer,
    onSecondaryContainer = OnLightCoralContainer,
    tertiary = AmberAccent,
    onTertiary = PorcelainTextPrimary,
    background = PorcelainBg,
    onBackground = PorcelainTextPrimary,
    surface = PorcelainSurface,
    onSurface = PorcelainTextPrimary,
    surfaceVariant = PorcelainCard,
    onSurfaceVariant = PorcelainTextSecondary,
    surfaceContainerLow = PorcelainSurface,
    surfaceContainer = PorcelainCard,
    surfaceContainerHigh = PorcelainCard,
    outline = PorcelainOutline,
    outlineVariant = PorcelainOutline.copy(alpha = 0.6f),
    error = ErrorCrimson,
    onError = PorcelainSurface
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LinkSaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
