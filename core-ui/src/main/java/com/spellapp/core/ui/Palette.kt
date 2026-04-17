package com.spellapp.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// Palette intent: a well-worn spellbook read by candlelight at the table.
// Dark = ink-on-vellum reversed, warm near-black. Light = parchment + iron-gall ink.
// Primary = candlelight amber (the Cast CTA color). Secondary = arcane indigo.
// Tertiary = sealing-wax rust for attention that is not error. Neutrals tinted warm.

internal val SpellAppDarkColorScheme: ColorScheme = darkColorScheme(
    primary = oklch(0.76, 0.11, 78.0),
    onPrimary = oklch(0.18, 0.010, 70.0),
    primaryContainer = oklch(0.35, 0.07, 78.0),
    onPrimaryContainer = oklch(0.92, 0.04, 78.0),

    secondary = oklch(0.68, 0.08, 285.0),
    onSecondary = oklch(0.16, 0.008, 70.0),
    secondaryContainer = oklch(0.30, 0.05, 285.0),
    onSecondaryContainer = oklch(0.88, 0.03, 285.0),

    tertiary = oklch(0.65, 0.09, 35.0),
    onTertiary = oklch(0.14, 0.008, 70.0),
    tertiaryContainer = oklch(0.30, 0.06, 35.0),
    onTertiaryContainer = oklch(0.90, 0.03, 35.0),

    background = oklch(0.17, 0.010, 70.0),
    onBackground = oklch(0.92, 0.008, 75.0),
    surface = oklch(0.20, 0.010, 70.0),
    onSurface = oklch(0.92, 0.008, 75.0),
    surfaceVariant = oklch(0.26, 0.012, 72.0),
    onSurfaceVariant = oklch(0.74, 0.010, 75.0),
    surfaceTint = oklch(0.76, 0.11, 78.0),

    outline = oklch(0.48, 0.010, 72.0),
    outlineVariant = oklch(0.32, 0.010, 72.0),

    error = oklch(0.68, 0.15, 28.0),
    onError = oklch(0.14, 0.010, 70.0),
    errorContainer = oklch(0.32, 0.09, 28.0),
    onErrorContainer = oklch(0.94, 0.04, 28.0),

    inverseSurface = oklch(0.92, 0.008, 75.0),
    inverseOnSurface = oklch(0.20, 0.010, 70.0),
    inversePrimary = oklch(0.38, 0.09, 78.0),

    scrim = oklch(0.04, 0.0, 0.0),
)

internal val SpellAppLightColorScheme: ColorScheme = lightColorScheme(
    primary = oklch(0.40, 0.10, 78.0),
    onPrimary = oklch(0.97, 0.014, 80.0),
    primaryContainer = oklch(0.88, 0.05, 78.0),
    onPrimaryContainer = oklch(0.20, 0.06, 78.0),

    secondary = oklch(0.42, 0.09, 285.0),
    onSecondary = oklch(0.97, 0.014, 80.0),
    secondaryContainer = oklch(0.86, 0.04, 285.0),
    onSecondaryContainer = oklch(0.22, 0.06, 285.0),

    tertiary = oklch(0.48, 0.14, 28.0),
    onTertiary = oklch(0.97, 0.014, 80.0),
    tertiaryContainer = oklch(0.89, 0.05, 28.0),
    onTertiaryContainer = oklch(0.24, 0.08, 28.0),

    background = oklch(0.96, 0.015, 82.0),
    onBackground = oklch(0.22, 0.012, 70.0),
    surface = oklch(0.97, 0.013, 82.0),
    onSurface = oklch(0.22, 0.012, 70.0),
    surfaceVariant = oklch(0.93, 0.018, 82.0),
    onSurfaceVariant = oklch(0.42, 0.010, 72.0),
    surfaceTint = oklch(0.40, 0.10, 78.0),

    outline = oklch(0.64, 0.012, 72.0),
    outlineVariant = oklch(0.82, 0.012, 72.0),

    error = oklch(0.48, 0.18, 28.0),
    onError = oklch(0.98, 0.014, 80.0),
    errorContainer = oklch(0.90, 0.06, 28.0),
    onErrorContainer = oklch(0.25, 0.10, 28.0),

    inverseSurface = oklch(0.24, 0.010, 70.0),
    inverseOnSurface = oklch(0.95, 0.013, 82.0),
    inversePrimary = oklch(0.76, 0.11, 78.0),

    scrim = oklch(0.04, 0.0, 0.0),
)
