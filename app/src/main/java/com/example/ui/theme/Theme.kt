package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = CobaltOnPrimaryDark,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = CobaltOnContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = SlateOnContainerDark,
    tertiary = IndigoTertiaryDark,
    onTertiary = IndigoOnTertiaryDark,
    tertiaryContainer = IndigoContainerDark,
    onTertiaryContainer = IndigoOnContainerDark,
    background = ObsidianBackground,
    onBackground = MistOnSurface,
    surface = ObsidianSurface,
    onSurface = MistOnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = MistOnSurfaceVariant,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceVariant,
    outline = SubtleOutlineDark,
    outlineVariant = SubtleOutlineVariantDark,
    error = DangerRedDark
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = CobaltOnPrimaryLight,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = CobaltOnContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = SlateOnSecondaryLight,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = SlateOnContainerLight,
    tertiary = IndigoTertiaryLight,
    onTertiary = IndigoOnTertiaryLight,
    tertiaryContainer = IndigoContainerLight,
    onTertiaryContainer = IndigoOnContainerLight,
    background = PorcelainBackground,
    onBackground = InkOnSurface,
    surface = PorcelainSurface,
    onSurface = InkOnSurface,
    surfaceVariant = PorcelainSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    surfaceContainerLowest = PorcelainSurface,
    surfaceContainerLow = PorcelainBackground,
    surfaceContainer = PorcelainSurfaceContainer,
    surfaceContainerHigh = PorcelainSurfaceVariant,
    outline = SubtleOutlineLight,
    outlineVariant = SubtleOutlineVariantLight,
    error = DangerRed
)

@Composable
fun UnifiedMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
