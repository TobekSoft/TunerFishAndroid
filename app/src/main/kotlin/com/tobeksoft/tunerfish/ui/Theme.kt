package com.tobeksoft.tunerfish.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Monochrome on purpose: black, white and greys only (covers bring the colour). Every role is set,
// so nothing falls back to Material's purple baseline. No dynamic (wallpaper) colour.

private val Light = lightColorScheme(
    primary = Color.Black, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E4), onPrimaryContainer = Color.Black,
    inversePrimary = Color(0xFFBDBDBD),
    secondary = Color(0xFF424242), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4E4), onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF424242), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E4E4), onTertiaryContainer = Color.Black,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFEEEEEE), onSurfaceVariant = Color(0xFF555555),
    surfaceTint = Color(0xFF757575),
    inverseSurface = Color(0xFF2E2E2E), inverseOnSurface = Color(0xFFF2F2F2),
    outline = Color(0xFF9E9E9E), outlineVariant = Color(0xFFDDDDDD),
    surfaceBright = Color.White, surfaceDim = Color(0xFFDADADA),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE6E6E6),
)

private val Dark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    primaryContainer = Color(0xFF333333), onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFF424242),
    secondary = Color(0xFFBDBDBD), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF333333), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFBDBDBD), onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF333333), onTertiaryContainer = Color.White,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF242424), onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceTint = Color(0xFF9E9E9E),
    inverseSurface = Color(0xFFE6E6E6), inverseOnSurface = Color(0xFF1C1C1C),
    outline = Color(0xFF757575), outlineVariant = Color(0xFF3A3A3A),
    surfaceBright = Color(0xFF2E2E2E), surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1C1C1C), surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
)

/** [dark] comes from the Theme setting (Light, Dark, or the phone's own setting). */
@Composable
fun TunerFishTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
