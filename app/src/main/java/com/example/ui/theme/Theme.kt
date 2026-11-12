package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme =
  darkColorScheme(
    primary = ElectricCyan,
    onPrimary = StudioObsidian,
    primaryContainer = Color(0xFF003B47),
    onPrimaryContainer = Color(0xFFB5F6FF),
    secondary = AmberOrange,
    onSecondary = StudioObsidian,
    secondaryContainer = Color(0xFF472300),
    onSecondaryContainer = Color(0xFFFFD6B3),
    tertiary = BeatGreen,
    onTertiary = StudioObsidian,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioConsole,
    onSurface = TextPrimary,
    surfaceVariant = StudioCard,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    error = ClipRed,
    onError = Color.White,
  )

@Composable
fun StemDeckTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = StudioDarkColorScheme, typography = Typography, content = content)
}
