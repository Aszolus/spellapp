package com.spellapp.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class SpellAppThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

@Composable
fun SpellAppTheme(
    themeMode: SpellAppThemeMode = SpellAppThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        SpellAppThemeMode.DARK -> true
        SpellAppThemeMode.LIGHT -> false
        SpellAppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) SpellAppDarkColorScheme else SpellAppLightColorScheme
    val traditionColors = if (darkTheme) SpellAppDarkTraditionColors else SpellAppLightTraditionColors

    CompositionLocalProvider(LocalTraditionColors provides traditionColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpellAppTypography,
            content = content,
        )
    }
}
